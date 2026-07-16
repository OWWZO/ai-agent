# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/9
# =====================
"""文件元数据表定义（SQLModel，文件服务主路径）。"""
from datetime import datetime
from typing import Optional

from sqlalchemy import DateTime, text
from sqlmodel import SQLModel, Field


class FileInfo(SQLModel, table=True):
    """会话/请求维度的产物文件索引。

    file_id: 逻辑唯一键（通常为 request_id+file_name 的 MD5）
    file_path: 本地落盘绝对/相对路径
    request_id: 会话/请求作用域，用于按会话列文件
    """
    id: int | None = Field(default=None, primary_key=True)
    file_id: str = Field(unique=True, nullable=True)
    filename: str = Field()
    file_path: str = Field()
    description: Optional[str]
    file_size: Optional[int]
    status: int = Field(default=0)  # 0 无效/删除，1 正常
    request_id: Optional[str] = Field(default=None)
    create_time: Optional[datetime] = Field(
        sa_type=DateTime, default=None, nullable=False, sa_column_kwargs={"server_default": text("CURRENT_TIMESTAMP")}
    )
