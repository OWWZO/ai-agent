# -*- coding: utf-8 -*-
"""文档解析与离线处理：parser 解析 → processor 切分向量化 → splitter 分句。"""
from .parser import DocumentParser
from .processor import DocumentProcessor

__all__ = ["DocumentParser", "DocumentProcessor"]
