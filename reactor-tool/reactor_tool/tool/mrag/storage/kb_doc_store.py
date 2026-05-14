from abc import abstractmethod, ABC
from typing import List, Optional

from .models.kb_doc_model import KBDocModel


class KBDocStore(ABC):

    @abstractmethod
    def add_doc(self, kb_doc: KBDocModel) -> bool:
        pass

    @abstractmethod
    def delete_doc(self, kb_doc: KBDocModel) -> bool:
        pass

    @abstractmethod
    def update_doc(self, kb_doc: KBDocModel) -> bool:
        pass

    @abstractmethod
    def get_docs(self, file_id: str, page_no: int, page_size: int) -> List[KBDocModel]:
        pass

    @abstractmethod
    def upsert_canonical_doc(self, kb_doc: KBDocModel) -> bool:
        pass

    @abstractmethod
    def get_canonical_doc(self, kb_id: str, file_id: str) -> Optional[KBDocModel]:
        pass

    @abstractmethod
    def delete_by_file_ids(self, kb_id: str, file_ids: List[str]) -> int:
        pass

    @abstractmethod
    def delete_by_kb_id(self, kb_id: str) -> int:
        pass
