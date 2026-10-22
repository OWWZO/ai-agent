# -*- coding: utf-8 -*-
"""SSE 事件与响应构造 seam。

路由层原先在 4 个模块里手写约 30 处
``{"requestId": ..., "data": ..., "isFinal": ...}``，并把同一段心跳
``ping_message_factory=lambda: ServerSentEvent(data="heartbeat"), ping=15``
复制了 5 遍。这里把信封和响应外壳各收敛成一个函数。
"""

import json
from typing import Any

from sse_starlette import EventSourceResponse, ServerSentEvent

# Java 侧按固定字符串判活；改这里必须同时确认 Java 侧的 SSE 空闲判定。
HEARTBEAT_DATA = "heartbeat"
HEARTBEAT_PING_SECONDS = 15


def sse_event(
    request_id: str,
    data: Any = None,
    *,
    is_final: bool = False,
    **extra: Any,
) -> str:
    """序列化一条 SSE 事件体。

    ``data`` 为 str 时原样透传，其余按 JSON 序列化；``ensure_ascii=False``
    保持中文可读，与 Java 侧 Jackson 解码行为一致。
    """
    payload: dict[str, Any] = {"requestId": request_id, "isFinal": is_final}
    if data is not None:
        payload["data"] = data
    payload.update(extra)
    return json.dumps(payload, ensure_ascii=False)


def sent_event(
    request_id: str,
    data: Any = None,
    *,
    is_final: bool = False,
    **extra: Any,
) -> ServerSentEvent:
    """:func:`sse_event` 的 sse-starlette 包装，用于 ``yield``。"""
    return ServerSentEvent(
        data=sse_event(request_id, data, is_final=is_final, **extra)
    )


def sse_response(stream: Any) -> EventSourceResponse:
    """带统一心跳的 SSE 响应。

    ``ping=15`` 是既有的空闲判定间隔，Java BashTool / 长任务客户端都按它设置
    readTimeout；调大之前先确认调用方不会提前断开。
    """
    return EventSourceResponse(
        stream,
        ping_message_factory=lambda: ServerSentEvent(data=HEARTBEAT_DATA),
        ping=HEARTBEAT_PING_SECONDS,
    )
