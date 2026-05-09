# -*- coding: utf-8 -*-
# =====================
#
#
# Author: OpenAI Codex
# Date:   2026/4/25
# =====================
import base64
import json
import mimetypes
import os
import re
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.parse import unquote, urlparse

import httpx
from loguru import logger

from reactor_tool.model.protocal import ImageGenerationRequest
from reactor_tool.util.file_util import (
    _is_local_file_reference,
    _normalize_local_path,
    upload_file_by_path,
)
from reactor_tool.util.llm_util import (
    _build_openai_compat_headers,
    _normalize_openai_compat_api_base,
)


DEFAULT_IMAGE_MODEL = "gpt-image-2"
DEFAULT_IMAGE_SIZE = "1024x1024"
RESPONSES_FALLBACK_STATUS = {404, 405, 501}
DATA_URL_RE = re.compile(r"^data:(?P<mime>[^;,]+);base64,(?P<data>.+)$", re.IGNORECASE | re.DOTALL)
BASE64_IMAGE_RE = re.compile(r"[A-Za-z0-9+/=\s]{200,}")
HTTP_IMAGE_RE = re.compile(r"https?://[^\s\"'<>)]+" , re.IGNORECASE)
MARKDOWN_IMAGE_RE = re.compile(r"!\[[^\]]*]\((https?://[^)\s]+)\)", re.IGNORECASE)


@dataclass
class GeneratedImage:
    """统一承载从上游响应中提取出的图片结果。"""

    data_url: str | None = None
    url: str | None = None


def resolve_generation_mode(request: ImageGenerationRequest) -> str:
    """按请求显式值优先，其次根据是否存在参考图自动推断模式。"""
    if request.mode in {"images", "edits"}:
        return request.mode
    return "edits" if request.file_names else "images"


def extract_generated_images(payload: Any) -> list[GeneratedImage]:
    """兼容 Responses API、legacy images API 和 chat/completions 的多种返回结构。"""
    images: list[GeneratedImage] = []
    if not payload:
        return images

    if isinstance(payload, dict):
        output_items = payload.get("output")
        if isinstance(output_items, list):
            for item in output_items:
                if not isinstance(item, dict):
                    continue
                if item.get("type") == "image_generation_call" and item.get("result"):
                    images.append(GeneratedImage(data_url=_normalize_data_url(str(item["result"]))))
                    continue
                if item.get("type") == "message" and isinstance(item.get("content"), list):
                    images.extend(_extract_images_from_content_parts(item["content"]))

        data_items = payload.get("data")
        if isinstance(data_items, list):
            for item in data_items:
                if not isinstance(item, dict):
                    continue
                if item.get("url"):
                    images.append(GeneratedImage(url=str(item["url"])))
                elif item.get("b64_json"):
                    images.append(GeneratedImage(data_url=_normalize_data_url(str(item["b64_json"]))))

        choices = payload.get("choices")
        if isinstance(choices, list):
            for choice in choices:
                if not isinstance(choice, dict):
                    continue
                message = choice.get("message") or choice.get("delta") or {}
                if isinstance(message, dict):
                    content = message.get("content")
                    if isinstance(content, str):
                        images.extend(_extract_images_from_text(content))
                    elif isinstance(content, list):
                        images.extend(_extract_images_from_content_parts(content))

    if not images:
        images.extend(_extract_images_from_text(json.dumps(payload, ensure_ascii=False)))

    return _deduplicate_generated_images(images)


