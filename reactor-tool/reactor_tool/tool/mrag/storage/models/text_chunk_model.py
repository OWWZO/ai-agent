# -*- coding: utf-8 -*-
"""写入 Qdrant 文本集合的 chunk payload 模型。"""
from typing import Optional, List

from pydantic import BaseModel, Field


class TextChunkModel(BaseModel):
    """文本切块：稠密向量 + BM25 稀疏向量 + 来源元数据。"""
    kb_id: str

    text: Optional[str] = Field(None, description="原始文本")
    vector: Optional[List[float]] = Field(None, description="文本向量")
    sparse_vector: Optional[dict] = Field(None, description="文本稀疏向量")
    ref_id: Optional[str] = Field(None, description="引用ID")
    file_sorted: Optional[str] = Field(None, description="文件内排序")
    chunk_type: Optional[str] = Field(None, description="切块类型")
    file_path: Optional[str] = Field(None, description="文件路径")
    filename: Optional[str] = Field(None, description="文件名")
    created: Optional[float] = Field(None, description="创建时间")
    split_type: Optional[str] = Field(None, description="切分类型")

    class Config:
        extra = "allow"
