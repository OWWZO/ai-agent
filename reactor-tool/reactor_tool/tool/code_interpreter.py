# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import asyncio
import importlib
import json
import os
import shutil
import tempfile
from pathlib import Path
from typing import Any, List, Optional

import pandas as pd
import yaml
from jinja2 import Template
from loguru import logger
from smolagents import (
    ChatMessage,
    LiteLLMModel,
    OpenAIServerModel,
    FinalAnswerStep,
    PythonInterpreterTool,
    ChatMessageStreamDelta,
)

from reactor_tool.tool.ci_agent import CIAgent
from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    CodeInterpreterPermissionPolicy,
    build_permission_policy,
    build_runtime_helpers,
    validate_code_against_policy,
)
from reactor_tool.util.file_util import download_all_files_in_path, upload_file, upload_file_by_path
from reactor_tool.util.log_util import timer
from reactor_tool.util.llm_util import ask_llm_sync_iter
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.model.code import ActionOutput, CodeOuput


def _normalize_openai_compat_api_base(api_base: str) -> str:
    if not api_base:
        return api_base
    normalized = api_base.strip().rstrip("/")
    suffixes = (
        "/v1/chat/completions",
        "/chat/completions",
        "/v1/completions",
        "/completions",
        "/v1/responses",
        "/responses",
    )
    lower = normalized.lower()
    changed = True
    while changed:
        changed = False
        for suffix in suffixes:
            if lower.endswith(suffix):
                normalized = normalized[: -len(suffix)].rstrip("/")
                lower = normalized.lower()
                changed = True
                break
    if "bigmodel.cn" not in lower and not lower.endswith("/v1"):
        normalized = normalized + "/v1"
    return normalized


def _build_chat_completions_url(api_base: str) -> str:
    base = (api_base or "").rstrip("/")
    if base.endswith("/chat/completions"):
        return base
    return f"{base}/chat/completions"


def _safe_int(value: Any) -> Optional[int]:
    try:
        return int(value)
    except Exception:
        return None


def _timeout_to_seconds(value: Any) -> float:
    try:
        timeout_value = float(value)
    except Exception:
        timeout_value = 600000.0
    if timeout_value > 10000:
        return timeout_value / 1000.0
    return timeout_value


def _decode_utf8(content: bytes) -> str:
    try:
        return content.decode("utf-8")
    except Exception:
        return content.decode("utf-8", errors="replace")


def _normalize_message_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: _normalize_message_value(val) for key, val in value.items() if val is not None}
    if isinstance(value, list):
        return [_normalize_message_value(item) for item in value]
    if hasattr(value, "value") and not isinstance(value, (str, bytes, int, float, bool)):
        return _normalize_message_value(value.value)
    return value


def _chat_message_to_dict(message: Any) -> dict:
    if isinstance(message, dict):
        data = dict(message)
    elif hasattr(message, "model_dump"):
        data = message.model_dump(exclude_none=True)
    elif hasattr(message, "dict"):
        data = message.dict(exclude_none=True)
    else:
        data = {}
        for key in ("role", "content", "tool_calls", "name", "tool_call_id"):
            if hasattr(message, key):
                data[key] = getattr(message, key)
    return _normalize_message_value(data)


