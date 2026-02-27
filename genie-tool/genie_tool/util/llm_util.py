# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/8
# =====================
import json
import os
import asyncio
from typing import List, Any, Optional

from litellm import acompletion
from loguru import logger
from genie_tool.util.log_util import timer, AsyncTimer
from genie_tool.util.sensitive_detection import SensitiveWordsReplace

# 裸模型名 -> 用于显示的 provider/model 标识（仅用于判断是否为 dashscope）
_LITELLM_DASHSCOPE_MODELS = {
    "qwen-flash", "qwen-plus", "qwen-turbo",
    "qwen-max-latest", "qwen-plus-latest", "qwen-turbo-latest",
    "qwen-vl-plus", "qwen-vl-max",
    "qwen3.5-plus", "qwen3.5-turbo", "qwen3.5-flash",  # Qwen3.5 系列
}

# 部分 litellm 版本的 LlmProviders 枚举中不包含 dashscope，传 model=dashscope/qwen-max 仍会报
# LLM Provider NOT provided。此时改用 OpenAI 兼容路径：api_base + api_key + custom_llm_provider="openai"。
DASHSCOPE_API_BASE_DEFAULT = "https://dashscope.aliyuncs.com/compatible-mode/v1"


def _normalize_api_base(api_base: str) -> str:
    """确保 api_base 以 /v1 结尾（litellm 会在其后追加 /chat/completions）。"""
    if not api_base:
        return api_base
    api_base = api_base.rstrip("/")
    if not api_base.endswith("/v1"):
        api_base = f"{api_base}/v1"
    return api_base


def _is_dashscope_api_base(api_base: str) -> bool:
    """判断 api_base 是否指向 DashScope（兼容接口）。"""
    return api_base and "dashscope" in api_base.lower()


def _prepare_litellm_params(model: str, **kwargs: Any) -> dict:
    """
    统一处理 model / api_base / api_key / custom_llm_provider，兼容 dashscope 等
    在旧版 litellm 中不在 provider_list 的 provider。
    
    处理逻辑：
    1. model=dashscope/qwen-max → 使用 DashScope
    2. model=qwen-max（qwen 系列） → 使用 DashScope
    3. model=qwen-max（或其他）+ OPENAI_BASE_URL 指向 dashscope → 使用 DashScope
    """
    model = (model or "").strip()
    if not model:
        return {"model": model, **kwargs}

    # 检查环境变量和显式传入的 api_base
    explicit_api_base = kwargs.get("api_base")
    env_api_base = os.getenv("DASHSCOPE_API_BASE") or os.getenv("OPENAI_BASE_URL")
    use_dashscope = False
    api_base_raw = None

    # 情况1: model 是 dashscope/qwen-max 格式
    if "/" in model:
        prefix, rest = model.split("/", 1)
        if prefix == "dashscope" and rest:
            use_dashscope = True
            api_base_raw = explicit_api_base or env_api_base or DASHSCOPE_API_BASE_DEFAULT
        elif explicit_api_base and _is_dashscope_api_base(explicit_api_base):
            use_dashscope = True
            api_base_raw = explicit_api_base
        elif env_api_base and _is_dashscope_api_base(env_api_base):
            use_dashscope = True
            api_base_raw = env_api_base
        else:
            return {"model": model, **kwargs}
    # 情况2: model 是 qwen 系列裸模型名（含 qwen3.5-plus 等）
    elif model in _LITELLM_DASHSCOPE_MODELS or model.startswith("qwen"):
        use_dashscope = True
        api_base_raw = explicit_api_base or env_api_base or DASHSCOPE_API_BASE_DEFAULT
    # 情况3: 其他模型名，但 OPENAI_BASE_URL 指向 dashscope（用户想用 DashScope 兼容接口调用任意模型）
    elif env_api_base and _is_dashscope_api_base(env_api_base) and not explicit_api_base:
        use_dashscope = True
        api_base_raw = env_api_base

    if use_dashscope and api_base_raw:
        # 移除 kwargs 中可能存在的 api_base 和 api_key（我们将使用处理后的值）
        kwargs.pop("api_base", None)
        api_base = _normalize_api_base(api_base_raw)
        api_key = kwargs.pop("api_key", None) or os.getenv("DASHSCOPE_API_KEY") or os.getenv("OPENAI_API_KEY")
        # 如果是 dashscope/qwen-max 格式，model 使用 rest（qwen-max）
        final_model = rest if "/" in model and model.split("/", 1)[0] == "dashscope" else model
        
        # DashScope 只支持 Qwen 系列模型，非 Qwen 模型（如 gpt-4o-mini）自动映射到 qwen-turbo
        if final_model not in _LITELLM_DASHSCOPE_MODELS and not final_model.startswith("qwen"):
            from genie_tool.util.log_util import logger
            mapped_model = os.getenv("DASHSCOPE_FALLBACK_MODEL", "qwen3.5-plus")
            logger.warning(
                f"[ask_llm] DashScope 不支持模型 '{final_model}'（仅支持 Qwen 系列），"
                f"自动映射为 {mapped_model}。可通过 DASHSCOPE_FALLBACK_MODEL 环境变量自定义。"
            )
            final_model = mapped_model
        
        return {
            "model": final_model,
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": "openai",
            **kwargs,
        }

    return {"model": model, **kwargs}