async def generate_images(request: ImageGenerationRequest) -> dict[str, Any]:
    """调用 OpenAI 兼容图片接口并把最终产物上传到现有文件服务。"""
    mode = resolve_generation_mode(request)
    if mode == "edits" and not request.file_names:
        raise ValueError("图生图模式至少需要一张参考图片")
    if request.mask_file_names and len(request.mask_file_names) > len(request.file_names):
        raise ValueError("maskFileNames 数量不能超过 fileNames")

    # 图片模型统一走独立环境变量，避免请求侧透传敏感配置或和服务端配置产生漂移。
    base_url = _resolve_base_url()
    api_key = _resolve_api_key()
    if not base_url:
        raise ValueError("未配置图片生成 base url，请设置 IMAGE_GENERATION_BASE_URL")
    if not api_key:
        raise ValueError("未配置图片生成 api key，请设置 IMAGE_GENERATION_API_KEY")

    normalized_base_url = _normalize_openai_compat_api_base(base_url)
    model_name = _resolve_model_name()
    if not model_name:
        raise ValueError("未配置图片生成 model，请设置 IMAGE_GENERATION_MODEL")
    timeout = httpx.Timeout(timeout=float(request.timeout_seconds))

    async with httpx.AsyncClient(timeout=timeout) as client:
        primary_request, fallback_request = await _build_generation_requests(
            request=request,
            mode=mode,
            base_url=normalized_base_url,
            model_name=model_name,
            client=client,
        )
        payload, used_fallback = await _execute_generation_request(
            client=client,
            api_key=api_key,
            primary_request=primary_request,
            fallback_request=fallback_request,
        )

        generated_images = extract_generated_images(payload)
        if not generated_images:
            text_output = _extract_text_output(payload)
            if text_output:
                raise RuntimeError(f"上游未返回图片内容，文本响应为：{text_output[:300]}")
            raise RuntimeError("上游未返回可识别的图片结果")

        file_info = await _upload_generated_images(
            client=client,
            request=request,
            generated_images=generated_images,
        )

    summary = _build_generation_summary(
        mode=mode,
        file_info=file_info,
        used_fallback=used_fallback,
    )
    return {
        "data": summary,
        "fileInfo": file_info,
        "requestId": request.request_id,
        "mode": mode,
        "usedFallback": used_fallback,
        "rawResponse": _sanitize_raw_response(payload),
    }


async def _build_generation_requests(
    request: ImageGenerationRequest,
    mode: str,
    base_url: str,
    model_name: str,
    client: httpx.AsyncClient,
) -> tuple[dict[str, Any], dict[str, Any] | None]:
    """构建 primary / fallback 请求体，保持和原型一致的接口降级策略。"""
    if mode == "images":
        tool = {"type": "image_generation"}
        if request.size:
            tool["size"] = request.size
        if request.n > 1:
            tool["n"] = request.n
        primary_request = {
            "url": f"{base_url}/responses",
            "body": {
                "model": model_name,
                "input": request.prompt,
                "tools": [tool],
            },
        }
        fallback_request = {
            "url": f"{base_url}/images/generations",
            "body": {
                "model": model_name,
                "prompt": request.prompt,
                "n": request.n,
                "size": request.size or DEFAULT_IMAGE_SIZE,
            },
        }
        return primary_request, fallback_request

    if len(request.file_names) == 1:
        primary_request = await _build_native_edit_request(
            request=request,
            base_url=base_url,
            model_name=model_name,
            client=client,
        )
        _, chat_content = await _build_edit_contents(request, client)
        fallback_request = {
            "url": f"{base_url}/chat/completions",
            "body": {
                "model": model_name,
                "messages": [{"role": "user", "content": chat_content}],
            },
        }
        return primary_request, fallback_request

    responses_content, chat_content = await _build_edit_contents(request, client)
    tool = {"type": "image_generation"}
    if request.size:
        tool["size"] = request.size
    if request.n > 1:
        tool["n"] = request.n

    primary_request = {
        "url": f"{base_url}/responses",
        "body": {
            "model": model_name,
            "input": [{"role": "user", "content": responses_content}],
            "tools": [tool],
        },
    }
    fallback_request = {
        "url": f"{base_url}/chat/completions",
        "body": {
            "model": model_name,
            "messages": [{"role": "user", "content": chat_content}],
        },
    }
    return primary_request, fallback_request


