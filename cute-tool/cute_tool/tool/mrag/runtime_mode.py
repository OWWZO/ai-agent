# -*- coding: utf-8 -*-
"""MRAG 运行时模式开关：图片索引 multimodal / text_proxy。"""
import os


IMAGE_INDEX_MODE_MULTIMODAL = "multimodal"  # 真多模态图片向量
IMAGE_INDEX_MODE_TEXT_PROXY = "text_proxy"  # 用文本 caption 代理图片
SUPPORTED_IMAGE_INDEX_MODES = {
    IMAGE_INDEX_MODE_MULTIMODAL,
    IMAGE_INDEX_MODE_TEXT_PROXY,
}


def get_image_index_mode() -> str:
    """读取图片索引模式，统一约束配置值，避免各处自行兜底。"""
    mode = (os.getenv("MRAG_IMAGE_INDEX_MODE") or IMAGE_INDEX_MODE_MULTIMODAL).strip().lower()
    if mode not in SUPPORTED_IMAGE_INDEX_MODES:
        raise ValueError(
            f"不支持的 MRAG 图片索引模式: {mode}，支持的模式: {sorted(SUPPORTED_IMAGE_INDEX_MODES)}"
        )
    return mode


def is_multimodal_image_index_enabled() -> bool:
    """只有 multimodal 模式才启用图片/页面向量集合与多模态 embedding。"""
    return get_image_index_mode() == IMAGE_INDEX_MODE_MULTIMODAL
