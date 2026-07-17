# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""FastAPI 路由聚合：/v1/tool、/v1/file_tool、文档与 MRAG 会话历史。"""
from fastapi import APIRouter

from .tool import router as tool_router
from .file_manage import router as file_router
from .sop import router as sop_router
from reactor_tool.tool.mrag.api.routes.document import router as document_router
from reactor_tool.tool.mrag.api.routes.history import router as mrag_history_router

# 对外统一前缀 /v1，由 server.py include_router(api_router)
api_router = APIRouter(prefix="/v1")

api_router.include_router(tool_router, prefix="/tool", tags=["tool"])
api_router.include_router(file_router, prefix="/file_tool", tags=["file_manage"])
api_router.include_router(sop_router, tags=["sop"])
api_router.include_router(document_router, tags=["documents"])
api_router.include_router(mrag_history_router, tags=["mrag_history"])
