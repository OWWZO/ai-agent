"""
存储模块

- VectorStore：Qdrant 向量统一门面（懒加载单例）
- store_factory：KB/会话/turn 等 SQLite 元数据仓储
- base_vector_store / qdrant_*：向量库抽象与实现
"""

__all__ = ["VectorStore"]


def __getattr__(name):
    # 延迟导入，避免未装 qdrant 时阻塞其它模块
    if name == "VectorStore":
        from .vector_store import VectorStore
        return VectorStore
    raise AttributeError(name)
