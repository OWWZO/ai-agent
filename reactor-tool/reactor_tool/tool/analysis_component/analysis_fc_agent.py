# -*- coding: utf-8 -*-
"""auto_analysis 混合协议 Agent：

- 外层：Function Calling 仅提交 python_interpreter(code=...)
- 内层：get_data / data_trans / insight_analysis / save_insight / final_answer 由代码编排
- 降级：tool_calls → ```python → <code> → 纯代码 AST → 结论型文本包 final_answer
"""

from __future__ import annotations

import ast
import json
import os
import re
from dataclasses import dataclass
from typing import Any, Dict, Generator, List, Optional

from openai import DefaultHttpxClient
from loguru import logger
from smolagents import Tool
from smolagents.local_python_executor import LocalPythonExecutor
from smolagents.models import get_tool_json_schema

from reactor_tool.tool.code_interpreter import (
    _build_chat_completions_url,
    _normalize_openai_compat_api_base,
)
from reactor_tool.util.llm_util import _timeout_to_seconds


AUTHORIZED_IMPORTS = [
    "pandas",
    "numpy",
    "statsmodels",
    "statsmodels.*",
    "scipy",
    "scipy.*",
    "sklearn",
    "sklearn.*",
]

_PYTHON_INTERPRETER_NAMES = {
    "python_interpreter",
    "python_interpreter_tool",
    "run_python",
    "code_execution",
}


@dataclass
class AnalysisStepEvent:
    """流式步骤事件，供 auto_analysis SSE 消费。"""

    step: int
    thought: str
    code: str
    observation: str
    is_final: bool = False
    output: Any = None


class AnalysisPythonInterpreterTool(Tool):
    """外层唯一 FC 工具：执行代码；业务工具注入 executor 内。"""

    name = "python_interpreter"
    description = (
        "在受控 Python 沙箱中执行分析代码。"
        "代码内可调用: get_data, data_trans, insight_analysis, save_insight, final_answer。"
        "内层签名为 get_data(query: str)、data_trans(df, column, measure, measure_type, trans_type)、"
        "insight_analysis(df, breakdown, measure, measure_type, analysis_method)、"
        "save_insight(df, insight, analysis_process)、final_answer(answer)。"
        "变量跨步保留。收工必须在代码中调用 final_answer(...)。"
    )
    inputs = {
        "code": {
            "type": "string",
            "description": (
                "完整 Python 代码。业务工具只能在此代码内调用，"
                "禁止在工具参数外直接写 Markdown 结论。"
            ),
        }
    }
    output_type = "string"

    def __init__(
        self, inner_tools: Dict[str, Tool], *args, context: Any = None, **kwargs
    ):
        self._context = context
        self._executor = LocalPythonExecutor(
            AUTHORIZED_IMPORTS,
            additional_functions={"dir": dir},
        )
        self._executor.send_tools(inner_tools)
        self.last_is_final = False
        self.last_output: Any = None
        self.last_logs = ""
        super().__init__(*args, **kwargs)

    def _data_fetch_failure_result(self) -> str | None:
        reason = getattr(self._context, "data_fetch_error", None)
        if not reason:
            return None
        self.last_is_final = True
        self.last_output = {
            "insights": [],
            "summary": reason,
        }
        return f"Code execution stopped: {reason}"

    def forward(self, code: str) -> str:
        code = (code or "").strip()
        if not code:
            # 每次执行前都要清空上一步的状态，避免空代码被误判为上一步已经收工。
            self.last_is_final = False
            self.last_output = None
            self.last_logs = ""
            return "Error: empty code"
        try:
            output, logs, is_final = self._executor(code)
        except Exception as exc:
            # 普通代码异常作为 observation 返回；取数失败由上下文状态转为终止事件。
            self.last_is_final = False
            self.last_output = None
            self.last_logs = str(exc)
            if failure := self._data_fetch_failure_result():
                return failure
            return f"Code execution error: {exc}"
        self.last_is_final = bool(is_final)
        self.last_output = output
        self.last_logs = logs or ""
        if failure := self._data_fetch_failure_result():
            return failure
        return f"Stdout:\n{self.last_logs}\nOutput: {output}"


