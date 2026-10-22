# -*- coding: utf-8 -*-
"""MRAG 单轮问答模型：问题、答案、引用 chunk、请求图/库范围。"""
from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, Field


class MRagTurnModel(BaseModel):
    """会话内一轮问答。"""
    turn_id: str
    session_id: str
    question: str
    answer_markdown: str = ""
    status: str = "RUNNING"
    error_message: str = ""
    request_kb_scope: list[str] = Field(default_factory=list)
    request_image_urls: list[str] = Field(default_factory=list)
    answer_image_urls: list[str] = Field(default_factory=list)
    raw_chunks: list[Any] = Field(default_factory=list)  # 检索命中原始结构
    deleted: int = 0
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None

