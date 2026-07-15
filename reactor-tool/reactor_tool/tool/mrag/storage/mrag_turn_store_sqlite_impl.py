from datetime import datetime

from sqlalchemy import Column, DateTime, Integer, JSON, String
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import Session, sessionmaker

from .models.mrag_turn_model import MRagTurnModel
from .mrag_turn_store import MRagTurnStore

Base = declarative_base()


class MRagTurnSQLModel(Base):
    __tablename__ = "t_mrag_turn"

    id = Column(Integer, primary_key=True, autoincrement=True)
    turn_id = Column(String, nullable=False, unique=True)
    session_id = Column(String, nullable=False)
    question = Column(String, nullable=False)
    answer_markdown = Column(String, nullable=False, default="")
    status = Column(String, nullable=False, default="RUNNING")
    error_message = Column(String, nullable=False, default="")
    request_kb_scope = Column(JSON, nullable=False)
    request_image_urls = Column(JSON, nullable=False)
    answer_image_urls = Column(JSON, nullable=False)
    raw_chunks = Column(JSON, nullable=False)
    deleted = Column(Integer, nullable=False, default=0)
    create_time = Column(DateTime, nullable=False)
    modify_time = Column(DateTime, nullable=False)

    def to_pydantic(self) -> MRagTurnModel:
        return MRagTurnModel(
            turn_id=self.turn_id,
            session_id=self.session_id,
            question=self.question,
            answer_markdown=self.answer_markdown,
            status=self.status,
            error_message=self.error_message,
            request_kb_scope=list(self.request_kb_scope or []),
            request_image_urls=list(self.request_image_urls or []),
            answer_image_urls=list(self.answer_image_urls or []),
            raw_chunks=list(self.raw_chunks or []),
            deleted=self.deleted,
            create_time=self.create_time,
            modify_time=self.modify_time,
        )


class MRagTurnSQLite(MRagTurnStore):

    def __init__(self, engine):
        self._engine = engine
        self._session_factory = sessionmaker(bind=engine)
        Base.metadata.create_all(self._engine)

    def _get_session(self) -> Session:
        return self._session_factory()

    def create_turn(self, turn: MRagTurnModel) -> bool:
        db = self._get_session()
        try:
            now = datetime.now()
            db.add(
                MRagTurnSQLModel(
                    turn_id=turn.turn_id,
                    session_id=turn.session_id,
                    question=turn.question,
                    answer_markdown=turn.answer_markdown,
                    status=turn.status,
                    error_message=turn.error_message,
                    request_kb_scope=turn.request_kb_scope,
                    request_image_urls=turn.request_image_urls,
                    answer_image_urls=turn.answer_image_urls,
                    raw_chunks=turn.raw_chunks,
                    deleted=turn.deleted,
                    create_time=turn.create_time or now,
                    modify_time=turn.modify_time or now,
                )
            )
            db.commit()
            return True
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def update_turn(self, turn: MRagTurnModel) -> bool:
        db = self._get_session()
        try:
            existing = db.query(MRagTurnSQLModel).filter(
                MRagTurnSQLModel.turn_id == turn.turn_id,
                MRagTurnSQLModel.deleted == 0,
            ).first()
            if not existing:
                return False

            existing.answer_markdown = turn.answer_markdown
            existing.status = turn.status
            existing.error_message = turn.error_message
            existing.request_kb_scope = turn.request_kb_scope
            existing.request_image_urls = turn.request_image_urls
            existing.answer_image_urls = turn.answer_image_urls
            existing.raw_chunks = turn.raw_chunks
            existing.modify_time = turn.modify_time or datetime.now()
            db.commit()
            return True
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def list_turns(self, session_id: str) -> list[MRagTurnModel]:
        db = self._get_session()
        try:
            records = db.query(MRagTurnSQLModel).filter(
                MRagTurnSQLModel.session_id == session_id,
                MRagTurnSQLModel.deleted == 0,
            ).order_by(MRagTurnSQLModel.create_time.asc()).all()
            return [record.to_pydantic() for record in records]
        finally:
            db.close()

    def delete_by_session_id(self, session_id: str) -> int:
        db = self._get_session()
        try:
            records = db.query(MRagTurnSQLModel).filter(
                MRagTurnSQLModel.session_id == session_id,
                MRagTurnSQLModel.deleted == 0,
            ).all()
            for record in records:
                record.deleted = 1
                record.modify_time = datetime.now()
            db.commit()
            return len(records)
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()
