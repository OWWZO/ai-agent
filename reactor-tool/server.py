# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import os
import sys
import warnings
from optparse import OptionParser
from pathlib import Path

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from loguru import logger
from starlette.middleware.cors import CORSMiddleware

from reactor_tool.util.middleware_util import (
    UnknownException,
    HTTPProcessTimeMiddleware,
)

load_dotenv()

# 压掉已知的第三方库噪音告警，避免排查真实异常时被无关 warning 干扰。
warnings.filterwarnings(
    "ignore",
    message="pkg_resources is deprecated as an API.*",
    category=UserWarning,
)
warnings.filterwarnings(
    "ignore",
    message=r"urllib3 \(.+\) or chardet \(.+\)/charset_normalizer \(.+\) doesn't match a supported version!",
    category=Warning,
)


def print_logo():
    from pyfiglet import Figlet

    f = Figlet(font="slant")
    print(f.renderText("Reactor Tool"))


def log_setting():
    log_path = os.getenv(
        "LOG_PATH", Path(__file__).resolve().parent / "logs" / "server.log"
    )
    log_format = "{time:YYYY-MM-DD HH:mm:ss.SSS} {level} {module}.{function} {message}"
    log_level = os.getenv("LOG_LEVEL", "INFO").upper()
    # 重载/多 worker 时先清理默认 sink，避免同一条日志重复写入控制台和文件。
    logger.remove()
    logger.add(sys.stderr, level=log_level)
    logger.add(log_path, format=log_format, level=log_level, rotation="200 MB")


def create_app() -> FastAPI:
    # create_app 是 Uvicorn factory 边界：每个 worker 子进程独立创建 middleware 和
    # router，避免父进程共享请求态或把启动期副作用复制到错误的进程生命周期。
    _app = FastAPI(on_startup=[log_setting])

    register_middleware(_app)
    register_router(_app)

    return _app


def register_middleware(app: FastAPI):
    app.add_middleware(UnknownException)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
        allow_credentials=True,
    )
    app.add_middleware(HTTPProcessTimeMiddleware)


def register_router(app: FastAPI):
    from reactor_tool.api import build_api_router

    app.include_router(build_api_router())


def resolve_worker_count(
    requested_workers: int,
    reload_enabled: bool = False,
    *,
    force_single_worker: bool = False,
) -> int:
    """解析 Uvicorn worker 数；reload / sandbox 角色要求单进程。"""
    if requested_workers < 1:
        raise ValueError("workers must be a positive integer")
    if force_single_worker and requested_workers > 1:
        print(f"sandbox role forces workers=1 (requested {requested_workers})")
        return 1
    if reload_enabled and requested_workers > 1:
        print(f"reload mode forces workers=1 (requested {requested_workers})")
        return 1
    return requested_workers


if __name__ == "__main__":
    parser = OptionParser()
    parser.add_option("--host", dest="host", type="string", default="0.0.0.0")
    parser.add_option("--port", dest="port", type="int", default=1601)
    parser.add_option("--workers", dest="workers", type="int", default=5)
    parser.add_option(
        "--role",
        dest="role",
        type="string",
        default=None,
        help="all|api|sandbox；也可用环境变量 REACTOR_TOOL_ROLE",
    )
    (options, args) = parser.parse_args()

    if options.role:
        os.environ["REACTOR_TOOL_ROLE"] = str(options.role).strip().lower()

    from reactor_tool.service_role import (
        get_service_role,
        sandbox_requires_single_worker,
    )

    role = get_service_role()
    print(f"Start params: {options} role={role}")
    # Logo 仅在主启动入口打印一次，避免多 worker 模式下每个子进程重复输出。
    print_logo()

    reload_enabled = os.getenv("ENV", "local") == "local"
    workers = resolve_worker_count(
        options.workers,
        reload_enabled=reload_enabled,
        force_single_worker=sandbox_requires_single_worker(role),
    )

    app_factory_path = "server:create_app"

    # reload 与多 worker 都要求 factory 模式，由 Uvicorn 在目标进程内创建 app；生产
    # 单 worker 可直接传实例。这个选择只影响进程装配，不改变路由层的并发语义。
    # 单进程直接构造 app；多 worker/reload 使用 factory，让子进程内再创建应用，避免启动期导入过重。
    if workers <= 1:
        if reload_enabled:
            uvicorn.run(
                app=app_factory_path,
                factory=True,
                host=options.host,
                port=options.port,
                reload=True,
                timeout_keep_alive=99999,
                ws_ping_interval=99999,
                ws_ping_timeout=99999,
            )
        else:
            uvicorn.run(
                app=create_app(),
                host=options.host,
                port=options.port,
                timeout_keep_alive=99999,
                ws_ping_interval=99999,
                ws_ping_timeout=99999,
            )
    else:
        uvicorn.run(
            app=app_factory_path,
            factory=True,
            host=options.host,
            port=options.port,
            workers=workers,
            timeout_keep_alive=99999,
            ws_ping_interval=99999,
            ws_ping_timeout=99999,
        )
