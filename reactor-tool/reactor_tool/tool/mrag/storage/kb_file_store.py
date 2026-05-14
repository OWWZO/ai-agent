from abc import abstractmethod, ABC
from typing import List, Optional

from .models.kb_file_model import KBFileModel

class KBFileStore(ABC):

    @abstractmethod
    def add_file(self, kb_file: KBFileModel) -> bool:
        pass

    @abstractmethod
    def delete_file(self, kb_file: KBFileModel) -> bool:
        pass

    @abstractmethod
    def update_file(self, kb_file: KBFileModel) -> bool:
        pass

    @abstractmethod
    def get_files(self, kb_id: str, page_no: int, page_size: int) -> List[KBFileModel]:
        pass

    @abstractmethod
    def delete_by_file_ids(self, kb_id: str, file_ids: List[str]):
        pass

    @abstractmethod
    def list_kb_files(self, kb_id: str, page_no: int, page_size: int) -> List[KBFileModel]:
        pass

    @abstractmethod
    def count_kb_files(self, kb_id: str) -> int:
        pass

    @abstractmethod
    def get_file(self, kb_id: str, file_id: str) -> Optional[KBFileModel]:
        pass

    @abstractmethod
    def delete_by_kb_id(self, kb_id: str) -> int:
        pass



