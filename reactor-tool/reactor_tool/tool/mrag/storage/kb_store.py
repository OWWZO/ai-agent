# -*- coding: utf-8 -*-
"""知识库（KB）元数据仓储接口。"""
from abc import abstractmethod, ABC
from typing import List

from .models.kb_model import KBModel


class KBStore(ABC):
    """知识库 CRUD 抽象。"""

    @abstractmethod
    def create_kb(self, kb_model: KBModel) -> bool:
        """创建知识库。"""
        pass

    @abstractmethod
    def delete_kb(self, kb_model: KBModel) -> bool:
        """删除知识库（逻辑/物理由实现决定）。"""
        pass

    @abstractmethod
    def update_kb(self, kb_model: KBModel) -> bool:
        """更新知识库配置（切分参数等）。"""
        pass

    @abstractmethod
    def get_kbs(self, page_no: int, page_size: int) -> List[KBModel]:
        """分页列出知识库。"""
        pass