def extract_code_from_message(
    content: str | None,
    tool_calls: list | None = None,
) -> tuple[Optional[str], str]:
    """按协议兼容优先级提取代码，返回 ``(code, source_tag)``。"""
    # 优先读取 Function Calling；兼容层只负责把不同供应商的参数形态统一成代码字符串。
    for call in tool_calls or []:
        fn = call.get("function") if isinstance(call, dict) else None
        if not isinstance(fn, dict):
            continue
        name = str(fn.get("name") or "").strip()
        if name not in _PYTHON_INTERPRETER_NAMES and name != "python_interpreter":
            # 允许任意名字但 arguments 含 code
            args_raw = fn.get("arguments")
            args = _parse_args(args_raw)
            if isinstance(args, dict) and args.get("code"):
                return str(args["code"]).strip(), f"tool_call:{name}"
            continue
        args = _parse_args(fn.get("arguments"))
        if isinstance(args, dict) and args.get("code"):
            return str(args["code"]).strip(), "tool_call:python_interpreter"
        if isinstance(args, str) and args.strip():
            return args.strip(), "tool_call:python_interpreter"

    text = content or ""
    if not text.strip():
        return None, "empty"

    # 模型没有使用 FC 时，依次兼容 Markdown 代码块和自定义 code 标签。
    fence = re.search(r"```(?:python)?\s*\n(.*?)```", text, re.S | re.I)
    if fence:
        return fence.group(1).strip(), "markdown_fence"

    # 3) <code>...</code>
    tag = re.search(r"<code>(.*?)</code>", text, re.S | re.I)
    if tag:
        return tag.group(1).strip(), "code_tag"

    # 纯文本只有在 AST 可解析且包含分析相关 token 时才执行，避免把普通说明误送进沙箱。
    stripped = text.strip()
    try:
        ast.parse(stripped)
        if any(
            tok in stripped
            for tok in ("get_data", "save_insight", "final_answer", "import ", "print(")
        ):
            return stripped, "raw_ast"
    except SyntaxError:
        pass

    # 已经是结论的文本直接包装为 final_answer，避免在收尾阶段继续要求模型重写代码。
    if _looks_like_conclusion(stripped):
        safe = stripped.replace("\\", "\\\\").replace('"""', '\\"""')
        return f'final_answer("""{safe}""")', "wrapped_final_answer"

    return None, "unparsed"


def _parse_args(args_raw: Any) -> Any:
    if args_raw is None:
        return {}
    if isinstance(args_raw, dict):
        return args_raw
    if isinstance(args_raw, str):
        try:
            return json.loads(args_raw)
        except Exception:
            return args_raw
    return {}


def _looks_like_conclusion(text: str) -> bool:
    if not text or len(text) < 8:
        return False
    markers = ("结论", "总结", "分析结论", "无法分析", "无数据", "###", "**")
    if any(m in text for m in markers):
        return True
    if text.count("\n") >= 2 and "def " not in text and "get_data" not in text:
        return True
    return False


def chat_completion_with_tools(
    messages: List[Dict[str, Any]],
    tools: List[Dict[str, Any]],
    model: str,
    api_base: str,
    api_key: str,
    timeout: float = 600.0,
) -> Dict[str, Any]:
    """调用 OpenAI 兼容接口，并保留外层 FC 的 ``tool_calls`` 结构。"""
    url = _build_chat_completions_url(_normalize_openai_compat_api_base(api_base))
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    payload = {
        "model": model,
        "messages": messages,
        "tools": tools,
        "tool_choice": "auto",
        "temperature": 0,
        "stream": False,
    }
    timeout_s = _timeout_to_seconds(timeout)
    with DefaultHttpxClient(timeout=timeout_s, trust_env=False) as client:
        resp = client.post(url, headers=headers, json=payload)
        if resp.status_code >= 400:
            raise RuntimeError(
                f"analysis FC LLM error status={resp.status_code}, body={resp.text[:500]}"
            )
        return resp.json()


