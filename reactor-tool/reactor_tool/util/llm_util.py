# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/8
# =====================
import asyncio
import json
import os
import queue
import threading
from types import SimpleNamespace
from typing import Any, List, Optional

import httpx
from litellm import acompletion
from loguru import logger

from reactor_tool.model.context import LLMModelInfoFactory
from reactor_tool.util.log_util import AsyncTimer, timer
from reactor_tool.util.sensitive_detection import SensitiveWordsReplace

# model aliases that should route to DashScope OpenAI-compatible endpoint
_LITELLM_DASHSCOPE_MODELS = {
    "qwen-flash",
    "qwen-plus",
    "qwen-turbo",
    "qwen-max-latest",
    "qwen-plus-latest",
    "qwen-turbo-latest",
    "qwen-vl-plus",
    "qwen-vl-max",
    "qwen3.5-plus",
    "qwen3.5-turbo",
    "qwen3.5-flash",
}

DASHSCOPE_API_BASE_DEFAULT = "https://dashscope.aliyuncs.com/compatible-mode/v1"
OPENAI_COMPAT_DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:148.0) "
    "Gecko/20100101 Firefox/148.0"
)


def _trimmed_env(*keys: str) -> str | None:
    """按顺序读取环境变量，返回首个非空白值。"""
    for key in keys:
        value = os.getenv(key)
        if value is None:
            continue
        trimmed = value.strip()
        if trimmed:
            return trimmed
    return None


def resolve_openai_compat_env(prefix: str) -> dict[str, str | None]:
    """解析指定前缀的 OpenAI 兼容配置，并兼容回退到全局 OPENAI_*。"""
    normalized_prefix = (prefix or "").strip().upper()
    if not normalized_prefix:
        return {"api_base": _trimmed_env("OPENAI_BASE_URL", "OPENAI_API_BASE"), "api_key": _trimmed_env("OPENAI_API_KEY")}

    return {
        "api_base": _trimmed_env(f"{normalized_prefix}_BASE_URL", "OPENAI_BASE_URL", "OPENAI_API_BASE"),
        "api_key": _trimmed_env(f"{normalized_prefix}_API_KEY", "OPENAI_API_KEY"),
    }


def _normalize_api_base(api_base: str) -> str:
    """Ensure api_base ends with /v1 for chat-completions style endpoints."""
    if not api_base:
        return api_base
    api_base = api_base.strip().rstrip("/")
    if not api_base.lower().endswith("/v1"):
        api_base = f"{api_base}/v1"
    return api_base


def _normalize_openai_compat_api_base(api_base: str) -> str:
    """
    Normalize OpenAI-compatible gateway base url.
    - strip endpoint tails like /chat/completions or /responses
    - normalize to /v1 (except bigmodel.cn style base path)
    """
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

    # zhipu bigmodel style base is not /v1 based
    if "bigmodel.cn" in lower:
        return normalized

    return _normalize_api_base(normalized)


def _is_dashscope_api_base(api_base: str) -> bool:
    return bool(api_base) and "dashscope" in api_base.lower()


def _is_official_openai_api_base(api_base: str) -> bool:
    return bool(api_base) and "api.openai.com" in api_base.lower()


def _build_openai_compat_headers(existing_headers: Optional[dict[str, Any]] = None) -> dict[str, Any]:
    """为 OpenAI 兼容网关补齐稳定请求头，避免第三方供应商拦截。"""
    headers = dict(existing_headers or {})
    lower_header_keys = {str(k).lower() for k in headers.keys()}
    if "user-agent" not in lower_header_keys:
        headers["User-Agent"] = os.getenv("OPENAI_COMPAT_USER_AGENT", OPENAI_COMPAT_DEFAULT_USER_AGENT)
    return headers


def _is_permission_or_policy_block_error(err: Exception) -> bool:
    err_text = str(err).lower()
    signals = (
        "your request was blocked",
        "permissiondenied",
        "permission denied",
        "forbidden",
        "access denied",
    )
    return any(signal in err_text for signal in signals)


def _safe_float(value: Any) -> Optional[float]:
    if value is None:
        return None
    try:
        text = str(value).strip()
        if not text:
            return None
        return float(text)
    except Exception:
        return None


def _safe_int(value: Any) -> Optional[int]:
    if value is None:
        return None
    try:
        text = str(value).strip()
        if not text:
            return None
        return int(text)
    except Exception:
        return None