class RawOpenAICompatHTTPModel(OpenAIServerModel):
    """
    复用 llm_util 中统一的大模型请求逻辑，保持和其他工具一致。
    """

    def _prepare_llm_kwargs(
        self,
        messages: list[ChatMessage],
        stop_sequences: list[str] | None = None,
        response_format: dict[str, str] | None = None,
        tools_to_call_from=None,
        **kwargs,
    ) -> tuple[list[dict], str, Optional[dict], dict]:
        completion_kwargs = self._prepare_completion_kwargs(
            messages=messages,
            stop_sequences=stop_sequences,
            response_format=response_format,
            tools_to_call_from=tools_to_call_from,
            model=self.model_id,
            custom_role_conversions=self.custom_role_conversions,
            convert_images_to_image_urls=True,
            **kwargs,
        )

        prepared_messages = completion_kwargs.pop("messages", None) or messages
        extra_headers = completion_kwargs.pop("extra_headers", None)
        model_id = completion_kwargs.pop("model", self.model_id)
        completion_kwargs.pop("stream", None)

        if self.client_kwargs.get("base_url"):
            completion_kwargs["api_base"] = self.client_kwargs.get("base_url")
        if self.client_kwargs.get("api_key"):
            completion_kwargs["api_key"] = self.client_kwargs.get("api_key")
        if self.kwargs.get("timeout") is not None:
            completion_kwargs["timeout"] = self.kwargs.get("timeout")

        message_payload = [_chat_message_to_dict(message) for message in prepared_messages]
        return message_payload, model_id, extra_headers, completion_kwargs

    def generate_stream(
        self,
        messages: list[ChatMessage],
        stop_sequences: list[str] | None = None,
        response_format: dict[str, str] | None = None,
        tools_to_call_from=None,
        **kwargs,
    ):
        message_payload, model_id, extra_headers, completion_kwargs = self._prepare_llm_kwargs(
            messages=messages,
            stop_sequences=stop_sequences,
            response_format=response_format,
            tools_to_call_from=tools_to_call_from,
            **kwargs,
        )
        logger.info(f"[code_interpreter] llm_util stream request: model={model_id}")

        for chunk in ask_llm_sync_iter(
            messages=message_payload,
            model=model_id,
            stream=True,
            only_content=True,
            extra_headers=extra_headers,
            **completion_kwargs,
        ):
            if isinstance(chunk, str) and chunk:
                yield ChatMessageStreamDelta(content=chunk)

    def generate(
        self,
        messages: list[ChatMessage],
        stop_sequences: list[str] | None = None,
        response_format: dict[str, str] | None = None,
        tools_to_call_from=None,
        **kwargs,
    ) -> ChatMessage:
        message_payload, model_id, extra_headers, completion_kwargs = self._prepare_llm_kwargs(
            messages=messages,
            stop_sequences=stop_sequences,
            response_format=response_format,
            tools_to_call_from=tools_to_call_from,
            **kwargs,
        )
        content_parts: list[str] = []
        logger.info(f"[code_interpreter] llm_util aggregate request: model={model_id}")

        for chunk in ask_llm_sync_iter(
            messages=message_payload,
            model=model_id,
            stream=False,
            only_content=True,
            extra_headers=extra_headers,
            **completion_kwargs,
        ):
            if isinstance(chunk, str) and chunk:
                content_parts.append(chunk)

        return ChatMessage.from_dict(
            {"role": "assistant", "content": "".join(content_parts)},
            raw={},
        )

