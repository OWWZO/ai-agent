from abc import ABC, abstractmethod

from .models.mrag_turn_model import MRagTurnModel


class MRagTurnStore(ABC):

    @abstractmethod
    def create_turn(self, turn: MRagTurnModel) -> bool:
        pass

    @abstractmethod
    def update_turn(self, turn: MRagTurnModel) -> bool:
        pass

    @abstractmethod
    def list_turns(self, session_id: str) -> list[MRagTurnModel]:
        pass

    @abstractmethod
    def delete_by_session_id(self, session_id: str) -> int:
        pass