@timer(key="enter")
async def ask_llm(
        messages: str | List[Any],
        model: str,
        temperature: float = None,
        top_p: float = None,
        stream: bool = False,

        # 自定义字段
        only_content: bool = False,     # 只返回内容

        extra_headers: Optional[dict] = None,
        timeout: Optional[int] = None,  # 添加 timeout 参数
        **kwargs,
):
    if isinstance(messages, str):
        messages = [{"role": "user", "content": messages}]
    if os.getenv("SENSITIVE_WORD_REPLACE", "false") == "true":
        for message in messages:
            if isinstance(message.get("content"), str):
                message["content"] = SensitiveWordsReplace.replace(message["content"])
            else:
                message["content"] = json.loads(
                    SensitiveWordsReplace.replace(json.dumps(message["content"], ensure_ascii=False)))
    params = _prepare_litellm_params(model, extra_headers=extra_headers, **kwargs)
    
    # 优先使用传入的 timeout，其次使用环境变量，默认 600s
    if timeout is None:
        timeout = int(os.getenv("LLM_TIMEOUT", 600000))
    params["timeout"] = timeout

    # 调试日志：确认实际使用的参数（特别是 dashscope 的 api_base）
    if params.get("custom_llm_provider") == "openai" and params.get("api_base"):
        logger.info(f"[ask_llm] DashScope 兼容模式: model={params.get('model')}, api_base={params.get('api_base')}, has_api_key={bool(params.get('api_key'))}, timeout={timeout}")
    
    # 重试逻辑
    max_retries = 1
    response = None
    buffered_chunks: list[str] = []
    for attempt in range(max_retries + 1):
        try:
            response = await acompletion(
                messages=messages,
                temperature=temperature,
                top_p=top_p,
                stream=stream,
                **params
            )
            
            async with AsyncTimer(key=f"exec ask_llm"):
                if stream:
                    async for chunk in response:
                        if only_content:
                            if chunk.choices and chunk.choices[0] and chunk.choices[0].delta and chunk.choices[0].delta.content:
                                text = chunk.choices[0].delta.content
                                buffered_chunks.append(text)
                                yield text
                        else:
                            yield chunk
                else:
                    yield response.choices[0].message.content if only_content else response
            return # 成功执行后返回

        except asyncio.CancelledError as e:
            logger.warning(f"[ask_llm] Request cancelled (attempt {attempt + 1}/{max_retries + 1}): {e}")
            # 若任务被上层取消，继续任何 await 都可能再次抛 CancelledError。
            # 优先返回已缓冲的内容，避免业务完全中断。
            if stream and only_content and buffered_chunks:
                try:
                    yield "".join(buffered_chunks)
                    return
                except Exception:
                    pass
            if attempt < max_retries:
                # 回退方案：切换为非流式一次性获取，避免中途被 cancel scope 终止
                try:
                    fallback = await asyncio.shield(
                        acompletion(
                            messages=messages,
                            temperature=temperature,
                            top_p=top_p,
                            stream=False,
                            **params
                        )
                    )
                    yield fallback.choices[0].message.content if only_content else fallback
                    return
                except Exception as ex:
                    logger.warning(f"[ask_llm] Fallback non-stream failed: {ex}")
                    continue
            # 达到最大重试仍被取消，则向上抛出
            raise e
        except Exception as e:
            if attempt == max_retries:
                logger.error(f"[ask_llm] Request failed after {max_retries + 1} attempts: {e}")
                raise e
            logger.warning(f"[ask_llm] Request failed (attempt {attempt + 1}/{max_retries + 1}): {e}")
            # 轻量退避，避免瞬时重试打满
            try:
                await asyncio.sleep(0.5)
            except asyncio.CancelledError:
                # 若上层取消，直接结束
                raise


if __name__ == "__main__":
    pass
