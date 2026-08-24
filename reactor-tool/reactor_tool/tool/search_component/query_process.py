# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/9
# =====================
"""DeepSearch 查询拆解与章节搜索计划。"""

import os
import re
import time
import json

from json_repair import repair_json
from loguru import logger

from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.llm_util import resolve_openai_compat_env
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.model.context import RequestIdCtx
from reactor_tool.util.log_util import timer


@timer()
async def query_decompose(query: str, **kwargs):
    """将复杂问题拆成多个可检索的子查询（流式 think + 结构化输出）。"""
    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = (
        os.getenv("QUERY_DECOMPOSE_MODEL") or os.getenv("DEFAULT_MODEL") or "gpt-4.1"
    )
    think_model = os.getenv("QUERY_DECOMPOSE_THINK_MODEL") or model
    current_date = time.strftime("%Y-%m-%d", time.localtime())
    decompose_prompt = get_prompt("deepsearch")
    # 第一步：think 模型做推理草稿
    think_content = ""
    async for chunk in ask_llm(
        messages=decompose_prompt["query_decompose_think_prompt"].format(
            task=query, retrieval_str=""
        ),
        model=think_model,
        stream=True,
        only_content=True,  # 只返回内容
        api_base=llm_config["api_base"],
        api_key=llm_config["api_key"],
    ):
        if chunk:
            think_content += chunk

    logger.debug(
        f"{RequestIdCtx.request_id} query_decompose think completed, chars={len(think_content)}"
    )

    # decompose
    messages = [
        {
            "role": "system",
            "content": decompose_prompt["chapter_structure_prompt"].format(
                current_date=current_date,
                max_queries=os.getenv("QUERY_DECOMPOSE_MAX_SIZE", 5),
                thinking_result=think_content,
            ),
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


def parse_report_structure(content: str, fallback_query: str = "") -> list[dict]:
    """解析结构化章节，并兼容旧版 markdown 查询列表。"""
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
                        chapters.append({"title": value, "content": value})
                    elif isinstance(item, dict):
                        title = str(item.get("title") or item.get("name") or "").strip()
                        content_text = str(
                            item.get("content") or item.get("description") or title
                        ).strip()
                        if title:
                            chapters.append(
                                {"title": title, "content": content_text or title}
                            )
                if chapters:
                    return chapters
        except (TypeError, ValueError, json.JSONDecodeError):
            pass

    legacy_queries = re.findall(r"^- (.+)$", text, re.MULTILINE)
    if legacy_queries:
        return [
            {"title": item.strip(), "content": item.strip()}
            for item in legacy_queries
            if item.strip()
        ]

    fallback = (fallback_query or "").strip()
    return [{"title": fallback, "content": fallback}] if fallback else []


async def plan_chapter_search(
    query: str, chapter_title: str, chapter_content: str
) -> dict:
    """根据章节研究任务生成可直接提交给搜索引擎的关键词。"""
    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = (
        os.getenv("CHAPTER_SEARCH_PLAN_MODEL")
        or os.getenv("QUERY_DECOMPOSE_MODEL")
        or os.getenv("DEFAULT_MODEL")
        or "gpt-4.1"
    )
    prompt = get_prompt("deepsearch")["chapter_search_plan_prompt"].format(
        query=query,
        chapter_title=chapter_title,
        chapter_content=chapter_content or chapter_title,
        current_date=time.strftime("%Y-%m-%d", time.localtime()),
        max_queries=os.getenv("CHAPTER_SEARCH_QUERY_MAX_SIZE", 3),
    )
    content = ""
    async for chunk in ask_llm(
        messages=prompt,
        model=model,
        stream=True,
        only_content=True,
        api_base=llm_config["api_base"],
        api_key=llm_config["api_key"],
    ):
        if chunk:
            content += chunk
    return parse_search_plan(content, fallback_query=chapter_content or chapter_title)


def parse_search_plan(content: str, fallback_query: str = "") -> dict:
    """解析搜索计划；解析失败时使用章节标题作为唯一关键词。"""
    fallback = (fallback_query or "").strip()
    try:
        raw = (content or "").strip()
        parsed = json.loads(raw or repair_json(raw))
        if isinstance(parsed, dict):
            queries = parsed.get("search_queries") or parsed.get("queries") or []
            if isinstance(queries, str):
                queries = [queries]
            normalized = []
            for item in queries:
                value = str(item).strip()
                if value and value not in normalized:
                    normalized.append(value)
            if normalized:
                return {
                    "search_queries": normalized,
                    "reasoning": str(parsed.get("reasoning") or "").strip(),
                }
    except (TypeError, ValueError, json.JSONDecodeError):
        pass
    return {"search_queries": [fallback] if fallback else [], "reasoning": ""}


if __name__ == "__main__":
    pass
