# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""FastAPI 路由聚合：按 REACTOR_TOOL_ROLE 装配 api / sandbox / all。"""
from fastapi import APIRouter

from reactor_tool.service_role import get_service_role


def build_api_router() -> APIRouter:
    """按进程角色组装 /v1 路由。

    - all：单进程全量（默认，兼容旧启动方式）
    - sandbox：仅 bash + code_execution（应 workers=1）
    - api：其余工具 + 把 bash/code_execution 反代到 REACTOR_SANDBOX_URL
    """
    role = get_service_role()
    api_router = APIRouter(prefix="/v1")

    if role == "sandbox":
        from .sandbox_routes import router as sandbox_router

        api_router.include_router(sandbox_router, prefix="/tool", tags=["sandbox"])
        return api_router

    from .tool import router as tool_router
    from .file_manage import router as file_router
    from .sop import router as sop_router
    from reactor_tool.tool.mrag.api.routes.document import router as document_router
    from reactor_tool.tool.mrag.api.routes.history import router as mrag_history_router

    api_router.include_router(tool_router, prefix="/tool", tags=["tool"])
    api_router.include_router(file_router, prefix="/file_tool", tags=["file_manage"])
    api_router.include_router(sop_router, tags=["sop"])
    api_router.include_router(document_router, tags=["documents"])
    api_router.include_router(mrag_history_router, tags=["mrag_history"])

    if role == "api":
        from .sandbox_proxy import router as sandbox_proxy_router

        api_router.include_router(sandbox_proxy_router, prefix="/tool", tags=["sandbox-proxy"])
    else:
        from .sandbox_routes import router as sandbox_router

        api_router.include_router(sandbox_router, prefix="/tool", tags=["sandbox"])

    return api_router


# 兼容：from reactor_tool.api import api_router（create_app 启动时再 build，避免 import 期读错 env）
def __getattr__(name: str):
    if name == "api_router":
        return build_api_router()
    raise AttributeError(name)
