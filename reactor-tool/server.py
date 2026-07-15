# -*- coding: utf-8 -*-
# =====================
#
#
# Author: liumin.423
# Date:   2025/7/7
# =====================
import os
import warnings
from optparse import OptionParser
from pathlib import Path

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI
from loguru import logger
from starlette.middleware.cors import CORSMiddleware

from reactor_tool.util.middleware_util import UnknownException, HTTPProcessTimeMiddleware

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
    log_path = os.getenv("LOG_PATH", Path(__file__).resolve().parent / "logs" / "server.log")
    log_format = "{time:YYYY-MM-DD HH:mm:ss.SSS} {level} {module}.{function} {message}"
    logger.add(log_path, format=log_format, rotation="200 MB")


def create_app() -> FastAPI:
    _app = FastAPI(
        on_startup=[log_setting]
    )

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
    from reactor_tool.api import api_router
    app.include_router(api_router)

if __name__ == "__main__":
    parser = OptionParser()
    parser.add_option("--host", dest="host", type="string", default="0.0.0.0")
    parser.add_option("--port", dest="port", type="int", default=1601)
    parser.add_option("--workers", dest="workers", type="int", default=5)
    (options, args) = parser.parse_args()

    print(f"Start params: {options}")
    # Logo 仅在主启动入口打印一次，避免多 worker 模式下每个子进程重复输出。
    print_logo()

    reload_enabled = os.getenv("ENV", "local") == "local"
    app_factory_path = "server:create_app"

    # 单进程直接构造 app；多 worker/reload 使用 factory，让子进程内再创建应用，避免启动期导入过重。
    if not reload_enabled and int(options.workers) <= 1:
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
            workers=options.workers,
            reload=reload_enabled,
            timeout_keep_alive=99999,
            ws_ping_interval=99999,
            ws_ping_timeout=99999,
        )
