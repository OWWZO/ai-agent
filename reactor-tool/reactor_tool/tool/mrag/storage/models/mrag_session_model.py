# -*- coding: utf-8 -*-
"""MRAG 会话模型：知识库范围、最近问答预览、轮次计数。"""
from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class MRagSessionModel(BaseModel):
    """一次 MRAG 对话会话。"""
    session_id: str
    title: str = Field(default="新对话")
    kb_scope: list[str] = Field(default_factory=list)  # 可多选知识库
    cover_kb_id: Optional[str] = None  # 列表封面用主库
    latest_question: Optional[str] = None
    latest_answer_preview: Optional[str] = None
    turn_count: int = 0
    status: str = "IDLE"  # IDLE / RUNNING 等
    deleted: int = 0
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None

