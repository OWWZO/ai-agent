# -*- coding: utf-8 -*-
"""知识库文件数据源类型。"""
import enum


class SourceTypeEnum(enum.Enum):
    """数据源类型：本地文件 / 网页 URL。"""
    FILE = "file"
    URL = "url"
