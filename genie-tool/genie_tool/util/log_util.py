# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/8
# =====================
import asyncio
import functools
import inspect
import time
import traceback
from loguru import logger

from genie_tool.model.context import RequestIdCtx


class Timer(object):
    def __init__(self, key: str):
        self.key = key

    def __enter__(self):
        self.start_time = time.time()
        logger.info(f"{RequestIdCtx.request_id} {self.key} start...")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is not None:
            logger.error(f"{RequestIdCtx.request_id} {self.key} error={exc_tb}")
        else:
            logger.info(f"{RequestIdCtx.request_id} {self.key} cost=[{int((time.time() - self.start_time) * 1000)} ms]")


class AsyncTimer(object):
    def __init__(self, key: str):
        self.key = key

    async def __aenter__(self):
        self.start_time = time.time()
        logger.info(f"{RequestIdCtx.request_id} {self.key} start...")
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if exc_type is not None:
            logger.error(f"{RequestIdCtx.request_id} {self.key} error={traceback.format_exc()}")
        else:
            logger.info(f"{RequestIdCtx.request_id} {self.key} cost=[{int((time.time() - self.start_time) * 1000)} ms]")


def timer(key: str = ""):
    def decorator(func):
        if asyncio.iscoroutinefunction(func):
            @functools.wraps(func)
            async def wrapper(*args, **kwargs):
                async with AsyncTimer(f"{key} {func.__name__}"):
                    result = await func(*args, **kwargs)
                return result
            return wrapper
        elif inspect.isasyncgenfunction(func):
            @functools.wraps(func)
            async def wrapper(*args, **kwargs):
                async with AsyncTimer(f"{key} {func.__name__}"):
                    async for element in func(*args, **kwargs):
                        yield element
            return wrapper
        else:
            @functools.wraps(func)
            def wrapper(*args, **kwargs):
                with Timer(f"{key} {func.__name__}"):
                    result = func(*args, **kwargs)
                return result
            return wrapper
    return decorator


if __name__ == "__main__":
    pass
