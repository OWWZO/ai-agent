"""
向量存储配置管理模块

提供集中化的配置管理，避免重复读取配置文件。
"""
import os
from typing import Dict, Optional

import dotenv

from ..runtime_mode import get_image_index_mode, is_multimodal_image_index_enabled

dotenv.load_dotenv()


class VectorStoreConfig:
    """向量存储配置管理类"""

    def __init__(self, config_dict: Optional[Dict] = None):
        """
        初始化配置
        
        Args:
            config_dict: 配置字典，如果为 None 则从配置文件读取
        """
        self.store_type = os.getenv("VECTOR_STORE_TYPE")
        self.image_index_mode = get_image_index_mode()
        self.image_vector_enabled = is_multimodal_image_index_enabled()
        self.text_collection = os.getenv("TEXT_COLLECTION")
        self.image_collection = os.getenv("IMAGE_COLLECTION")
        self.page_collection = os.getenv("PAGE_COLLECTION")
        self.text_dimension = int(os.getenv("TEXT_EMBEDDING_DIMENSION"))
        self.image_dimension = self._read_int_env(
            "IMAGE_EMBEDDING_DIMENSION",
            required=self.image_vector_enabled,
        )

    @staticmethod
    def _read_int_env(name: str, required: bool) -> int:
        """按模式读取可选整数配置，text_proxy 下允许图片维度留空。"""
        raw_value = os.getenv(name)
        if raw_value is None or not raw_value.strip():
            if required:
                raise ValueError(f"环境变量 {name} 未配置")
            return 0
        return int(raw_value)

    @property
    def page_dimension(self) -> int:
        """页面向量维度"""
        return self.image_dimension
