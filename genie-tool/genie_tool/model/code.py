from dataclasses import dataclass
from typing import Any

@dataclass
class CodeOuput:
    code: Any
    file_name: str
    file_list: list = None

@dataclass
class ActionOutput:
    content: str
    file_list: list