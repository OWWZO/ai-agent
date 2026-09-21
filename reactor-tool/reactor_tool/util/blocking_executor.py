"""Shared bounded worker pool for synchronous tool integrations."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from functools import partial
from typing import Any, Callable


BLOCKING_EXECUTOR = ThreadPoolExecutor(
    max_workers=4,
    thread_name_prefix="reactor-tool-blocking",
)


async def run_blocking(func: Callable[..., Any], /, *args: Any, **kwargs: Any) -> Any:
    """Run a synchronous integration without consuming an event-loop thread."""
    import asyncio

    loop = asyncio.get_running_loop()
    return await loop.run_in_executor(BLOCKING_EXECUTOR, partial(func, *args, **kwargs))
