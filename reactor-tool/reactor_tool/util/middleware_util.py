# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
"""FastAPI 中间件与自定义路由。

- UnknownException: 未捕获异常转 500
- RequestHandlerRoute: POST JSON 请求体日志
- HTTPProcessTimeMiddleware: 生成 request_id 并写入耗时响应头
"""
import time
import traceback
import uuid
from typing import Callable

from fastapi.routing import APIRoute
from loguru import logger
from starlette.middleware.base import BaseHTTPMiddleware, RequestResponseEndpoint
from starlette.requests import Request
from starlette.responses import Response

from reactor_tool.model.context import RequestIdCtx
from reactor_tool.util.log_util import AsyncTimer


class UnknownException(BaseHTTPMiddleware):
    """兜底异常中间件：打印堆栈并返回 500 文本。"""

    async def dispatch(self, request: Request, call_next: RequestResponseEndpoint) -> Response:
        try:
            return await call_next(request)
        except Exception as e:
            # 异常统一在 HTTP 边界转成响应，避免内部堆栈直接泄露给调用方；request_id 保留排障关联线索。
            logger.error(f"{RequestIdCtx.request_id} {request.method} {request.url.path} error={traceback.format_exc()}")
            return Response(content=f"Unexpected error: {e}", status_code=500)


class RequestHandlerRoute(APIRoute):
    """自定义路由：非 multipart 的 POST 请求打印 body，便于联调排障。"""

    def get_route_handler(self) -> Callable:
        original_route_handler = super().get_route_handler()

        async def custom_route_handler(request: Request) -> Response:
            try:
                content_type = request.headers.get('content-type', '')
                # 跳过文件上传，避免把二进制写进日志
                if request.method == "POST" and not content_type.startswith('multipart/form-data'):
                    # Starlette 会缓存 request.body()，下游仍可正常读取同一请求体；这里只增加可观测性。
                    body = (await request.body()).decode("utf-8")
                    logger.info(f"{RequestIdCtx.request_id} {request.method} {request.url.path} body={body}")
            except Exception as e:
                logger.warning(f"{RequestIdCtx.request_id} {request.method} {request.url.path} failed. error={e}")

            return await original_route_handler(request)

        return custom_route_handler


class HTTPProcessTimeMiddleware(BaseHTTPMiddleware):
    """每个请求生成 UUID 作为 request_id，并在响应头返回 X-Process-Time(ms)。"""

    async def dispatch(self, request, call_next):
        # request_id 在进入业务链路前建立，使路由日志、异常日志和下游工具共享同一关联标识。
        RequestIdCtx.request_id = str(uuid.uuid4())
        async with AsyncTimer(key=f"{request.method} {request.url.path}") as t:
            response = await call_next(request)
            # 计时必须包住 call_next，响应头在拿到下游响应后写入，确保记录完整请求耗时。
            process_time = int((time.time() - t.start_time) * 1000)
            response.headers["X-Process-Time"] = str(process_time)
        return response