def _build_chat_completions_url(api_base: str) -> str:
    base = (api_base or "").rstrip("/")
    if base.endswith("/chat/completions"):
        return base
    return f"{base}/chat/completions"


def _payload_from_litellm_params(messages: List[Any], stream: bool, params: dict) -> dict:
    payload = {"messages": messages, "stream": stream}
    for k, v in params.items():
        if k in {"api_base", "api_key", "custom_llm_provider", "timeout", "extra_headers"}:
            continue
        payload[k] = v
    return payload


def _timeout_to_seconds(timeout: Any) -> float:
    value = _safe_float(timeout)
    if value is None:
        return 600.0
    # project uses ms-style timeout env by default (e.g. 600000)
    if value > 10000:
        return value / 1000.0
    return value


def _to_attr_obj(value: Any) -> Any:
    if isinstance(value, dict):
        return SimpleNamespace(**{k: _to_attr_obj(v) for k, v in value.items()})
    if isinstance(value, list):
        return [_to_attr_obj(item) for item in value]
    return value


def _get_value(source: Any, key: str, default: Any = None) -> Any:
    """兼容 dict / SimpleNamespace / LiteLLM 对象的安全取值。"""
    if source is None:
        return default
    if isinstance(source, dict):
        return source.get(key, default)
    return getattr(source, key, default)


def _extract_text_payload(value: Any) -> str:
    """提取不同 content 结构中的文本内容。"""
    if value is None:
        return ""
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        text_parts: list[str] = []
        for item in value:
            if isinstance(item, str):
                text_parts.append(item)
                continue
            item_type = _get_value(item, "type", "")
            if item_type in {"text", "output_text"}:
                text = _get_value(item, "text") or _get_value(item, "content")
                if isinstance(text, str) and text:
                    text_parts.append(text)
        return "".join(text_parts)
    return ""


def extract_stream_chunk_text(chunk: Any, include_reasoning: bool = False) -> str:
    """从流式 chunk 中安全提取文本，兼容 content / reasoning_content 等结构。"""
    choices = _get_value(chunk, "choices", []) or []
    if isinstance(choices, list) and choices:
        choice = choices[0]
        delta = _get_value(choice, "delta")
        message = _get_value(choice, "message")

        content = _extract_text_payload(_get_value(delta, "content"))
        if content:
            return content

        if include_reasoning:
            reasoning_content = _extract_text_payload(
                _get_value(delta, "reasoning_content") or _get_value(delta, "reasoning")
            )
            if reasoning_content:
                return reasoning_content

        message_content = _extract_text_payload(_get_value(message, "content"))
        if message_content:
            return message_content

        if include_reasoning:
            message_reasoning = _extract_text_payload(
                _get_value(message, "reasoning_content") or _get_value(message, "reasoning")
            )
            if message_reasoning:
                return message_reasoning

        choice_text = _extract_text_payload(_get_value(choice, "text"))
        if choice_text:
            return choice_text

    output_text = _extract_text_payload(_get_value(chunk, "output_text"))
    if output_text:
        return output_text

    return ""


async def _raw_openai_like_request(
    messages: List[Any],
    params: dict,
    stream: bool,
    only_content: bool,
):
    api_base = params.get("api_base")
    api_key = params.get("api_key")
    if not api_base or not api_key:
        raise RuntimeError("raw_openai_like_request missing api_base/api_key")

    url = _build_chat_completions_url(str(api_base))
    transport_stream = True
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }
    if isinstance(params.get("extra_headers"), dict):
        # Merge caller-provided headers, then enforce SSE accept for compat gateways.
        headers.update(params.get("extra_headers"))
    headers["Accept"] = "text/event-stream"

    payload = _payload_from_litellm_params(messages=messages, stream=transport_stream, params=params)
    timeout_s = _timeout_to_seconds(params.get("timeout"))

    async with httpx.AsyncClient(timeout=timeout_s) as client:
        async with client.stream("POST", url, headers=headers, json=payload) as resp:
            if resp.status_code >= 400:
                text = (await resp.aread()).decode("utf-8", errors="ignore")
                raise RuntimeError(f"raw_openai_like status={resp.status_code}, body={text[:500]}")

            buffered_text_parts: list[str] = []
            last_obj: dict = {}
            saw_chunk = False
            async for line in resp.aiter_lines():
                if not line or not line.startswith("data:"):
                    continue
                data = line[5:].strip()
                if not data:
                    continue
                if data == "[DONE]":
                    break
                try:
                    chunk_obj = json.loads(data)
                except Exception:
                    continue

                saw_chunk = True
                last_obj = chunk_obj
                piece = extract_stream_chunk_text(chunk_obj)

                if stream:
                    if only_content:
                        if piece:
                            yield piece
                    else:
                        yield _to_attr_obj(chunk_obj)
                else:
                    if piece:
                        buffered_text_parts.append(piece)

            if not stream:
                merged_content = "".join(buffered_text_parts)
                if only_content:
                    yield merged_content
                else:
                    if not saw_chunk:
                        yield _to_attr_obj({
                            "choices": [{"message": {"role": "assistant", "content": ""}}],
                        })
                        return
                    merged_obj = dict(last_obj) if isinstance(last_obj, dict) else {}
                    choices = merged_obj.get("choices") or [{}]
                    first_choice = choices[0] if isinstance(choices, list) and choices else {}
                    first_choice = dict(first_choice) if isinstance(first_choice, dict) else {}
                    first_choice["message"] = {"role": "assistant", "content": merged_content}
                    first_choice.pop("delta", None)
                    merged_obj["choices"] = [first_choice]
                    yield _to_attr_obj(merged_obj)


