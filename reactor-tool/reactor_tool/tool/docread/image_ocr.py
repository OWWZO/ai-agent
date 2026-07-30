# -*- coding: utf-8 -*-
"""Image OCR via existing MRAG cloud OCR backends (OCR_TYPE)."""
from __future__ import annotations

import os
from pathlib import Path
from typing import Any

_ALLOWED_SUFFIXES = frozenset({
    ".png", ".jpg", ".jpeg", ".bmp", ".webp", ".tif", ".tiff", ".gif",
})
_MAX_BYTES = 20 * 1024 * 1024  # 20MB


def run_image_ocr_sync(file_path: str, *, prompt: str | None = None, lang: str | None = None) -> dict[str, Any]:
    """Run OCR on a local image path using get_ocr_model().

    Returns a data dict with text/metadata. Raises on validation or empty OCR.
    """
    path = Path(file_path)
    if not path.is_file():
        raise FileNotFoundError(f"Image not found: {file_path}")
    suffix = path.suffix.lower()
    if suffix not in _ALLOWED_SUFFIXES:
        raise ValueError(
            f"Unsupported image type: {suffix or '(none)'}. "
            f"Allowed: {', '.join(sorted(_ALLOWED_SUFFIXES))}"
        )
    size = path.stat().st_size
    if size > _MAX_BYTES:
        raise ValueError(f"Image too large: {size} bytes (max {_MAX_BYTES})")
    if size == 0:
        raise ValueError("Image file is empty")

    ocr_type = (os.getenv("OCR_TYPE") or "").strip().lower() or "unknown"
    text = _invoke_ocr(str(path.resolve()), prompt=prompt, lang=lang)
    text = (text or "").strip()
    if not text:
        raise RuntimeError(
            f"OCR returned empty text (ocr_type={ocr_type}). "
            "Check OCR_TYPE / VLM_* / DEEPSEEK_OCR_* / PADDLE_OCR_* credentials."
        )
    return {
        "text": text,
        "char_count": len(text),
        "ocr_type": ocr_type,
        "file_path": str(path.resolve()),
        "file_name": path.name,
        "file_size": size,
        "lang": lang or "",
        "source": "mrag.ocr_utils",
    }


def _invoke_ocr(image_path: str, *, prompt: str | None, lang: str | None) -> str:
    """Prefer custom prompt on VLM backends; otherwise standard get_ocr_model()."""
    ocr_type = (os.getenv("OCR_TYPE") or "").strip().lower()
    custom = (prompt or "").strip()
    if custom and ocr_type in {"vlm-ocr", "deepseek-ocr"}:
        return _vlm_ocr_with_prompt(image_path, custom, ocr_type=ocr_type)
    # lang is only a soft hint for default prompt on VLM backends
    if lang and ocr_type in {"vlm-ocr", "deepseek-ocr"} and not custom:
        hint = {
            "ch": "提取图片中的中文文字，保持原有顺序和换行。",
            "en": "Extract all English text from the image. Preserve line breaks.",
            "ch_en": "提取图片中的中英文文字，保持原有顺序和换行。",
        }.get(str(lang).strip().lower())
        if hint:
            return _vlm_ocr_with_prompt(image_path, hint, ocr_type=ocr_type)

    from reactor_tool.tool.mrag.utils.ocr_utils import get_ocr_model

    return get_ocr_model().ocr(image_path)


def _vlm_ocr_with_prompt(image_path: str, prompt: str, *, ocr_type: str) -> str:
    from reactor_tool.tool.mrag.generation.vlm import VLLMClient

    if ocr_type == "deepseek-ocr":
        client = VLLMClient(
            base_url=os.getenv("DEEPSEEK_OCR_BASE_URL"),
            api_key=os.getenv("DEEPSEEK_OCR_API_KEY"),
            model_name=os.getenv("DEEPSEEK_OCR_MODEL_NAME"),
        )
    else:
        client = VLLMClient()
    messages = client.convert_messages_with_image_path(prompt, image_path)
    return client.completions(messages, max_tokens=2048, temperature=0, stream=False)
