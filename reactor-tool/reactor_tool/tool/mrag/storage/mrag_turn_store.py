# -*- coding: utf-8 -*-
"""MRAG 对话轮次（turn）仓储接口。"""
from abc import ABC, abstractmethod

from .models.mrag_turn_model import MRagTurnModel


class MRagTurnStore(ABC):
    """单轮问答记录：创建、更新、按会话列出、按会话删除。"""

    @abstractmethod
    def create_turn(self, turn: MRagTurnModel) -> bool:
        pass

    @abstractmethod
    def update_turn(self, turn: MRagTurnModel) -> bool:
        pass

    @abstractmethod
    def list_turns(self, session_id: str) -> list[MRagTurnModel]:
        pass

    @abstractmethod
    def delete_by_session_id(self, session_id: str) -> int:
        pass