def _prepare_litellm_params(model: str, **kwargs: Any) -> dict:
    """
    Normalize model/api_base/api_key/custom_llm_provider for:
    1) DashScope compatibility endpoint
    2) Zhipu OpenAI-compatible endpoint
    3) Generic OpenAI-compatible gateways
    """
    model = (model or "").strip()
    if not model:
        return {"model": model, **kwargs}

    explicit_api_base = kwargs.get("api_base")
    env_dashscope_api_base = os.getenv("DASHSCOPE_API_BASE")
    env_openai_api_base = os.getenv("OPENAI_BASE_URL") or os.getenv("OPENAI_API_BASE")
    explicit_dashscope = bool(explicit_api_base) and _is_dashscope_api_base(str(explicit_api_base))

    # ===== DashScope =====
    use_dashscope = False
    api_base_raw = None
    if "/" in model:
        prefix, rest = model.split("/", 1)
        if prefix == "dashscope" and rest:
            use_dashscope = True
            api_base_raw = explicit_api_base or env_dashscope_api_base or DASHSCOPE_API_BASE_DEFAULT
        elif explicit_dashscope:
            use_dashscope = True
            api_base_raw = explicit_api_base
        else:
            return {"model": model, **kwargs}
    elif model in _LITELLM_DASHSCOPE_MODELS or model.startswith("qwen"):
        use_dashscope = True
        # 只有 Qwen / DashScope 模型才自动复用 DashScope 环境，避免 gpt-* 被错误劫持到 DashScope。
        api_base_raw = explicit_api_base or env_dashscope_api_base or DASHSCOPE_API_BASE_DEFAULT
    elif explicit_dashscope:
        use_dashscope = True
        api_base_raw = explicit_api_base

    if use_dashscope and api_base_raw:
        kwargs.pop("api_base", None)
        api_base = _normalize_api_base(api_base_raw)
        api_key = kwargs.pop("api_key", None) or os.getenv("DASHSCOPE_API_KEY") or os.getenv("OPENAI_API_KEY")
        final_model = rest if "/" in model and model.split("/", 1)[0] == "dashscope" else model

        if final_model not in _LITELLM_DASHSCOPE_MODELS and not final_model.startswith("qwen"):
            mapped_model = os.getenv("DASHSCOPE_FALLBACK_MODEL", "qwen3.5-plus")
            logger.warning(
                f"[ask_llm] DashScope does not support model '{final_model}', fallback to {mapped_model}."
            )
            final_model = mapped_model

        return {
            "model": final_model,
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": "openai",
            **kwargs,
        }

    # ===== Zhipu (glm-*) via OpenAI-compatible endpoint =====
    if model.startswith("zhipuai/") or model.startswith("glm-"):
        final_model = model.split("/", 1)[1] if "/" in model else model

        api_base_raw = kwargs.pop("api_base", None) or os.getenv("OPENAI_BASE_URL") or os.getenv("OPENAI_API_BASE")
        if not api_base_raw:
            api_base_raw = "https://open.bigmodel.cn/api/paas/v4"
        api_base_raw = api_base_raw.rstrip("/")

        if "bigmodel.cn" in api_base_raw.lower():
            if api_base_raw.lower().endswith("/chat/completions"):
                api_base_raw = api_base_raw[: -len("/chat/completions")]
            api_base = api_base_raw
        else:
            api_base = _normalize_api_base(api_base_raw)

        api_key = kwargs.pop("api_key", None) or os.getenv("OPENAI_API_KEY")
        return {
            "model": final_model,
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": "openai",
            **kwargs,
        }

    # ===== Generic OpenAI-compatible gateways =====
    # example: OPENAI_BASE_URL=https://your-gateway/v1/chat/completions
    openai_compat_base = kwargs.pop("api_base", None) or env_openai_api_base
    if openai_compat_base and not _is_dashscope_api_base(openai_compat_base):
        api_base = _normalize_openai_compat_api_base(openai_compat_base)
        api_key = kwargs.pop("api_key", None) or os.getenv("OPENAI_API_KEY")
        provider = "openai" if _is_official_openai_api_base(api_base) else "openai_like"
        return {
            "model": model,
            "api_base": api_base,
            "api_key": api_key,
            "custom_llm_provider": provider,
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
    only_content: bool = False,
    extra_headers: Optional[dict] = None,
    timeout: Optional[int] = None,
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
                    SensitiveWordsReplace.replace(json.dumps(message["content"], ensure_ascii=False))
                )

    params = _prepare_litellm_params(model, extra_headers=extra_headers, **kwargs)

    if timeout is None:
        timeout = int(os.getenv("LLM_TIMEOUT", 600000))
    params["timeout"] = timeout

    if params.get("custom_llm_provider") in {"openai", "openai_like"} and params.get("api_base"):
        logger.info(
            f"[ask_llm] OpenAI-compatible mode: provider={params.get('custom_llm_provider')}, "
            f"model={params.get('model')}, api_base={params.get('api_base')}, "
            f"has_api_key={bool(params.get('api_key'))}, timeout={timeout}"
        )

    # Align key request fields with Java-side behavior, while honoring existing python settings.
    # temperature: function arg > existing params > env > default(0.0)
    if temperature is not None:
        params["temperature"] = temperature
    elif params.get("temperature") is None:
        params["temperature"] = _safe_float(os.getenv("LLM_TEMPERATURE"))
        if params["temperature"] is None:
            params["temperature"] = 0.0

    # top_p: only set when explicitly passed
    if top_p is not None:
        params["top_p"] = top_p

    # max_tokens: existing params > env > model registry default
    auto_max_tokens_from_model = False
    if params.get("max_tokens") is None and params.get("max_completion_tokens") is None:
        env_max_tokens = _safe_int(os.getenv("LLM_MAX_TOKENS") or os.getenv("MAX_TOKENS"))
        if env_max_tokens is not None and env_max_tokens > 0:
            params["max_tokens"] = env_max_tokens
        else:
            auto_max_tokens_from_model = True
            effective_model = str(params.get("model") or model)
            params["max_tokens"] = int(LLMModelInfoFactory.get_max_output(effective_model, default=32000))

    # Header alignment: Java stream uses Accept: text/event-stream.
    merged_headers = {}
    if isinstance(params.get("extra_headers"), dict):
        merged_headers.update(params.get("extra_headers"))
    if isinstance(extra_headers, dict):
        merged_headers.update(extra_headers)
    if params.get("custom_llm_provider") in {"openai", "openai_like"} or params.get("api_base"):
        merged_headers = _build_openai_compat_headers(merged_headers)
    lower_header_keys = {str(k).lower() for k in merged_headers.keys()}
    if "content-type" not in lower_header_keys:
        merged_headers["Content-Type"] = "application/json"
    if "accept" not in lower_header_keys:
        merged_headers["Accept"] = "text/event-stream" if stream else "application/json"
    params["extra_headers"] = merged_headers

    max_retries = 1
    fallback_model = (
        os.getenv("OPENAI_COMPAT_FALLBACK_MODEL")
        or os.getenv("OPENAI_FALLBACK_MODEL")
        or "gpt-4"
    ).strip()
    openai_compat_http_primary = (
        bool(params.get("api_base"))
        and bool(params.get("api_key"))
        and not _is_dashscope_api_base(str(params.get("api_base")))
    )
    allow_litellm_fallback_for_openai_compat = (
        os.getenv("OPENAI_COMPAT_ALLOW_LITELLM_FALLBACK", "false").strip().lower() == "true"
    )
    fallback_switched = False
    buffered_chunks: list[str] = []
    for attempt in range(max_retries + 1):
        try:
            if openai_compat_http_primary:
                provider = params.get("custom_llm_provider")
                logger.info(f"[ask_llm] {provider} path: using raw HTTP as primary transport.")
                try:
                    async with AsyncTimer(key="exec ask_llm"):
                        async for raw_chunk in _raw_openai_like_request(
                            messages=messages,
                            params=params,
                            stream=stream,
                            only_content=only_content,
                        ):
                            if stream and only_content and isinstance(raw_chunk, str):
                                buffered_chunks.append(raw_chunk)
                            yield raw_chunk
                    return
                except Exception as raw_err:
                    if allow_litellm_fallback_for_openai_compat:
                        logger.warning(
                            f"[ask_llm] raw HTTP primary failed, fallback to LiteLLM is enabled. "
                            f"provider={provider}, model={params.get('model')}, error={raw_err}"
                        )
                    else:
                        logger.warning(
                            f"[ask_llm] raw HTTP primary failed, LiteLLM fallback disabled. "
                            f"provider={provider}, model={params.get('model')}, error={raw_err}"
                        )
                        raise raw_err

            response = await acompletion(messages=messages, stream=stream, **params)

            async with AsyncTimer(key="exec ask_llm"):
                if stream:
                    async for chunk in response:
                        if only_content:
                            text = extract_stream_chunk_text(chunk)
                            if text:
                                buffered_chunks.append(text)
                                yield text
                        else:
                            yield chunk
                else:
                    yield response.choices[0].message.content if only_content else response
            return
        except asyncio.CancelledError as e:
            logger.warning(f"[ask_llm] Request cancelled (attempt {attempt + 1}/{max_retries + 1}): {e}")
            if stream and only_content and buffered_chunks:
                try:
                    yield "".join(buffered_chunks)
                    return
                except Exception:
                    pass
            if attempt < max_retries:
                try:
                    if openai_compat_http_primary:
                        async for fallback_chunk in _raw_openai_like_request(
                            messages=messages,
                            params=params,
                            stream=False,
                            only_content=only_content,
                        ):
                            yield fallback_chunk
                            return
                    else:
                        fallback = await asyncio.shield(
                            acompletion(
                                messages=messages,
                                stream=False,
                                **params,
                            )
                        )
                        yield fallback.choices[0].message.content if only_content else fallback
                        return
                except Exception as ex:
                    logger.warning(f"[ask_llm] Fallback non-stream failed: {ex}")
                    continue
            raise e
        except Exception as e:
            current_model = params.get("model")
            can_switch = (
                not fallback_switched
                and params.get("custom_llm_provider") in {"openai", "openai_like"}
                and fallback_model
                and fallback_model != current_model
                and _is_permission_or_policy_block_error(e)
            )
            if can_switch:
                logger.warning(
                    f"[ask_llm] Model '{current_model}' was blocked by provider policy, "
                    f"retrying with fallback model '{fallback_model}'."
                )
                params["model"] = fallback_model
                if auto_max_tokens_from_model:
                    params["max_tokens"] = int(LLMModelInfoFactory.get_max_output(fallback_model, default=32000))
                fallback_switched = True
                continue

            if attempt == max_retries:
                logger.error(f"[ask_llm] Request failed after {max_retries + 1} attempts: {e}")
                raise e
            logger.warning(f"[ask_llm] Request failed (attempt {attempt + 1}/{max_retries + 1}): {e}")
            try:
                await asyncio.sleep(0.5)
            except asyncio.CancelledError:
                raise


def ask_llm_sync_iter(*args, **kwargs):
    """
    为同步调用方提供 ask_llm 的桥接能力，内部仍复用统一的异步请求逻辑。
    """
    result_queue: queue.Queue[tuple[str, Any]] = queue.Queue()

    async def _consume():
        try:
            async for item in ask_llm(*args, **kwargs):
                result_queue.put(("chunk", item))
        except Exception as exc:
            result_queue.put(("error", exc))
        finally:
            result_queue.put(("done", None))

    def _runner():
        asyncio.run(_consume())

    worker = threading.Thread(target=_runner, daemon=True)
    worker.start()

    pending_error: Optional[Exception] = None
    try:
        while True:
            kind, payload = result_queue.get()
            if kind == "chunk":
                yield payload
                continue
            if kind == "error":
                pending_error = payload
                continue
            if kind == "done":
                break
    finally:
        worker.join(timeout=0.1)

    if pending_error is not None:
        raise pending_error


if __name__ == "__main__":
    pass
