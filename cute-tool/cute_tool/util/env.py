# -*- coding: utf-8 -*-
"""环境变量读取 seam。

仓库里原本有 8 个私有 helper（``_env_bool`` ×3、``_trimmed_env`` ×2、
``_env_int``、``_env_str``、``_env_flag``、``_first_non_blank_env``），
真值集合和"空串"语义并不一致。这里收敛成一个入口，并**显式保留两种布尔语义**：

- ``env_bool`` 语义 A：空串视为未设置 → 返回 default（``plan_sop._env_flag``）。
- ``env_bool_strict`` 语义 B：空串视为已设置的假值（``qdrant_utils._env_bool``）。

调用方迁移时必须选对语义，否则会改变开关行为。
"""

import os
from typing import Optional

# 统一真值集合（合并了原 plan_sop 的 {"1","true","yes","y","on"} 与其它处
# 的 {"1","true","yes","on"}）：加 "y" 只可能把原本判假的输入变成真，因此
# 这里仍显式包含它，避免迁移时出现"某个 flag 突然关不掉"。
TRUTHY_VALUES = frozenset({"1", "true", "yes", "y", "on"})
FALSY_VALUES = frozenset({"0", "false", "no", "n", "off"})


def env_str(name: str, default: Optional[str] = None) -> Optional[str]:
    """读取单个环境变量：去首尾空白，空串视为未设置并返回 default。"""
    value = os.getenv(name)
    if value is None:
        return default
    normalized = value.strip()
    return normalized or default


def env_str_any(*names: str, default: Optional[str] = None) -> Optional[str]:
    """按顺序读取多个环境变量，返回第一个非空白值。"""
    for name in names:
        value = os.getenv(name)
        if value is None:
            continue
        trimmed = value.strip()
        if trimmed:
            return trimmed
    return default


def env_int(name: str, default: int) -> int:
    """读取整型环境变量；缺失或非法时返回 default，不让坏配置打断启动。"""
    raw = env_str(name)
    if raw is None:
        return default
    try:
        return int(raw)
    except (TypeError, ValueError):
        return default


def env_float(name: str, default: float) -> float:
    """读取浮点环境变量；缺失或非法时返回 default。"""
    raw = env_str(name)
    if raw is None:
        return default
    try:
        return float(raw)
    except (TypeError, ValueError):
        return default


def env_bool(name: str, default: bool = False) -> bool:
    """布尔环境变量（语义 A）：未设置或空串 → default。"""
    raw = env_str(name)
    if raw is None:
        return default
    return raw.lower() in TRUTHY_VALUES


def env_bool_strict(name: str, default: bool = False) -> bool:
    """布尔环境变量（语义 B）：空串已被设置 → 按假值计算。

    与 :func:`env_bool` 的差别只在空串：这里空串会短路到 ``False``
    （与 ``qdrant_utils._env_bool`` 行为一致），调用方依赖的是"配了就必须显式给真值"。
    """
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in TRUTHY_VALUES
