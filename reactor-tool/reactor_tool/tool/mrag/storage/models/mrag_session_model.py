from datetime import datetime
from typing import Optional

from pydantic import BaseModel, Field


class MRagSessionModel(BaseModel):
    session_id: str
    title: str = Field(default="新对话")
    kb_scope: list[str] = Field(default_factory=list)
    cover_kb_id: Optional[str] = None
    latest_question: Optional[str] = None
    latest_answer_preview: Optional[str] = None
    turn_count: int = 0
    status: str = "IDLE"
    deleted: int = 0
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None

