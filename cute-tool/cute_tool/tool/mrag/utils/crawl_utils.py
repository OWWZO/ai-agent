# -*- coding: utf-8 -*-
"""网页抓取：crawl4ai 转 Markdown，供 URL 入库。"""
from crawl4ai import AsyncWebCrawler

from .logger_utils import logger


async def async_crawl(url: str) -> str:
    """异步抓取，失败返回空串。"""
    try:
        async with AsyncWebCrawler() as crawler:
            result = await crawler.arun(url=url)

        return result.markdown
    except Exception as e:
        logger.error(f"Failed to crawl {url}: {e}")
        return ""


def crawl(url: str) -> str:
    """同步包装 async_crawl。"""
    import asyncio
    return asyncio.run(async_crawl(url))
