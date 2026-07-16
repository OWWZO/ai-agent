# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/9
# =====================
"""SQLite 引擎与异步 Session 工厂。

文件服务元数据默认落在 SQLITE_DB_PATH（默认 autobots.db）。
"""
import os
from typing import Callable, AsyncGenerator

from loguru import logger
from sqlalchemy import AsyncAdaptedQueuePool, create_engine
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlmodel import SQLModel


# 可通过环境变量覆盖库文件路径
SQLITE_DB_PATH = os.environ.get("SQLITE_DB_PATH", "autobots.db")

# 同步引擎：建表、脚本初始化
engine = create_engine(f"sqlite:///{SQLITE_DB_PATH}", echo=True)

# 异步引擎：FastAPI 请求路径读写
async_engine = create_async_engine(
    f"sqlite+aiosqlite:///{SQLITE_DB_PATH}",
    poolclass=AsyncAdaptedQueuePool,
    pool_size=10,
    pool_recycle=3600,
    echo=False,
)

async_session_local: Callable[..., AsyncSession] = sessionmaker(bind=async_engine, class_=AsyncSession)


async def get_async_session() -> AsyncGenerator[AsyncSession, None]:
    """session 生成器，可作为 FastAPI Depends 注入。"""
    async with async_session_local() as session:
        yield session


def init_db():
    """根据 SQLModel 元数据创建表（幂等）。"""
    from reactor_tool.db.file_table import FileInfo  # noqa: F401 — 触发表注册
    SQLModel.metadata.create_all(engine)
    logger.info(f"DB init done")


if __name__ == "__main__":
    init_db()
