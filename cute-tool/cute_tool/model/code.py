# -*- coding: utf-8 -*-
"""代码解释器相关输出模型。

- CodeOuput: 单次代码执行产出（代码片段 + 产物文件）
- ActionOutput: 动作级输出（文本内容 + 文件列表）
"""
from dataclasses import dataclass
from typing import Any


@dataclass
class CodeOuput:
    """代码解释器一次执行的结构化输出。"""
    code: Any  # 生成或执行的代码内容
    file_name: str  # 主产物文件名
    file_list: list = None  # 关联产物文件列表
    thought: str = ""  # 本步思考（不含代码块）
    execution_logs: str = ""  # 沙箱 stdout/stderr 预览
    step: int = 0  # 第几步（1-based，0 表示未知）


@dataclass
class ActionOutput:
    """工具动作执行后的统一输出结构。"""
    content: str  # 文本结果 / observation
    file_list: list  # 本动作产生的文件列表
