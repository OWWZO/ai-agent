# -*- coding: utf-8 -*-
"""知识库文件元数据：上传任务状态、来源类型、文档数。"""
from datetime import datetime
from typing import Optional

from pydantic import BaseModel


class KBFileModel(BaseModel):
    """库内单个文件/URL 资源记录。"""
    kb_id: Optional[str] = None
    file_id: Optional[str] = None
    file_url: Optional[str] = None
    title: Optional[str] = None
    task_id: Optional[str] = None  # 后台解析任务 ID
    file_ext: Optional[str] = None
    source_type: Optional[str] = None  # file / url
    task_status: Optional[dict] = None
    file_status: Optional[str] = None
    doc_count: Optional[int] = None  # 切分后文档/chunk 数
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None
    deleted: Optional[int] = None
    creator: Optional[str] = None
