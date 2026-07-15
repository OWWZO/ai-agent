import uuid
from datetime import datetime

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from ...storage.models.mrag_session_model import MRagSessionModel
from ...storage.store_factory import get_mrag_session_store, get_mrag_turn_store

router = APIRouter(prefix="/mrag/sessions", tags=["MRAG 对话历史"])


def _session_to_payload(session: MRagSessionModel) -> dict:
    return {
        "session_id": session.session_id,
        "title": session.title,
        "kb_scope": session.kb_scope,
        "cover_kb_id": session.cover_kb_id or "",
        "latest_question": session.latest_question or "",
        "latest_answer_preview": session.latest_answer_preview or "",
        "turn_count": session.turn_count,
        "status": session.status,
        "created_at": session.create_time.isoformat() if session.create_time else "",
        "updated_at": session.modify_time.isoformat() if session.modify_time else "",
    }


def _turn_to_payload(turn) -> dict:
    return {
        "turn_id": turn.turn_id,
        "session_id": turn.session_id,
        "question": turn.question,
        "answer_markdown": turn.answer_markdown,
        "status": turn.status,
        "error_message": turn.error_message,
        "request_kb_scope": turn.request_kb_scope,
        "request_image_urls": turn.request_image_urls,
        "answer_image_urls": turn.answer_image_urls,
        "raw_chunks": turn.raw_chunks,
        "created_at": turn.create_time.isoformat() if turn.create_time else "",
        "updated_at": turn.modify_time.isoformat() if turn.modify_time else "",
    }


class CreateSessionRequest(BaseModel):
    kb_id: str = Field(default="", description="主知识库 ID")
    kb_ids: list[str] = Field(default_factory=list, description="知识库范围")
    title: str = Field(default="新对话", description="会话标题")

    def resolve_scope(self) -> list[str]:
        if self.kb_ids:
            return [item.strip() for item in self.kb_ids if item.strip()]
        if self.kb_id.strip():
            return [self.kb_id.strip()]
        return []


class SessionDetailRequest(BaseModel):
    session_id: str = Field(..., min_length=1)


class DeleteSessionRequest(BaseModel):
    session_id: str = Field(..., min_length=1)


class ListSessionRequest(BaseModel):
    page_no: int = Field(default=1, ge=1)
    page_size: int = Field(default=20, ge=1, le=100)


@router.post("/create")
def create_session(request: CreateSessionRequest):
    session_id = f"mrag_session_{uuid.uuid4().hex}"
    scope = request.resolve_scope()
    now = datetime.now()
    session = MRagSessionModel(
        session_id=session_id,
        title=request.title.strip() or "新对话",
        kb_scope=scope,
        cover_kb_id=scope[0] if scope else None,
        status="IDLE",
        create_time=now,
        modify_time=now,
    )
    get_mrag_session_store().create_session(session)
    return {"code": 200, "data": _session_to_payload(session)}


@router.post("/list")
def list_sessions(request: ListSessionRequest):
    sessions = get_mrag_session_store().list_sessions(request.page_no, request.page_size)
    return {
        "code": 200,
        "data": {
            "list": [_session_to_payload(session) for session in sessions],
        },
    }


@router.post("/detail")
def session_detail(request: SessionDetailRequest):
    session = get_mrag_session_store().get_session(request.session_id)
    if not session:
        raise HTTPException(status_code=404, detail="MRAG 会话不存在")

    turns = get_mrag_turn_store().list_turns(request.session_id)
    return {
        "code": 200,
        "data": {
            "session": _session_to_payload(session),
            "turns": [_turn_to_payload(turn) for turn in turns],
        },
    }


@router.post("/delete")
def delete_session(request: DeleteSessionRequest):
    deleted = get_mrag_session_store().delete_session(request.session_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="MRAG 会话不存在")
    deleted_turn_count = get_mrag_turn_store().delete_by_session_id(request.session_id)
    return {
        "code": 200,
        "data": {
            "session_id": request.session_id,
            "deleted_turn_count": deleted_turn_count,
        },
    }
