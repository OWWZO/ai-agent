# -*- coding: utf-8 -*-
# =====================
#
# Author: wanghanmin1
# Date:   2025/7/8
# =====================
"""搜索/抓取文档模型。

DeepSearch、WebFetch 等工具将网页结果统一封装为 Doc，便于后续切片、落盘与摘要。
"""
import uuid
from typing import Literal, Any
from dataclasses import dataclass, field


@dataclass
class Doc:
    """网页/检索文档数据类。"""
    doc_type: Literal["web_page"]  # 文档类型，当前仅支持网页
    content: str  # 正文内容
    title: str  # 标题
    link: str = ""  # 原始链接
    data: dict[str, Any] = field(default_factory=dict)  # 扩展元数据
    unique_id: str = field(default_factory=lambda: str(uuid.uuid4()))  # 全局唯一 ID

    is_chunk: bool = False  # 是否为切分后的 chunk
    chunk_id: int = -1  # chunk 序号，-1 表示完整文档

    def __str__(self):
        """人类可读摘要，便于日志打印。"""
        doc_type_map = {
            "web_page": "网页",
        }

        return (
            f"Doc(\n"
            f"  文档类型={doc_type_map.get(self.doc_type, self.doc_type)},\n"
            f"  文档标题={self.title},\n"
            f"  文档链接={self.link},\n"
            f"  文档内容={self.content},\n"
            f")"
        )

    def to_html(self):
        """转为简单 HTML 片段，用于报告或预览嵌入。"""
        return (
            f"<div>\n"
            f"  <p>文档类型:{self.doc_type}</p>\n"
            f"  <p>文档标题:{self.title}</p>\n"
            f"  <p>文档链接:{self.link}</p>\n"
            f"  <p>文档内容:{self.content}</p>\n"
            f"</div>"
        )

    def to_dict(self, truncate_len: int = 0):
        """序列化为字典；truncate_len>0 时截断 content，控制上下文长度。"""
        content = self.content[0:truncate_len] if truncate_len > 0 else self.content
        return {
            "doc_type": self.doc_type,
            "content": content,
            "title": self.title,
            "link": self.link,
            "data": self.data,
        }