@timer()
async def code_interpreter_agent(
    task: str,
    file_names: Optional[List[str]] = None,
    max_file_abstract_size: int = 2000,
    max_tokens: int = 32000,
    request_id: str = "",
    stream: bool = True,
    permission_profile: str = "analysis",
):
    work_dir = ""
    try:
        work_dir = tempfile.mkdtemp()
        workspace_root = str(Path(work_dir).resolve())
        output_dir = str(Path(workspace_root).joinpath("output").resolve())
        os.makedirs(output_dir, exist_ok=True)
        import_files = await download_all_files_in_path(file_names=file_names, work_dir=work_dir)
        permission_policy = build_permission_policy(
            profile=permission_profile,
            workspace_root=workspace_root,
            output_dir=output_dir,
            input_files=[
                {
                    "name": item.get("file_name"),
                    "path": item.get("file_path"),
                }
                for item in (import_files or [])
            ],
        )

        # 1. 文件处理
        files = []
        if import_files:
            for import_file in import_files:

                file_name = import_file["file_name"]

                file_path = import_file["file_path"]
                if not file_name or not file_path:
                    continue

                # 表格文件
                if file_name.split(".")[-1] in ["xlsx", "xls", "csv"]:
                    pd.set_option("display.max_columns", None)
                    df = (
                        pd.read_csv(file_path)
                        if file_name.endswith(".csv")
                        else pd.read_excel(file_path)
                    )
                    files.append({"path": file_path, "abstract": f"{df.head(10)}"})
                # 文本文件
                elif file_name.split(".")[-1] in ["txt", "md", "html"]:
                    try:
                        with open(file_path, "r", encoding='utf-8') as rf:
                            files.append(
                                {
                                    "path": file_path,
                                    "abstract": "".join(rf.readlines())[:max_file_abstract_size],
                                }
                            )
                    except UnicodeDecodeError:
                        # UTF-8失败时尝试GBK
                        with open(file_path, "r", encoding='gbk') as rf:
                            files.append(
                                {
                                    "path": file_path,
                                    "abstract": "".join(rf.readlines())[:max_file_abstract_size],
                                }
                            )

        # 2. 构建 Prompt
        ci_prompt_template = get_prompt("code_interpreter")

        # 3. CodeAgent
        agent = create_ci_agent(
            prompt_templates=ci_prompt_template,
            max_tokens=max_tokens,
            return_full_result=True,
            output_dir=output_dir,
            permission_policy=permission_policy,
        )

        template_task = Template(ci_prompt_template["task_template"]).render(
            files=files,
            task=task,
            output_dir=output_dir,
            permission_profile=permission_policy.profile,
            available_helpers=permission_policy.to_prompt_context()["available_helpers"],
            input_file_names=permission_policy.to_prompt_context()["input_file_names"],
        )

        if stream:
            for step in agent.run(task=str(template_task), stream=True, max_steps=10):
                if isinstance(step, CodeOuput):
                    file_info = await upload_file(
                        content=step.code,
                        file_name=step.file_name,
                        file_type="py",
                        request_id=request_id,
                    )
                    step.file_list = [file_info]
                    yield step
                
                elif isinstance(step, FinalAnswerStep):
                    file_list = []
                    file_path = get_new_file_by_path(output_dir=output_dir)
                    if file_path:
                        file_info = await upload_file_by_path(
                            file_path=file_path, request_id=request_id
                        )
                        if file_info:
                            file_list.append(file_info)
                    code_name = f"{task[:20]}_代码输出.md"
                    file_list.append(
                        await upload_file(
                            content=step.output,
                            file_name=code_name,
                            file_type="md",
                            request_id=request_id,
                        )
                    )

                    output = ActionOutput(content=step.output, file_list=file_list)
                    yield output
                elif isinstance(step, ChatMessageStreamDelta):
                    #yield step.content
                    pass
                await asyncio.sleep(0)
                
        else:
            output = agent.run(task=str(template_task))
            yield output
    except CodeExecutionPermissionError as e:
        raise e
    except Exception as e:
        raise e

    finally:
        if work_dir:
            shutil.rmtree(work_dir, ignore_errors=True)


def get_new_file_by_path(output_dir):
    temp_file = ""
    latest_time = 0
    for item in os.listdir(output_dir):
        if item.endswith(".xlsx") or item.endswith(".csv") or item.endswith(".xls"):
            item_path = os.path.join(output_dir, item)
            if os.path.isfile(item_path):
                # 获取文件的最后修改时间
                mod_time = os.path.getmtime(item_path)
                # 如果当前文件比之前记录的更新，则更新最新文件和时间为当前文件
                if mod_time > latest_time:
                    latest_time = mod_time
                    temp_file = item_path
    return temp_file


