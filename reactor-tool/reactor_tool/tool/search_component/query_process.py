# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/9
# =====================
"""DeepSearch 查询拆解与章节搜索词。"""

import os
import re
import time
import json

from loguru import logger

from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.llm_util import resolve_openai_compat_env
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.model.context import RequestIdCtx
from reactor_tool.util.log_util import timer


@timer()
async def query_decompose(query: str, **kwargs):
    """将复杂问题拆成章节，并为每个章节生成搜索关键词。"""
    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = (
        os.getenv("QUERY_DECOMPOSE_MODEL") or os.getenv("DEFAULT_MODEL") or "gpt-4.1"
    )
    current_date = time.strftime("%Y-%m-%d", time.localtime())
    prompt = get_prompt("deepsearch")["chapter_structure_prompt"].format(
        query=query,
        current_date=current_date,
        max_queries=os.getenv("QUERY_DECOMPOSE_MAX_SIZE", 5),
        max_search_queries=os.getenv("CHAPTER_SEARCH_QUERY_MAX_SIZE", 3),
    )
    messages = [
        {
            "role": "system",
            "content": prompt,
        },
        {"role": "user", "content": "请只输出符合要求的 JSON 数组。"},
    ]
    extend_queries = ""
    async for chunk in ask_llm(
        messages=messages,
        model=model,
        stream=True,
        only_content=True,  # 只返回内容
        api_base=llm_config["api_base"],
        api_key=llm_config["api_key"],
    ):
        if chunk:
            extend_queries += chunk

    chapters = parse_report_structure(extend_queries, fallback_query=query)
    logger.debug(
        f"{RequestIdCtx.request_id} query_decompose queries completed, "
        f"chars={len(extend_queries)} chapters={len(chapters)}"
    )
    return chapters


def normalize_search_queries(value) -> list[str]:
    """将模型返回的搜索词统一为去重后的字符串列表。"""
    values = value if isinstance(value, list) else [value]
    normalized = []
    for item in values:
        text = str(item or "").strip()
        if text and text not in normalized:
            normalized.append(text)
    return normalized


def parse_report_structure(content: str, fallback_query: str = "") -> list[dict]:
    """解析章节和每章搜索词，并兼容旧版 markdown 查询列表。"""
    text = (content or "").strip()
    if text:
        try:
            parsed = json.loads(text)
            if isinstance(parsed, dict):
                parsed = parsed.get("chapters") or parsed.get("items") or []
            if isinstance(parsed, list):
                chapters = []
                for item in parsed:
                    if isinstance(item, str) and item.strip():
                        value = item.strip()
                        chapters.append(
                            {
                                "title": value,
                                "content": value,
                                "search_queries": [value],
                            }
                        )
                    elif isinstance(item, dict):
                        title = str(item.get("title") or item.get("name") or "").strip()
                        content_text = str(
                            item.get("content") or item.get("description") or title
                        ).strip()
                        if title:
                            search_queries = normalize_search_queries(
                                item.get("search_queries") or item.get("queries")
                            )
                            if not search_queries and content_text:
                                search_queries = [content_text]
                            chapters.append(
                                {
                                    "title": title,
                                    "content": content_text or title,
                                    "search_queries": search_queries,
                                }
                            )
                if chapters:
                    return chapters
        except (TypeError, ValueError, json.JSONDecodeError):
            pass

    legacy_queries = re.findall(r"^- (.+)$", text, re.MULTILINE)
    if legacy_queries:
        return [
            {
                "title": item.strip(),
                "content": item.strip(),
                "search_queries": [item.strip()],
            }
            for item in legacy_queries
            if item.strip()
        ]

    fallback = (fallback_query or "").strip()
    return (
        [{"title": fallback, "content": fallback, "search_queries": [fallback]}]
        if fallback
        else []
    )


if __name__ == "__main__":
    pass
