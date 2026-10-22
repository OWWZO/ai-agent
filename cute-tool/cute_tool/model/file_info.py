#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SQLAlchemy 版文件元数据模型（历史/兼容路径）。

实际文件服务主路径使用 db/file_table.py 的 SQLModel FileInfo；
本文件保留 ORM 基类与字段定义，供部分适配层复用。
"""
from datetime import datetime

from sqlalchemy import String, func
from sqlalchemy.orm import Mapped, mapped_column
from typing import Annotated

from sqlalchemy.orm import DeclarativeBase, MappedAsDataclass, declared_attr, mapped_column
from sqlalchemy.ext.asyncio import AsyncAttrs


class MappedBase(AsyncAttrs, DeclarativeBase):
    """
    声明式基类, 作为所有基类或数据模型类的父类而存在

    `AsyncAttrs <https://docs.sqlalchemy.org/en/20/orm/extensions/asyncio.html#sqlalchemy.ext.asyncio.AsyncAttrs>`__
    `DeclarativeBase <https://docs.sqlalchemy.org/en/20/orm/declarative_config.html>`__
    `mapped_column() <https://docs.sqlalchemy.org/en/20/orm/mapping_api.html#sqlalchemy.orm.mapped_column>`__
    """

    @declared_attr.directive
    def __tablename__(cls) -> str:
        # 默认表名 = 类名小写
        return cls.__name__.lower()


class DataClassBase(MappedAsDataclass, MappedBase):
    """
    声明性数据类基类, 它将带有数据类集成, 允许使用更高级配置, 但你必须注意它的一些特性, 尤其是和 DeclarativeBase 一起使用时

    `MappedAsDataclass <https://docs.sqlalchemy.org/en/20/orm/dataclasses.html#orm-declarative-native-dataclasses>`__
    """  # noqa: E501

    __abstract__ = True


# 通用 Mapped 类型主键, 需手动添加
# MappedBase -> id: Mapped[id_key]
# DataClassBase && Base -> id: Mapped[id_key] = mapped_column(init=False)
id_key = Annotated[
    int, mapped_column(primary_key=True, index=True, autoincrement=True, sort_order=-999, comment='主键id')
]


class FileInfo(DataClassBase):
    """文档/产物文件元信息（ORM 映射）。"""

    __tablename__ = 'file_info'

    id: Mapped[id_key] = mapped_column(init=False)
    filename: Mapped[str] = mapped_column(String(20), comment='文件名')
    request_id: Mapped[str] = mapped_column(String(255), comment='请求id')
    description: Mapped[str | None] = mapped_column(String(255), comment='描述')
    file_path: Mapped[str] = mapped_column(String(50), comment='本地存储路径')
    file_size: Mapped[int] = mapped_column(comment='文件大小(字节)')
    status: Mapped[int] = mapped_column(default=1, comment='文件状态(0删除 1正常)')
    create_time: Mapped[datetime] = mapped_column(
        init=False,
        server_default=func.now(),  # 数据库侧自动生成创建时间
        comment='创建时间'
    )
