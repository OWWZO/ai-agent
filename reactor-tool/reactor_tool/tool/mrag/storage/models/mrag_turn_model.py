from datetime import datetime
from typing import Any, Optional

from pydantic import BaseModel, Field


class MRagTurnModel(BaseModel):
    turn_id: str
    session_id: str
    question: str
    answer_markdown: str = ""
    status: str = "RUNNING"
    error_message: str = ""
    request_kb_scope: list[str] = Field(default_factory=list)
    request_image_urls: list[str] = Field(default_factory=list)
    answer_image_urls: list[str] = Field(default_factory=list)
    raw_chunks: list[Any] = Field(default_factory=list)
    deleted: int = 0
    create_time: Optional[datetime] = None
    modify_time: Optional[datetime] = None

