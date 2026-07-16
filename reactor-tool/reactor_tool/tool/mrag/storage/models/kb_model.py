# -*- coding: utf-8 -*-
"""知识库元数据模型。"""
from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class KBModel(BaseModel):
    """知识库：切分策略、描述与软删除标记。"""
    kb_id: str
    kb_name: Optional[str] = None
    kb_desc: Optional[str] = None
    chunk_type: Optional[str] = None
    chunk_size: Optional[int] = None
    chunk_overlap_size: Optional[int] = None
    deleted: Optional[int] = None
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None
    creator: Optional[str] = None
