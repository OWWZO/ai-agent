# -*- coding: utf-8 -*-
"""知识库文档/chunk 元数据（含整篇正文 canonical 回显）。"""
from typing import Optional

from pydantic import BaseModel


CANONICAL_FULL_TEXT_CHUNK_TYPE = "canonical_full_text"


def build_canonical_doc_id(file_id: str) -> str:
    """为整篇正文回显生成稳定 doc_id，避免前端耦合临时目录结构。"""
    return f"{file_id}#canonical"


class KBDocModel(BaseModel):
    """单条文档块或 canonical 全文记录。"""
    kb_id: str
    doc_id: Optional[str]
    text: Optional[str]
    chunk_type: Optional[str]
    file_id: Optional[str]
    title: Optional[str]
    file_url: Optional[str]
    parent_id: Optional[str]
    deleted: Optional[int]
    create_time: Optional[str]
    modify_time: Optional[str]
    creator: Optional[str]
    modifier: Optional[str]
