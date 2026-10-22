# -*- coding: utf-8 -*-
"""图片 OCR：DeepSeek / Paddle 等实现，统一 ocr(image_path) 接口。"""
import os
from abc import abstractmethod, ABC

import dotenv

from .logger_utils import logger
from .paddle_ocr_client import PaddleOCRClient
from ..generation.vlm import VLLMClient

dotenv.load_dotenv()


class OCRBase(ABC):
    """OCR 抽象基类。"""

    @abstractmethod
    def ocr(self, image: str) -> str:
        """对图片路径做 OCR，返回纯文本。"""
        pass


class DeepSeekOCR(OCRBase):
    """DeepSeek OCR API 实现。"""

    def __init__(self):
        self.api_key = os.getenv("DEEPSEEK_OCR_API_KEY")
        self.base_url = os.getenv("DEEPSEEK_OCR_BASE_URL")
        self.model = os.getenv("DEEPSEEK_OCR_MODEL_NAME")

    def ocr(self, image_path: str) -> str:
        try:
            prompt = "提取图片中的文字"
            client = VLLMClient(base_url=self.base_url, api_key=self.api_key, model_name=self.model)
            messages = client.convert_messages_with_image_path(prompt, image_path)
            response = client.completions(messages)
            return response
        except Exception as e:
            import traceback
            logger.error(traceback.format_exc())
            return ""


class VLMOCR(OCRBase):

    def ocr(self, image_path: str) -> str:
        try:
            prompt = "提取图片中的文字"
            client = VLLMClient()
            messages = client.convert_messages_with_image_path(prompt, image_path)
            response = client.completions(messages, max_tokens=1024, temperature=0, stream=False)
            return response
        except Exception as e:
            import traceback
            logger.error(traceback.format_exc())
            return ""


class PaddleOCRVLOCR(OCRBase):

    def __init__(self):
        self._client = PaddleOCRClient()

    def ocr(self, image_path: str) -> str:
        try:
            return self._client.extract_text(image_path)
        except Exception as e:
            import traceback
            logger.error(traceback.format_exc())
            return ""


def get_ocr_model() -> OCRBase:
    ocr_type = (os.getenv("OCR_TYPE") or "").strip().lower()
    if ocr_type == "deepseek-ocr":
        return DeepSeekOCR()
    elif ocr_type == "vlm-ocr":
        return VLMOCR()
    elif ocr_type == "paddleocr-vl":
        return PaddleOCRVLOCR()
    else:
        raise ValueError(f"不支持的OCR模型: {ocr_type}")