def _ci_model_id_and_litellm_kwargs():
    """
    获取 code_interpreter 使用的 model_id，并根据模型前缀补全对应的
    api_base / api_key / custom_llm_provider，避免 litellm 报错
    `LLM Provider NOT provided`。

    支持：
    - qwen* / dashscope/*  → 走阿里 DashScope 兼容接口
    - glm-* / zhipuai/glm-* → 走智谱 OpenAI 兼容接口
    """
    model_id = (os.getenv("CODE_INTEPRETER_MODEL") or os.getenv("DEFAULT_MODEL") or "gpt-4.1").strip()

    # 1) Qwen / DashScope
    if model_id.startswith("qwen") or model_id.startswith("dashscope/"):
        api_base = (
            os.getenv("DASHSCOPE_API_BASE")
            or os.getenv("OPENAI_BASE_URL")
            or "https://dashscope.aliyuncs.com/compatible-mode/v1"
        )
        api_base = api_base.rstrip("/")
        if not api_base.endswith("/v1"):
            api_base = api_base + "/v1"
        api_key = os.getenv("DASHSCOPE_API_KEY") or os.getenv("OPENAI_API_KEY")
        final_model = model_id.split("/", 1)[1] if model_id.startswith("dashscope/") else model_id
        return final_model, {
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": "openai",
        }

    # 2) Zhipu GLM（与 deepsearch 保持一致）
    if model_id.startswith("zhipuai/") or model_id.startswith("glm-"):
        # 标准化模型名：zhipuai/glm-4-flash -> glm-4-flash
        final_model = model_id.split("/", 1)[1] if "/" in model_id else model_id

        api_base_raw = os.getenv("OPENAI_BASE_URL") or os.getenv("OPENAI_API_BASE")
        if not api_base_raw:
            api_base_raw = "https://open.bigmodel.cn/api/paas/v4"
        api_base_raw = api_base_raw.rstrip("/")
        if api_base_raw.endswith("/chat/completions"):
            api_base_raw = api_base_raw[: -len("/chat/completions")]
        api_base = api_base_raw

        api_key = os.getenv("OPENAI_API_KEY")
        return final_model, {
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": "openai",
        }

    # 3) Generic OpenAI-compatible gateways: HTTP-first
    openai_base = os.getenv("OPENAI_BASE_URL") or os.getenv("OPENAI_API_BASE")
    if openai_base:
        openai_base = _normalize_openai_compat_api_base(openai_base)
        return model_id, {
            "api_base": openai_base,
            "api_key": os.getenv("OPENAI_API_KEY"),
            "custom_llm_provider": "openai",
        }

    # 4) Others: fallback to LiteLLM
    return model_id, {}


def create_ci_agent(
    prompt_templates=None,
    max_tokens: int = 16000,
    return_full_result: bool = True,
    output_dir: str = "",
    permission_policy: CodeInterpreterPermissionPolicy | None = None,
) -> CIAgent:
    model_id, litellm_extra = _ci_model_id_and_litellm_kwargs()
    api_base = litellm_extra.get("api_base")
    api_key = litellm_extra.get("api_key")
    if api_base and api_key:
        # HTTP-first for OpenAI-compatible providers.
        model = RawOpenAICompatHTTPModel(
            max_tokens=max_tokens,
            model_id=model_id,
            api_base=api_base,
            api_key=api_key,
        )
    else:
        model = LiteLLMModel(
            max_tokens=max_tokens,
            model_id=model_id,
            api_base=api_base,
            api_key=api_key,
            **{k: v for k, v in litellm_extra.items() if k not in ("api_base", "api_key")},
        )

    if permission_policy is None:
        workspace_root = str(Path(output_dir or tempfile.mkdtemp()).resolve())
        final_output_dir = str(Path(output_dir or Path(workspace_root).joinpath("output")).resolve())
        permission_policy = build_permission_policy(
            profile="analysis",
            workspace_root=workspace_root,
            output_dir=final_output_dir,
            input_files=[],
        )

    runtime_helpers = build_runtime_helpers(permission_policy)
    runtime_variables = permission_policy.to_runtime_variables()

    return CIAgent(
        model=model,
        prompt_templates=prompt_templates,
        tools=[PythonInterpreterTool()],
        return_full_result=return_full_result,
        additional_authorized_imports=list(permission_policy.authorized_imports),
        executor_kwargs={"additional_functions": runtime_helpers},
        output_dir=output_dir,
        before_execute=lambda code_action: validate_code_against_policy(code_action, permission_policy),
        runtime_variables=runtime_variables,
    )


if __name__ == "__main__":
    pass
