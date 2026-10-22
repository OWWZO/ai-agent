# -*- coding: utf-8 -*-
"""知识库文档/chunk 元数据 SQLite 实现（表 t_kb_doc）。

含 canonical 全文 upsert/get，供前端整篇预览。
"""
from datetime import datetime
from typing import List, Optional

from sqlalchemy import Column, Integer, String, DateTime, Text
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session

from cute_tool.tool.mrag.storage.models.kb_doc_model import (
    CANONICAL_FULL_TEXT_CHUNK_TYPE,
    KBDocModel as KBDocPydanticModel,
)

Base = declarative_base()


class KBDocSQLModel(Base):
    """SQLAlchemy ORM：文档块 / 全文行。"""
    __tablename__ = "t_kb_doc"
    id = Column(Integer, primary_key=True, autoincrement=True)
    kb_id = Column(String, nullable=False)
    doc_id = Column(String, nullable=False)
    text = Column(Text, nullable=False)
    chunk_type = Column(String, nullable=False)
    file_id = Column(String, nullable=False)
    title = Column(String, nullable=False)
    file_url = Column(String, nullable=False)

    parent_id = Column(String, nullable=False)

    deleted = Column(Integer, nullable=False)
    create_time = Column(DateTime, nullable=False)
    modify_time = Column(DateTime, nullable=False)
    creator = Column(String, nullable=True)
    modifier = Column(String, nullable=True)

    def to_pydantic(self) -> KBDocPydanticModel:
        """ORM → Pydantic（时间转 isoformat 字符串）。"""
        return KBDocPydanticModel(
            kb_id=self.kb_id,
            doc_id=self.doc_id,
            text=self.text,
            chunk_type=self.chunk_type,
            file_id=self.file_id,
            title=self.title,
            file_url=self.file_url,
            parent_id=self.parent_id,
            deleted=self.deleted,
            create_time=self.create_time.isoformat() if self.create_time else None,
            modify_time=self.modify_time.isoformat() if self.modify_time else None,
            creator=self.creator,
            modifier=self.modifier
        )

    @staticmethod
    def from_pydantic(pydantic_model: KBDocPydanticModel) -> 'KBDocSQLModel':
        """Convert Pydantic model to SQLAlchemy model"""
        return KBDocSQLModel(
            kb_id=pydantic_model.kb_id or "",
            doc_id=pydantic_model.doc_id or "",
            text=pydantic_model.text or "",
            chunk_type=pydantic_model.chunk_type or "",
            file_id=pydantic_model.file_id or "",
            title=pydantic_model.title or "",
            file_url=pydantic_model.file_url or "",
            parent_id=pydantic_model.parent_id or "",
            deleted=pydantic_model.deleted or 0,
            create_time=datetime.fromisoformat(pydantic_model.create_time) if pydantic_model.create_time else datetime.now(),
            modify_time=datetime.fromisoformat(pydantic_model.modify_time) if pydantic_model.modify_time else datetime.now(),
            creator=pydantic_model.creator,
            modifier=pydantic_model.modifier
        )


class KBDocSQLite:
    __tablename__ = "t_kb_doc"

    def __init__(self, engine):
        self._engine = engine
        self._session_factory = sessionmaker(bind=engine)

        Base.metadata.create_all(self._engine)

    def _get_session(self) -> Session:
        # 每个仓储操作独立创建并在 finally 关闭会话，避免长连接把 SQLite 事务状态带到下一次请求。
        return self._session_factory()

    def upsert_canonical_doc(self, kb_doc: KBDocPydanticModel) -> bool:
        """按文件覆盖 canonical 正文记录，保证回显内容稳定且唯一。"""
        session = self._get_session()
        try:
            # 优先复用同文件的最新 canonical 行，并将软删除记录恢复为可见；只有首次入库才新增记录。
            existing_doc = session.query(KBDocSQLModel).filter(
                KBDocSQLModel.kb_id == kb_doc.kb_id,
                KBDocSQLModel.file_id == kb_doc.file_id,
                KBDocSQLModel.chunk_type == CANONICAL_FULL_TEXT_CHUNK_TYPE,
            ).order_by(KBDocSQLModel.id.desc()).first()

            if existing_doc:
                existing_doc.doc_id = kb_doc.doc_id or existing_doc.doc_id
                existing_doc.text = kb_doc.text or ""
                existing_doc.title = kb_doc.title or ""
                existing_doc.file_url = kb_doc.file_url or ""
                existing_doc.parent_id = kb_doc.parent_id or ""
                existing_doc.deleted = 0
                existing_doc.modify_time = datetime.now()
                existing_doc.modifier = kb_doc.modifier
            else:
                session.add(KBDocSQLModel.from_pydantic(kb_doc))

            session.commit()
            return True
        except Exception as e:
            session.rollback()
            raise e
        finally:
            session.close()

    def get_canonical_doc(self, kb_id: str, file_id: str) -> Optional[KBDocPydanticModel]:
        session = self._get_session()
        try:
            doc = session.query(KBDocSQLModel).filter(
                KBDocSQLModel.kb_id == kb_id,
                KBDocSQLModel.file_id == file_id,
                KBDocSQLModel.chunk_type == CANONICAL_FULL_TEXT_CHUNK_TYPE,
                KBDocSQLModel.deleted == 0
            ).order_by(KBDocSQLModel.modify_time.desc(), KBDocSQLModel.id.desc()).first()
            return doc.to_pydantic() if doc else None
        except Exception as e:
            raise e
        finally:
            session.close()

    def delete_by_file_ids(self, kb_id: str, file_ids: List[str]) -> int:
        session = self._get_session()
        try:
            # 元数据删除采用软删除，保留历史行供排查；向量索引的物理清理由上层另行协调。
            docs = session.query(KBDocSQLModel).filter(
                KBDocSQLModel.kb_id == kb_id,
                KBDocSQLModel.file_id.in_(file_ids),
                KBDocSQLModel.deleted == 0,
            ).all()

            for doc in docs:
                doc.deleted = 1
                doc.modify_time = datetime.now()

            session.commit()
            return len(docs)
        except Exception as e:
            session.rollback()
            raise e
        finally:
            session.close()

    def delete_by_kb_id(self, kb_id: str) -> int:
        session = self._get_session()
        try:
            docs = session.query(KBDocSQLModel).filter(
                KBDocSQLModel.kb_id == kb_id,
                KBDocSQLModel.deleted == 0,
            ).all()

            for doc in docs:
                doc.deleted = 1
                doc.modify_time = datetime.now()

            session.commit()
            return len(docs)
        except Exception as e:
            session.rollback()
            raise e
        finally:
            session.close()
