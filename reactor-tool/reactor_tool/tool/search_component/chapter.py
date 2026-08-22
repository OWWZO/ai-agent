# -*- coding: utf-8 -*-
"""DeepSearch 章节级总结与合并。

将子问题升格为报告章节：分章总结、轻量反思后的更新总结、终稿合并。
"""

import os
import time
from typing import AsyncGenerator, List

from reactor_tool.model.document import Doc
from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.llm_util import resolve_openai_compat_env
from reactor_tool.util.log_util import timer
from reactor_tool.util.prompt_util import get_prompt


def docs_to_html(docs: List[Doc], model: str = None) -> str:
    """将文档列表格式化为带编号的 HTML，供章节总结引用。"""
    from reactor_tool.model.context import LLMModelInfoFactory
    from reactor_tool.util.file_util import truncate_files

    if not docs:
        return ""
    truncate_docs = docs
    if model:
        max_tokens = LLMModelInfoFactory.get_context_length(model)
        truncate_docs = truncate_files(docs, max_tokens=int(max_tokens * 0.8))
    parts = []
    for i, doc in enumerate(truncate_docs, start=1):
        parts.append(f"文档编号〔{i}〕. \n{doc.to_html()}\n")
    return "".join(parts)


async def summarize_chapter_stream(
    query: str,
    chapter_title: str,
    chapter_content: str,
    chapter_order: int,
    search_content: str,
) -> AsyncGenerator[str, None]:
    """流式产出单章总结 token，供前端边生成边展示。"""
    prompts = get_prompt("deepsearch")
    prompt_template = prompts["chapter_summary_prompt"]
    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")
    answer_length = os.getenv("SEARCH_CHAPTER_LENGTH", "1200")

    prompt = prompt_template.format(
        query=query,
        chapter_title=chapter_title,
        chapter_content=chapter_content or chapter_title,
        chapter_order=chapter_order,
        sub_qa=search_content,
        current_time=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        response_length=answer_length,
    )
    async for chunk in ask_llm(
        messages=prompt,
        model=model,
        stream=True,
        only_content=True,
        api_base=llm_config["api_base"],
        api_key=llm_config["api_key"],
    ):
        if chunk:
            yield chunk


@timer()
async def summarize_chapter(
    query: str,
    chapter_title: str,
    chapter_content: str,
    chapter_order: int,
    search_content: str,
) -> str:
    """基于检索材料生成单章总结（拼接流式输出）。"""
    content = ""
    async for chunk in summarize_chapter_stream(
        query=query,
        chapter_title=chapter_title,
        chapter_content=chapter_content,
        chapter_order=chapter_order,
        search_content=search_content,
    ):
        content += chunk
    return content.strip()


@timer()
async def update_chapter_summary(
    query: str,
    chapter_title: str,
    chapter_content: str,
    chapter_order: int,
    previous_summary: str,
    search_content: str,
) -> str:
    """用补搜材料更新章节总结，保留已有要点并补充缺失信息。"""
    prompts = get_prompt("deepsearch")
    prompt_template = prompts["chapter_reflection_summary_prompt"]
    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")
    answer_length = os.getenv("SEARCH_CHAPTER_LENGTH", "1200")

    prompt = prompt_template.format(
        query=query,
        chapter_title=chapter_title,
        chapter_content=chapter_content or chapter_title,
        chapter_order=chapter_order,
        previous_summary=previous_summary,
        sub_qa=search_content,
        current_time=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        response_length=answer_length,
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
    return content.strip() or previous_summary


@timer()
async def merge_chapters(
    query: str,
    chapter_sections: List[dict],
) -> AsyncGenerator[str, None]:
    """按章节顺序直接拼接原文，保留各章完整内容，不做 LLM 改写。"""
    ordered = sorted(
        chapter_sections or [],
        key=lambda item: (
            item.get("order") if isinstance(item.get("order"), int) else 10**9
        ),
    )
    parts = [f"# {query.strip() or '深度研究报告'}", ""]
    for item in ordered:
        title = str(item.get("title") or "未命名章节").strip()
        order = item.get("order")
        summary = str(item.get("summary") or "").strip()
        heading = f"## {order}. {title}" if order not in (None, "") else f"## {title}"
        parts.append(heading)
        parts.append("")
        parts.append(summary if summary else "（本章暂无可用内容）")
        parts.append("")

    report = "\n".join(parts).strip() + "\n"
    # 按块产出，兼容现有 stream report 事件节流。
    chunk_size = max(200, int(os.getenv("SEARCH_MERGE_CHUNK_SIZE", "800")))
    for start in range(0, len(report), chunk_size):
        yield report[start : start + chunk_size]
