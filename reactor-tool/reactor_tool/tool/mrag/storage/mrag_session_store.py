from abc import ABC, abstractmethod
from typing import Optional

from .models.mrag_session_model import MRagSessionModel


class MRagSessionStore(ABC):

    @abstractmethod
    def create_session(self, session: MRagSessionModel) -> bool:
        pass

    @abstractmethod
    def update_session(self, session: MRagSessionModel) -> bool:
        pass

    @abstractmethod
    def get_session(self, session_id: str) -> Optional[MRagSessionModel]:
        pass

    @abstractmethod
    def list_sessions(self, page_no: int, page_size: int) -> list[MRagSessionModel]:
        pass

    @abstractmethod
    def delete_session(self, session_id: str) -> bool:
        pass