class AnalysisFCCodeAgent:
    """外层 FC 交代码 + 内层代码编排业务工具的分析 Agent。"""

    def __init__(
        self,
        instructions: str,
        inner_tools: Dict[str, Tool],
        max_steps: int = 10,
        model_id: str | None = None,
        api_base: str | None = None,
        api_key: str | None = None,
        context: Any = None,
    ):
        self.instructions = instructions or ""
        self.max_steps = max_steps or 10
        self.model_id = (
            model_id
            or os.getenv("ANALYSIS_MODEL")
            or os.getenv("DEFAULT_MODEL")
            or "gpt-4.1"
        )
        self.api_base = api_base or os.getenv("OPENAI_BASE_URL") or ""
        self.api_key = api_key or os.getenv("OPENAI_API_KEY") or ""
        self.runner = AnalysisPythonInterpreterTool(
            inner_tools=inner_tools, context=context
        )
        self._openai_tools = [get_tool_json_schema(self.runner)]

    def _system_prompt(self) -> str:
        return (
            "你是数据分析 Agent。\n"
            "【交代码协议 - 强制】\n"
            "1. 每一步必须调用工具 python_interpreter，参数 code 为完整 Python 代码。\n"
            "2. 禁止只输出 Markdown 结论；结论必须在代码中调用 final_answer(...)。\n"
            "3. 业务工具只能在 code 内调用：get_data, data_trans, insight_analysis, save_insight, final_answer。\n"
            "4. 禁止在外层直接调用 get_data 等业务工具（它们不是外层 FC 工具）。\n"
            "5. 变量跨步保留，直接使用上一步变量名；可用 dir() 查看，但优先用明确变量名。\n"
            "6. Thought 用中文，code 用 Python。\n"
            "【内层工具的真实 Python 签名 - 必须严格遵守】\n"
            "- get_data(query: str) -> pandas.DataFrame：只接受一个 query 字符串；示例：df = get_data(query='根据商品类别分组，统计销售数量')。\n"
            "- 禁止调用 get_data()；每次调用必须传入一个非空 query 字符串。检查数据源/表时也必须传 query，例如：source_check = get_data(query='请检查当前可访问的数据源和表，返回表名和可用字段，重点识别销量相关字段；不要返回明细数据。')。\n"
            "- data_trans(df, column: str, measure: str, measure_type: str, trans_type: str) -> pandas.DataFrame。\n"
            "- insight_analysis(df, breakdown: str, measure: str, measure_type: str, analysis_method: str) -> list。\n"
            "- save_insight(df, insight: str, analysis_process: str) -> str。\n"
            "- final_answer(answer: str) -> dict。\n"
            "不要给 get_data 传 table、columns、table_name、metrics 等参数；不要把参数字典作为 get_data 的位置参数。\n\n"
            f"{self.instructions}"
        )

    def run(self, task: str, stream: bool = False) -> Any:
        gen = self._run_stream(task)
        if stream:
            return gen
        final = None
        for event in gen:
            if event.is_final:
                final = event.output
        return final

    def _run_stream(self, task: str) -> Generator[AnalysisStepEvent, None, None]:
        messages: List[Dict[str, Any]] = [
            {"role": "system", "content": self._system_prompt()},
            {"role": "user", "content": f"分析任务：{task}"},
        ]

        for step in range(1, self.max_steps + 1):
            # 每一步都让模型根据完整历史决定下一段代码；max_steps 是防止协议失控的硬边界。
            try:
                raw = chat_completion_with_tools(
                    messages=messages,
                    tools=self._openai_tools,
                    model=self.model_id,
                    api_base=self.api_base,
                    api_key=self.api_key,
                    timeout=os.getenv("LLM_TIMEOUT", 600000),
                )
            except Exception as exc:
                logger.error(f"analysis FC LLM call failed: {exc}")
                yield AnalysisStepEvent(
                    step=step,
                    thought="",
                    code="",
                    observation=f"LLM 调用失败: {exc}",
                    is_final=True,
                    output={
                        "insights": [],
                        "summary": f"分析失败：LLM 调用异常 {exc}",
                    },
                )
                return

            choice = ((raw.get("choices") or [{}])[0]) or {}
            message = choice.get("message") or {}
            content = message.get("content") or ""
            tool_calls = message.get("tool_calls") or []

            code, source = extract_code_from_message(content, tool_calls)
            thought = _extract_thought(content)

            if not code:
                # 解析失败不直接结束任务，追加修复提示，让模型回到唯一的 python_interpreter 协议。
                repair = (
                    "上一步未能提取可执行代码。"
                    "请仅通过工具 python_interpreter 提交 code；"
                    "收工时在 code 内调用 final_answer。"
                )
                messages.append({"role": "assistant", "content": content or ""})
                messages.append({"role": "user", "content": repair})
                yield AnalysisStepEvent(
                    step=step,
                    thought=thought or content[:500],
                    code="",
                    observation=f"未解析到代码 (source={source})，已要求重试。",
                )
                continue

            observation = self.runner.forward(code)
            is_final = self.runner.last_is_final
            output = self.runner.last_output

            yield AnalysisStepEvent(
                step=step,
                thought=thought,
                code=code,
                observation=observation,
                is_final=is_final,
                output=output,
            )

            # 先把当前执行结果写回对话历史，再决定是否继续；这样下一轮能看到真实 observation。
            if tool_calls:
                messages.append(
                    {
                        "role": "assistant",
                        "content": content or None,
                        "tool_calls": tool_calls,
                    }
                )
                # OpenAI 协议要求每个 tool_call 都有对应的 tool 消息，不能只追加一条汇总结果。
                for call in tool_calls:
                    call_id = call.get("id") or f"call_{step}"
                    messages.append(
                        {
                            "role": "tool",
                            "tool_call_id": call_id,
                            "content": observation,
                        }
                    )
            else:
                messages.append(
                    {
                        "role": "assistant",
                        "content": f"Thought: {thought}\n<code>\n{code}\n</code>",
                    }
                )
                messages.append(
                    {
                        "role": "user",
                        "content": f"Observation:\n{observation}\n\n继续分析；完成时在 code 中 final_answer。",
                    }
                )

            if is_final:
                # final_answer 由沙箱内的业务工具触发，外层只负责转发最终输出并停止循环。
                return

        # 未触发 final_answer 时仍返回结构化失败事件，避免 SSE 调用方无限等待。
        yield AnalysisStepEvent(
            step=self.max_steps,
            thought="",
            code="",
            observation="达到 max_steps，分析未正常 final_answer",
            is_final=True,
            output={
                "insights": [],
                "summary": "分析未在限定步数内完成，请缩小任务范围后重试。",
            },
        )


def _extract_thought(content: str) -> str:
    if not content:
        return ""
    text = content
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    text = re.sub(r"<code>.*?</code>", "", text, flags=re.S | re.I)
    text = text.replace("Thought:", "\n").strip()
    return text[:2000]