async def _build_native_edit_request(
    request: ImageGenerationRequest,
    base_url: str,
    model_name: str,
    client: httpx.AsyncClient,
) -> dict[str, Any]:
    """单图图生图优先走原生 /images/edits，局部编辑质量更稳定。"""
    image_reference = request.file_names[0]
    image_bytes, _ = await _reference_to_image_bytes(client, image_reference)
    files: list[tuple[str, tuple[str, bytes, str] | str]] = [
        ("model", (None, model_name)),
        ("prompt", (None, request.prompt)),
        ("response_format", (None, "b64_json")),
        ("image", ("image.png", image_bytes, "image/png")),
    ]
    if request.size:
        files.append(("size", (None, request.size)))

    mask_reference = request.mask_file_names[0] if request.mask_file_names else ""
    if mask_reference:
        mask_bytes, _ = await _reference_to_image_bytes(client, mask_reference)
        files.append(("mask", ("mask.png", mask_bytes, "image/png")))

    return {
        "url": f"{base_url}/images/edits",
        "body": files,
        "multipart": True,
    }


async def _build_edit_contents(
    request: ImageGenerationRequest,
    client: httpx.AsyncClient,
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """把参考图和可选遮罩图转成 Responses / chat 两套兼容输入。"""
    any_mask = bool(request.mask_file_names)
    image_count = len(request.file_names)

    if image_count == 1 and not any_mask:
        instruction = (
            "Edit the attached image as described. "
            "Output the full edited image, same dimensions as the input.\n\n"
            f"Instruction:\n{request.prompt}"
        )
    elif any_mask:
        instruction = (
            "Attached are reference image(s). For any image immediately followed by a duplicate "
            "with a semi-transparent red overlay, the red overlay marks the ONLY region to edit "
            "in that image. Treat the red overlay as an instruction, NOT as image content. "
            "Modify only pixels inside the highlighted region; pixels outside must remain "
            "pixel-identical to the original.\n\n"
            f"Instruction:\n{request.prompt}\n\nOutput a single final image."
        )
    else:
        instruction = (
            f"Attached are {image_count} reference images (in order). Use them together to produce "
            f"a single final image according to the instruction.\n\nInstruction:\n{request.prompt}"
        )

    responses_content: list[dict[str, Any]] = [{"type": "input_text", "text": instruction}]
    chat_content: list[dict[str, Any]] = [{"type": "text", "text": instruction}]

    for index, image_reference in enumerate(request.file_names):
        image_data_url = await _reference_to_data_url(client, image_reference)
        responses_content.append({"type": "input_image", "image_url": image_data_url})
        chat_content.append({"type": "image_url", "image_url": {"url": image_data_url}})

        if index < len(request.mask_file_names):
            mask_reference = request.mask_file_names[index]
            if mask_reference:
                mask_data_url = await _reference_to_data_url(client, mask_reference)
                responses_content.append({"type": "input_image", "image_url": mask_data_url})
                chat_content.append({"type": "image_url", "image_url": {"url": mask_data_url}})

    return responses_content, chat_content


async def _execute_generation_request(
    client: httpx.AsyncClient,
    api_key: str,
    primary_request: dict[str, Any],
    fallback_request: dict[str, Any] | None,
) -> tuple[Any, bool]:
    """优先走 Responses API，未实现时自动切换 legacy 接口。"""
    json_headers = _build_openai_compat_headers(
        {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
    )
    multipart_headers = _build_openai_compat_headers(
        {
            "Authorization": f"Bearer {api_key}",
            "Accept": "application/json",
        }
    )

    used_fallback = False
    try:
        response = await _post_generation_request(client, primary_request, json_headers, multipart_headers)
    except Exception:
        if not fallback_request:
            raise
        used_fallback = True
        response = await _post_generation_request(client, fallback_request, json_headers, multipart_headers)

    if (
        not response.is_success
        and fallback_request
        and not used_fallback
        and response.status_code in RESPONSES_FALLBACK_STATUS
    ):
        used_fallback = True
        response = await _post_generation_request(client, fallback_request, json_headers, multipart_headers)

    raw_text = response.text
    try:
        payload = response.json()
    except Exception:
        payload = raw_text

    if not response.is_success:
        raise RuntimeError(f"图片生成请求失败，status={response.status_code}，body={str(raw_text)[:500]}")

    return payload, used_fallback


async def _post_generation_request(
    client: httpx.AsyncClient,
    request: dict[str, Any],
    json_headers: dict[str, str],
    multipart_headers: dict[str, str],
) -> httpx.Response:
    """统一执行 JSON / multipart 两类上游请求。"""
    if request.get("multipart"):
        return await client.post(request["url"], headers=multipart_headers, files=request["body"])
    return await client.post(request["url"], headers=json_headers, json=request["body"])


async def _upload_generated_images(
    client: httpx.AsyncClient,
    request: ImageGenerationRequest,
    generated_images: Iterable[GeneratedImage],
) -> list[dict[str, Any]]:
    """把上游返回的图片内容固化成文件，并上传到现有文件服务。"""
    output_name = _sanitize_output_name(request.file_name or "图片生成结果")
    file_info: list[dict[str, Any]] = []
    images = list(generated_images)

    with tempfile.TemporaryDirectory(prefix="reactor-image-generation-") as temp_dir:
        temp_directory = Path(temp_dir)
        for index, image in enumerate(images):
            image_bytes, mime_type = await _materialize_generated_image(client, image)
            extension = _guess_extension(mime_type, image.url)
            file_name = _build_output_file_name(output_name, index, len(images), extension)
            target_path = temp_directory / file_name
            target_path.write_bytes(image_bytes)

            uploaded = await upload_file_by_path(str(target_path), request_id=request.request_id)
            if uploaded:
                file_info.append(uploaded)

    if not file_info:
        raise RuntimeError("图片生成成功，但上传产物失败")

    return file_info


async def _materialize_generated_image(
    client: httpx.AsyncClient,
    image: GeneratedImage,
) -> tuple[bytes, str]:
    """把 data url 或远程 url 统一转成二进制图片内容。"""
    if image.data_url:
        return _decode_image_data(image.data_url)

    if not image.url:
        raise RuntimeError("图片结果缺少 data_url 和 url")

    response = await client.get(image.url)
    response.raise_for_status()
    content = response.content
    mime_type = (
        response.headers.get("Content-Type", "").split(";", 1)[0].strip()
        or _guess_mime_from_name(image.url)
        or _guess_mime_from_bytes(content)
    )
    return content, mime_type or "image/png"


def _extract_images_from_content_parts(parts: list[Any]) -> list[GeneratedImage]:
    images: list[GeneratedImage] = []
    for part in parts:
        if not isinstance(part, dict):
            continue
        part_type = str(part.get("type") or "").strip()
        if part_type == "output_image":
            image_reference = part.get("image_url") or part.get("url") or part.get("b64_json") or part.get("image")
            if isinstance(image_reference, dict):
                image_reference = image_reference.get("url")
            if isinstance(image_reference, str) and image_reference:
                images.extend(_extract_images_from_text(image_reference))
            continue

        if part_type == "image_url":
            image_reference = part.get("image_url")
            if isinstance(image_reference, dict):
                image_reference = image_reference.get("url")
            if isinstance(image_reference, str) and image_reference:
                images.extend(_extract_images_from_text(image_reference))
            continue

        part_text = part.get("text")
        if isinstance(part_text, str) and part_text:
            images.extend(_extract_images_from_text(part_text))
    return images


def _extract_images_from_text(text: str) -> list[GeneratedImage]:
    if not text:
        return []

    images: list[GeneratedImage] = []
    for match in MARKDOWN_IMAGE_RE.finditer(text):
        images.append(GeneratedImage(url=match.group(1)))

    for match in re.finditer(r"data:image/[a-z0-9.+-]+;base64,[A-Za-z0-9+/=\s]+", text, re.IGNORECASE):
        try:
            images.append(GeneratedImage(data_url=_normalize_data_url(match.group(0))))
        except Exception:
            continue

    for match in HTTP_IMAGE_RE.finditer(text):
        candidate = match.group(0)
        if re.search(r"\.(png|jpe?g|gif|webp|bmp|svg)(\?|$)", candidate, re.IGNORECASE):
            images.append(GeneratedImage(url=candidate))

    if not images:
        base64_match = BASE64_IMAGE_RE.search(text)
        if base64_match:
            try:
                images.append(GeneratedImage(data_url=_normalize_data_url(base64_match.group(0))))
            except Exception:
                logger.debug("忽略无法解析的 Base64 图片片段")
    return images


def _deduplicate_generated_images(images: Iterable[GeneratedImage]) -> list[GeneratedImage]:
    unique: list[GeneratedImage] = []
    seen: set[str] = set()
    for image in images:
        if image.data_url:
            key = image.data_url[:120]
        elif image.url:
            key = image.url
        else:
            continue
        if key in seen:
            continue
        seen.add(key)
        unique.append(image)
    return unique


def _extract_text_output(payload: Any) -> str:
    """兜底提取文本，便于无图片场景报错定位。"""
    if isinstance(payload, str):
        return payload
    if not isinstance(payload, dict):
        return ""

    text_parts: list[str] = []
    output_items = payload.get("output")
    if isinstance(output_items, list):
        for item in output_items:
            if not isinstance(item, dict):
                continue
            if isinstance(item.get("content"), list):
                for part in item["content"]:
                    if isinstance(part, dict) and isinstance(part.get("text"), str):
                        text_parts.append(part["text"])

    choices = payload.get("choices")
    if isinstance(choices, list):
        for choice in choices:
            if not isinstance(choice, dict):
                continue
            message = choice.get("message") or choice.get("delta") or {}
            if isinstance(message, dict):
                content = message.get("content")
                if isinstance(content, str):
                    text_parts.append(content)
                elif isinstance(content, list):
                    for part in content:
                        if isinstance(part, dict) and isinstance(part.get("text"), str):
                            text_parts.append(part["text"])
    return "".join(text_parts)


async def _reference_to_data_url(client: httpx.AsyncClient, reference: str) -> str:
    """把本地文件或 URL 统一转成 data url，便于兼容 OpenAI 风格多模态输入。"""
    file_bytes, mime_type = await _reference_to_image_bytes(client, reference)
    return _bytes_to_data_url(file_bytes, mime_type)


async def _reference_to_image_bytes(client: httpx.AsyncClient, reference: str) -> tuple[bytes, str]:
    """把本地文件或 URL 统一转成二进制图片内容，供 multipart 与 data url 两种路径复用。"""
    if reference.startswith("data:"):
        return _decode_image_data(_normalize_data_url(reference))

    if _is_local_file_reference(reference):
        file_path = _normalize_local_path(reference)
        file_bytes = file_path.read_bytes()
        mime_type = _guess_mime_from_name(file_path.name) or _guess_mime_from_bytes(file_bytes) or "image/png"
        return file_bytes, mime_type

    response = await client.get(reference)
    response.raise_for_status()
    file_bytes = response.content
    mime_type = (
        response.headers.get("Content-Type", "").split(";", 1)[0].strip()
        or _guess_mime_from_name(reference)
        or _guess_mime_from_bytes(file_bytes)
        or "image/png"
    )
    return file_bytes, mime_type


def _resolve_base_url() -> str:
    return os.getenv("IMAGE_GENERATION_BASE_URL", "").strip()


def _resolve_api_key() -> str:
    return os.getenv("IMAGE_GENERATION_API_KEY", "").strip()


def _resolve_model_name() -> str:
    """优先读取专用环境变量，缺省时回退到绘画智能体默认模型。"""
    return os.getenv("IMAGE_GENERATION_MODEL", DEFAULT_IMAGE_MODEL).strip() or DEFAULT_IMAGE_MODEL


def _build_generation_summary(
    mode: str,
    file_info: list[dict[str, Any]],
    used_fallback: bool,
) -> str:
    action = "图片编辑" if mode == "edits" else "图片生成"
    file_names = "、".join(str(item.get("fileName") or "") for item in file_info if item.get("fileName"))
    fallback_hint = "；已自动切换兼容接口" if used_fallback else ""
    return f"{action}完成，共生成 {len(file_info)} 个图片文件：{file_names}{fallback_hint}"


def _sanitize_raw_response(payload: Any) -> Any:
    """调试面板只保留结构化信息，避免把整段大图 Base64 原样塞回前端。"""
    if isinstance(payload, dict):
        return {key: _sanitize_raw_response(value) for key, value in payload.items()}
    if isinstance(payload, list):
        return [_sanitize_raw_response(item) for item in payload]
    if isinstance(payload, str):
        normalized = payload.strip()
        compact = normalized.replace("\n", "").replace("\r", "")
        if normalized.startswith("data:image/") and len(normalized) > 200:
            return f"[omitted data url payload, length={len(normalized)}]"
        if BASE64_IMAGE_RE.fullmatch(compact) and len(compact) > 200:
            return f"[omitted base64 payload, length={len(compact)}]"
        return payload
    return payload


def _sanitize_output_name(raw_name: str) -> str:
    """清洗输出文件名，避免特殊字符导致上传失败。"""
    normalized = (raw_name or "图片生成结果").strip()
    normalized = re.sub(r"[<>:\"/\\|?*\x00-\x1F]+", "_", normalized).strip().rstrip(". ")
    if not normalized:
        return "图片生成结果"
    return normalized[:120]


def _build_output_file_name(base_name: str, index: int, total: int, extension: str) -> str:
    stem = Path(base_name).stem or "图片生成结果"
    suffix = f".{extension.lstrip('.')}"
    if total <= 1:
        return f"{stem}{suffix}"
    return f"{stem}_{index + 1}{suffix}"


def _guess_extension(mime_type: str | None, url: str | None) -> str:
    if mime_type:
        guessed = mimetypes.guess_extension(mime_type)
        if guessed:
            return guessed.lstrip(".")
    if url:
        suffix = Path(unquote(urlparse(url).path)).suffix.lstrip(".")
        if suffix:
            return suffix
    return "png"


def _guess_mime_from_name(name: str) -> str | None:
    guessed, _ = mimetypes.guess_type(name)
    return guessed


def _guess_mime_from_bytes(content: bytes) -> str | None:
    if content.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if content.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if content.startswith((b"GIF87a", b"GIF89a")):
        return "image/gif"
    if content[:4] == b"RIFF" and content[8:12] == b"WEBP":
        return "image/webp"
    if content.startswith(b"BM"):
        return "image/bmp"
    if content.lstrip().startswith(b"<svg"):
        return "image/svg+xml"
    return None


def _bytes_to_data_url(content: bytes, mime_type: str) -> str:
    encoded = base64.b64encode(content).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def _normalize_data_url(raw: str) -> str:
    """兼容纯 Base64 和 data url 两种输入，统一返回规范 data url。"""
    text = (raw or "").strip()
    if not text:
        raise ValueError("图片内容为空")

    match = DATA_URL_RE.match(text)
    if match:
        mime_type = match.group("mime").strip()
        base64_payload = re.sub(r"\s+", "", match.group("data"))
    else:
        mime_type = ""
        base64_payload = re.sub(r"\s+", "", text)

    base64_payload = _pad_base64(base64_payload)
    image_bytes = base64.b64decode(base64_payload, validate=True)
    detected_mime_type = mime_type or _guess_mime_from_bytes(image_bytes) or "image/png"
    normalized_base64 = base64.b64encode(image_bytes).decode("ascii")
    return f"data:{detected_mime_type};base64,{normalized_base64}"


def _decode_image_data(data_url: str) -> tuple[bytes, str]:
    match = DATA_URL_RE.match(data_url)
    if not match:
        raise ValueError("非法的 data url 图片内容")
    mime_type = match.group("mime").strip() or "image/png"
    image_bytes = base64.b64decode(_pad_base64(re.sub(r"\s+", "", match.group("data"))), validate=True)
    return image_bytes, mime_type


def _pad_base64(value: str) -> str:
    """补齐 Base64 尾部填充，兼容部分代理返回的非标准长度。"""
    remainder = len(value) % 4
    if remainder == 0:
        return value
    return value + ("=" * (4 - remainder))
