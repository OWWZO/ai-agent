# -*- coding: utf-8 -*-
"""有状态沙箱路由：bash + code_execution（应跑在 REACTOR_TOOL_ROLE=sandbox 单进程上）。"""
from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse
from loguru import logger

from reactor_tool.model.protocal import BashSandboxRequest, CodeExecutionRequest
from reactor_tool.util.middleware_util import RequestHandlerRoute

router = APIRouter(route_class=RequestHandlerRoute)


def _error_response(status_code: int, message: str) -> JSONResponse:
    return JSONResponse(status_code=status_code, content={"code": status_code, "message": message})


@router.post("/code_execution")
async def post_code_execution(body: CodeExecutionRequest):
    """Run caller-supplied Python and return stdout, errors, and fileInfo."""
    from reactor_tool.tool.direct_code_execution import execute_code

    return await execute_code(body)


@router.post("/bash")
async def post_bash(body: BashSandboxRequest):
    """会话沙箱 bash：runtime/skills 增量推送 → 执行 → skills 增量回写。"""
    from reactor_tool.tool.bash_sandbox import run_bash_sandbox

    try:
        result = await run_bash_sandbox(body)
        return result.model_dump(by_alias=True)
    except Exception as exc:
        logger.exception("[bash] request_id={} failed", body.request_id)
        return _error_response(500, f"bash sandbox failed: {exc}")
