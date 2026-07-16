# -*- coding: utf-8 -*-
"""图片/多模态 Embedding：Qwen-VL 等；text_proxy 模式下可禁用。"""
import os
from http import HTTPStatus
from typing import List

import requests
from PIL import Image

from .embedding import ImageEmbedding
from ..runtime_mode import is_multimodal_image_index_enabled
from ..utils import image_utils
from ..utils.logger_utils import logger
from ..utils.retry_utils import call_with_retry


def _normalize_dashscope_multimodal_embedding_base_url(base_url: str | None) -> str:
    """兼容把 OpenAI 兼容地址误填为多模态 embedding 地址的场景。"""
    default_url = (
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/"
        "multimodal-embedding/multimodal-embedding"
    )
    if not base_url:
        return default_url

    normalized = base_url.strip().rstrip("/")
    if normalized.endswith("/compatible-mode/v1"):
        return default_url
    return normalized


class QwenVLEmbedding(ImageEmbedding):
    """通义千问多模态向量：图/文统一编码进图片集合。"""

    def __init__(self):
        super().__init__()
        self.timeout = int(os.getenv("API_TIMEOUT", 300))
        self.api_key = os.getenv("DASHSCOPE_API_KEY")
        self.dimension = int(os.getenv("IMAGE_EMBEDDING_DIMENSION", "0"))
        self.model_name = "qwen2.5-vl-embedding"
        self.dashscope_base_url = _normalize_dashscope_multimodal_embedding_base_url(
            os.getenv("DASHSCOPE_MULTIMODAL_EMBEDDING_BASE_URL")
        )

        if os.getenv("DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL_NAME"):
            self.model_name = os.getenv("DASHSCOPE_MULTIMODAL_EMBEDDING_MODEL_NAME")

    @staticmethod
    def _image_to_base64(image: Image.Image) -> str:
        """
        将PIL图像转换为base64编码字符串

        Args:
            image: PIL图像对象

        Returns:
            base64编码的图像字符串
        """
        return "data:image/png;base64," + image_utils.image_to_base64(image)

    def _encode_image(self, image: Image.Image) -> list[float]:
        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json"
        }

        body = {
            "model": self.model_name,
            "input": {
                "contents": [{"image": self._image_to_base64(image)}]
            }
        }
        self._apply_dimension(body)

        def _do_request():
            resp = requests.post(
                self.dashscope_base_url,
                headers=headers,
                json=body,
                timeout=self.timeout
            )
            if resp.status_code == HTTPStatus.OK:
                return resp.json()["output"]["embeddings"][0]["embedding"]
            if resp.status_code in {408, 409, 425, 429, 500, 502, 503, 504}:
                raise RuntimeError(
                    f"image embedding upstream transient error: status={resp.status_code}, body={resp.text[:300]}"
                )
            logger.error(f"图片编码失败: status={resp.status_code}, body={resp.text}")
            return []

        return call_with_retry(
            _do_request,
            label=f"mrag-image-embedding:{self.model_name or 'unknown'}",
        )

    def _encode_image_batch(self, images: List[Image.Image]) -> list[list[float]]:
        """
        批量编码图片为向量

        Args:
            images: 图片列表

        Returns:
            向量数组，形状为 (len(images), embedding_dim)
        """
        if not images:
            return []

        embeddings = []
        for image in images:
            embedding = self._encode_image(image)
            embeddings.append(embedding)
        return embeddings

    def _encode_text(self, text: str):
        try:
            headers = {
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json"
            }

            contents = [{"text": text}]
            body = {
                "model": self.model_name,
                "input": {
                    "contents": contents
                }
            }
            self._apply_dimension(body)

            def _do_request():
                resp = requests.post(
                    self.dashscope_base_url,
                    headers=headers,
                    json=body,
                    timeout=self.timeout
                )
                if resp.status_code == HTTPStatus.OK:
                    output_data = resp.json()
                    return output_data["output"]["embeddings"][0]["embedding"]
                if resp.status_code in {408, 409, 425, 429, 500, 502, 503, 504}:
                    raise RuntimeError(
                        f"text embedding upstream transient error: status={resp.status_code}, body={resp.text[:300]}"
                    )
                logger.error(f"文本编码失败: {resp}")
                return []

            return call_with_retry(
                _do_request,
                label=f"mrag-image-text-embedding:{self.model_name or 'unknown'}",
            )

        except Exception as e:
            import traceback
            print(traceback.format_exc())
            raise Exception(f"文本编码失败: {e}") from e

    def _encode_text_batch(self, texts: List[str]) -> list[list[float]]:
        """
        批量编码图片为向量

        Args:
            texts: 文本列表

        Returns:
            向量数组，形状为 (len(images), embedding_dim)
        """
        if not texts:
            return []
        embeddings = []
        for text in texts:
            embedding = self._encode_text(text)
            embeddings.append(embedding)
        return embeddings

    def _apply_dimension(self, body: dict) -> None:
        """将配置中的向量维度显式透传给百炼接口，避免依赖服务端默认值。"""
        if self.dimension <= 0:
            return
        body["parameters"] = {"dimension": self.dimension}


class DisabledImageEmbedding(ImageEmbedding):
    """text_proxy 模式下的占位实现，避免误走图片 embedding 链路。"""

    def _encode_image_batch(self, images: List[Image.Image]) -> list[list[float]]:
        if images:
            logger.info("MRAG 图片向量已关闭，跳过图片 embedding 计算")
        return [[] for _ in images]

    def _encode_text_batch(self, texts: List[str]) -> list[list[float]]:
        if texts:
            logger.info("MRAG 图片向量已关闭，跳过文本到图片 embedding 计算")
        return [[] for _ in texts]


def get_image_embedding_model() -> ImageEmbedding:
    """获取图像embedding模型"""
    if not is_multimodal_image_index_enabled():
        return DisabledImageEmbedding()

    image_embedding_type = os.getenv("IMAGE_EMBEDDING_TYPE")
    if image_embedding_type == "dashscope":
        return QwenVLEmbedding()
    else:
        raise ValueError(f"不支持的图像embedding模型: {image_embedding_type}")
