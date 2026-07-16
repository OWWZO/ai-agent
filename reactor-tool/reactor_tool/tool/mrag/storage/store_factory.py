# -*- coding: utf-8 -*-
"""元数据 Store 工厂：按 STORE_TYPE（默认 sqlite）返回各仓储实现。"""
import os

from sqlalchemy import create_engine

from .kb_doc_store import KBDocStore
from .kb_file_store import KBFileStore
from .kb_store import KBStore
from .mrag_session_store import MRagSessionStore
from .mrag_turn_store import MRagTurnStore

_sqlite_engine = None

store_type = os.getenv("STORE_TYPE", "sqlite")

if store_type == "sqlite":
    local_path = os.getenv("SQLITE_PATH", "kb_file.db")
    _sqlite_engine = create_engine(f"sqlite:///{local_path}")


def get_kb_file_store() -> KBFileStore:
    """知识库文件元数据仓储。"""
    global _sqlite_engine, store_type
    if store_type == "sqlite":
        from .kb_file_store_sqlite_impl import KBFileSQLite
        return KBFileSQLite(_sqlite_engine)
    else:
        raise Exception("Unknown store type")


def get_kb_store() -> KBStore:
    """知识库本体仓储。"""
    global _sqlite_engine, store_type
    if store_type == "sqlite":
        from .kb_store_sqlite_impl import KBStoreSQLite
        return KBStoreSQLite(_sqlite_engine)
    else:
        raise Exception("Unknown store type")


def get_kb_doc_store() -> KBDocStore:
    """文档/chunk 元数据仓储。"""
    global _sqlite_engine, store_type
    if store_type == "sqlite":
        from .kb_doc_store_sqlite_impl import KBDocSQLite
        return KBDocSQLite(_sqlite_engine)
    else:
        raise Exception("Unknown store type")


def get_mrag_session_store() -> MRagSessionStore:
    """MRAG 对话会话仓储。"""
    global _sqlite_engine, store_type
    if store_type == "sqlite":
        from .mrag_session_store_sqlite_impl import MRagSessionSQLite
        return MRagSessionSQLite(_sqlite_engine)
    else:
        raise Exception("Unknown store type")


def get_mrag_turn_store() -> MRagTurnStore:
    """MRAG 对话轮次仓储。"""
    global _sqlite_engine, store_type
    if store_type == "sqlite":
        from .mrag_turn_store_sqlite_impl import MRagTurnSQLite
        return MRagTurnSQLite(_sqlite_engine)
    else:
        raise Exception("Unknown store type")
