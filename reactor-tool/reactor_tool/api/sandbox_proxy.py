# -*- coding: utf-8 -*-
"""API 角色：把 bash / code_execution / desktop_session 反代到 sandbox 单进程。"""

from __future__ import annotations

import httpx
from fastapi import APIRouter, Request, Response
from loguru import logger

from reactor_tool.service_role import get_sandbox_base_url
from reactor_tool.util.middleware_util import RequestHandlerRoute

router = APIRouter(route_class=RequestHandlerRoute)

_PROXY_PATHS = frozenset(
    {"bash", "code_execution", "desktop_session/open", "desktop_session/close"}
)
# 覆盖冷启动 + 长命令；与 Java BashTool readTimeout 同量级
_TIMEOUT = httpx.Timeout(connect=60.0, read=660.0, write=60.0, pool=60.0)
_DESKTOP_TIMEOUT = httpx.Timeout(connect=60.0, read=60.0, write=60.0, pool=60.0)


def proxy_path_name(path: str) -> str:
    marker = "/tool/"
    idx = path.find(marker)
    if idx < 0:
        return path.rstrip("/").split("/")[-1]
    return path[idx + len(marker) :].rstrip("/")


@router.post("/bash")
@router.post("/code_execution")
@router.post("/desktop_session/open")
@router.post("/desktop_session/close")
async def proxy_sandbox_tool(request: Request) -> Response:
    name = proxy_path_name(request.url.path)
    if name not in _PROXY_PATHS:
        return Response(status_code=404, content=b'{"message":"unknown sandbox route"}')

    base = get_sandbox_base_url()
    target = f"{base}/v1/tool/{name}"
    body = await request.body()
    headers = {
        "content-type": request.headers.get("content-type") or "application/json",
    }
    # 透传可选追踪头
    for key in ("x-request-id", "x-session-id"):
        if key in request.headers:
            headers[key] = request.headers[key]

    timeout = _DESKTOP_TIMEOUT if name.startswith("desktop_session/") else _TIMEOUT
    try:
        async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
            upstream = await client.post(target, content=body, headers=headers)
    except httpx.RequestError as exc:
        logger.exception("[sandbox-proxy] {} -> {} failed", name, target)
        return Response(
            status_code=502,
            media_type="application/json",
            content=(
                f'{{"code":502,"message":"sandbox proxy failed: {type(exc).__name__}: {exc}"}}'
            ).encode("utf-8"),
        )

    media = upstream.headers.get("content-type") or "application/json"
    return Response(
        content=upstream.content, status_code=upstream.status_code, media_type=media
    )
