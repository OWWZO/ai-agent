# -*- coding: utf-8 -*-
"""MRAG 会话 SQLite 实现（表 t_mrag_session）。"""
from datetime import datetime
from typing import Optional

from sqlalchemy import Column, DateTime, Integer, JSON, String
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import Session, sessionmaker

from .models.mrag_session_model import MRagSessionModel
from .mrag_session_store import MRagSessionStore

Base = declarative_base()


class MRagSessionSQLModel(Base):
    """SQLAlchemy ORM：会话行。"""
    __tablename__ = "t_mrag_session"

    id = Column(Integer, primary_key=True, autoincrement=True)
    session_id = Column(String, nullable=False, unique=True)
    title = Column(String, nullable=False)
    kb_scope = Column(JSON, nullable=False)  # 多知识库 ID 列表
    cover_kb_id = Column(String, nullable=True)
    latest_question = Column(String, nullable=True)
    latest_answer_preview = Column(String, nullable=True)
    turn_count = Column(Integer, nullable=False, default=0)
    status = Column(String, nullable=False, default="IDLE")
    deleted = Column(Integer, nullable=False, default=0)
    create_time = Column(DateTime, nullable=False)
    modify_time = Column(DateTime, nullable=False)

    def to_pydantic(self) -> MRagSessionModel:
        """ORM → Pydantic。"""
        return MRagSessionModel(
            session_id=self.session_id,
            title=self.title,
            kb_scope=list(self.kb_scope or []),
            cover_kb_id=self.cover_kb_id,
            latest_question=self.latest_question,
            latest_answer_preview=self.latest_answer_preview,
            turn_count=self.turn_count,
            status=self.status,
            deleted=self.deleted,
            create_time=self.create_time,
            modify_time=self.modify_time,
        )


class MRagSessionSQLite(MRagSessionStore):
    """MRagSessionStore 的 SQLite 落地。"""

    def __init__(self, engine):
        self._engine = engine
        self._session_factory = sessionmaker(bind=engine)
        Base.metadata.create_all(self._engine)

    def _get_session(self) -> Session:
        return self._session_factory()

    def create_session(self, session: MRagSessionModel) -> bool:
        """创建会话；session_id 已存在且未删除则返回 False。"""
        db = self._get_session()
        try:
            existing = db.query(MRagSessionSQLModel).filter(
                MRagSessionSQLModel.session_id == session.session_id,
                MRagSessionSQLModel.deleted == 0,
            ).first()
            if existing:
                return False

            now = datetime.now()
            db.add(
                MRagSessionSQLModel(
                    session_id=session.session_id,
                    title=session.title,
                    kb_scope=session.kb_scope,
                    cover_kb_id=session.cover_kb_id,
                    latest_question=session.latest_question,
                    latest_answer_preview=session.latest_answer_preview,
                    turn_count=session.turn_count,
                    status=session.status,
                    deleted=session.deleted,
                    create_time=session.create_time or now,
                    modify_time=session.modify_time or now,
                )
            )
            db.commit()
            return True
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def update_session(self, session: MRagSessionModel) -> bool:
        db = self._get_session()
        try:
            existing = db.query(MRagSessionSQLModel).filter(
                MRagSessionSQLModel.session_id == session.session_id,
                MRagSessionSQLModel.deleted == 0,
            ).first()
            if not existing:
                return False

            existing.title = session.title
            existing.kb_scope = session.kb_scope
            existing.cover_kb_id = session.cover_kb_id
            existing.latest_question = session.latest_question
            existing.latest_answer_preview = session.latest_answer_preview
            existing.turn_count = session.turn_count
            existing.status = session.status
            existing.modify_time = session.modify_time or datetime.now()
            db.commit()
            return True
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def get_session(self, session_id: str) -> Optional[MRagSessionModel]:
        db = self._get_session()
        try:
            existing = db.query(MRagSessionSQLModel).filter(
                MRagSessionSQLModel.session_id == session_id,
                MRagSessionSQLModel.deleted == 0,
            ).first()
            return existing.to_pydantic() if existing else None
        finally:
            db.close()

    def list_sessions(self, page_no: int, page_size: int) -> list[MRagSessionModel]:
        db = self._get_session()
        try:
            offset = (page_no - 1) * page_size
            records = db.query(MRagSessionSQLModel).filter(
                MRagSessionSQLModel.deleted == 0
            ).order_by(
                MRagSessionSQLModel.modify_time.desc()
            ).offset(offset).limit(page_size).all()
            return [record.to_pydantic() for record in records]
        finally:
            db.close()

    def delete_session(self, session_id: str) -> bool:
        db = self._get_session()
        try:
            existing = db.query(MRagSessionSQLModel).filter(
                MRagSessionSQLModel.session_id == session_id,
                MRagSessionSQLModel.deleted == 0,
            ).first()
            if not existing:
                return False
            existing.deleted = 1
            existing.modify_time = datetime.now()
            db.commit()
            return True
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

