# -*- coding: utf-8 -*-
"""文档解析/入库后台任务状态。"""
import enum


class TaskStatusEnum(enum.Enum):
    """任务生命周期：排队 → 执行 → 成功/失败。"""
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"