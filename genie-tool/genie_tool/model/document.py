# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: wanghanmin1
# Date:   2025/7/8
# =====================
import uuid
from typing import Literal, Any
from dataclasses import dataclass, field


@dataclass
class Doc:
    """文档数据类"""
    doc_type: Literal["web_page"]
    content: str
    title: str
    link: str = ""
    data: dict[str, Any] = field(default_factory=dict)
    unique_id: str = field(default_factory=lambda: str(uuid.uuid4()))

    is_chunk: bool = False
    chunk_id: int = -1  # chunk标记

    def __str__(self):
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
        return (
            f"<div>\n"
            f"  <p>文档类型:{self.doc_type}</p>\n"
            f"  <p>文档标题:{self.title}</p>\n"
            f"  <p>文档链接:{self.link}</p>\n"
            f"  <p>文档内容:{self.content}</p>\n"
            f"</div>"
        )

    def to_dict(self, truncate_len: int = 0):
        content = self.content[0:truncate_len] if truncate_len > 0 else self.content
        return {
            "doc_type": self.doc_type,
            "content": content,
            "title": self.title,
            "link": self.link,
            "data": self.data,
        }

