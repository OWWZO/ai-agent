# -*- coding: utf-8 -*-
"""元数据 Store 工厂：按 STORE_TYPE 返回各仓储实现。

原本是 5 个只差导入类名的分支函数，每个都重复 ``global _sqlite_engine,
store_type`` + ``if store_type == "sqlite": ... else: raise``。仓库里只有 sqlite
一种实现，所以这里用一张注册表 + 一个共享 engine 收敛。

新增后端时只需在 ``_STORES`` 里加一行，不必再复制 5 段样板。
"""
import os

from sqlalchemy import create_engine

from .kb_doc_store_sqlite_impl import KBDocSQLite
from .kb_file_store_sqlite_impl import KBFileSQLite
from .kb_store_sqlite_impl import KBStoreSQLite
from .mrag_session_store_sqlite_impl import MRagSessionSQLite
from .mrag_turn_store_sqlite_impl import MRagTurnSQLite

# 后端名 -> 各仓储实现类。键与 STORE_TYPE 环境变量取值一致。
_STORES: dict[str, dict[str, type]] = {
    "sqlite": {
        "kb_file": KBFileSQLite,
        "kb": KBStoreSQLite,
        "kb_doc": KBDocSQLite,
        "mrag_session": MRagSessionSQLite,
        "mrag_turn": MRagTurnSQLite,
    },
}

_store_type = os.getenv("STORE_TYPE", "sqlite")

# 只在真正取仓储时才校验 STORE_TYPE，不在导入期 raise：本模块被 api/tool.py 顶层
# 导入，若在这里抛错，一个 MRAG 专用配置写错会让整个服务（含 code_interpreter /
# deepsearch 等无关接口）启动失败。旧实现也是按需抛错的，这里保持同样的爆炸半径。
_engine = None
if _store_type in _STORES:
    # 单个 engine 供所有仓储共享：同进程内 5 个仓储共用一个连接池，
    # 而不是各自 create_all 一份 schema。
    _engine = create_engine(
        f"sqlite:///{os.getenv('SQLITE_PATH', 'kb_file.db')}"
    )


def _get(kind: str):
    """按种类取仓储实例；engine 由本模块统一持有。"""
    if _store_type not in _STORES:
        raise ValueError(
            f"Unknown store type: {_store_type}. Available: {sorted(_STORES)}"
        )
    return _STORES[_store_type][kind](_engine)


def get_kb_file_store() -> KBFileSQLite:
    """知识库文件元数据仓储。"""
    return _get("kb_file")


def get_kb_store() -> KBStoreSQLite:
    """知识库本体仓储。"""
    return _get("kb")


def get_kb_doc_store() -> KBDocSQLite:
    """文档/chunk 元数据仓储。"""
    return _get("kb_doc")


def get_mrag_session_store() -> MRagSessionSQLite:
    """MRAG 对话会话仓储。"""
    return _get("mrag_session")


def get_mrag_turn_store() -> MRagTurnSQLite:
    """MRAG 对话轮次仓储。"""
    return _get("mrag_turn")
