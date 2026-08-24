# -*- coding: utf-8 -*-
# =====================
#
# Author: wanghanmin1
# Date:   2025/7/8
# =====================
"""深度搜索（DeepSearch）主流程。

链路：查询拆解升格为章节 → 分章并发检索 → 分章总结 → 轻量反思补搜 → 合并终稿。
依赖 search_component 子模块与 MixSearch 混合检索引擎。
"""

import asyncio
import json
import os
import time
from dataclasses import dataclass, field
from functools import partial
from typing import List, AsyncGenerator, Tuple

from reactor_tool.util.log_util import logger
from reactor_tool.model.document import Doc
from reactor_tool.util.log_util import timer
from reactor_tool.tool.search_component.query_process import (
    plan_chapter_search,
    query_decompose,
)
from reactor_tool.tool.search_component.chapter import (
    docs_to_html,
    summarize_chapter_stream,
    update_chapter_summary,
    merge_chapters,
)
from reactor_tool.tool.search_component.reasoning import search_reasoning
from reactor_tool.tool.search_component.search_engine import MixSearch
from reactor_tool.model.protocal import StreamMode


@dataclass
class ChapterState:
    """单章研究状态。"""

    chapter_id: str
    title: str
    content: str
    order: int
    queries: List[str] = field(default_factory=list)
    docs: List[Doc] = field(default_factory=list)
    summary: str = ""
    status: str = "pending"
    error: str = ""


class DeepSearch:
    """深度搜索工具：章节升格 + 并发分章研究 + 轻量反思 + 合并回答。"""

    def __init__(self, engines: List[str] = []):
        """初始化搜索引擎开关；未传 engines 时读 USE_SEARCH_ENGINE 环境变量。"""
        normalized_engines = [
            engine.strip().lower() for engine in engines if engine and engine.strip()
        ]
        if not normalized_engines:
            env_value = os.getenv("USE_SEARCH_ENGINE", "ddg")
            normalized_engines = [
                engine.strip().lower()
                for engine in env_value.split(",")
                if engine.strip()
            ]
        if not normalized_engines:
            normalized_engines = ["ddg"]

        self.engines = normalized_engines
        use_ddg = "ddg" in normalized_engines
        use_bing = "bing" in normalized_engines
        use_jina = "jina" in normalized_engines
        use_sogou = "sogou" in normalized_engines
        use_serp = "serp" in normalized_engines
        use_exa = "exa" in normalized_engines
        self._search_single_query = partial(
            MixSearch().search_and_dedup,
            use_ddg=use_ddg,
            use_bing=use_bing,
            use_jina=use_jina,
            use_sogou=use_sogou,
            use_serp=use_serp,
            use_exa=use_exa,
            use_jina_reader=False,
        )
        self.searched_queries: List[str] = []
        self.current_docs: List[Doc] = []

    @timer("deepsearch", level="info")
    async def run(
        self,
        query: str,
        request_id: str = None,
        max_loop: int = 1,
        stream: bool = False,
        stream_mode: StreamMode = StreamMode(),
        *args,
        **kwargs,
    ) -> AsyncGenerator[str, None]:
        """深度搜索主循环（流式 yield SSE 数据片段）。"""

        total_timeout_seconds = int(
            os.getenv("DEEPSEARCH_TOTAL_TIMEOUT_SECONDS", "1200")
        )
        deadline = time.monotonic() + total_timeout_seconds

        def _remaining_timeout() -> float:
            return max(0.1, deadline - time.monotonic())

        # 轻量反思默认关闭；需要时设 DEEPSEARCH_LIGHT_REFLECTION=1
        _ = max_loop
        enable_light_reflection = str(
            os.getenv("DEEPSEARCH_LIGHT_REFLECTION", "0")
        ).lower() in ("1", "true", "yes")

        try:
            sub_queries = await asyncio.wait_for(
                query_decompose(query=query),
                timeout=_remaining_timeout(),
            )
            if not sub_queries:
                sub_queries = [{"title": query, "content": query}]

            chapters = [
                ChapterState(
                    chapter_id=f"C{idx}",
                    title=str(item.get("title") or "").strip(),
                    content=str(item.get("content") or item.get("title") or "").strip(),
                    order=idx,
                )
                for idx, item in enumerate(sub_queries, start=1)
                if str(item.get("title") or "").strip()
            ]

            yield json.dumps(
                {
                    "requestId": request_id,
                    "query": query,
                    "searchResult": {
                        "query": [c.title for c in chapters],
                        "chapters": [
                            {
                                "chapterId": c.chapter_id,
                                "chapterTitle": c.title,
                                "chapterContent": c.content,
                                "chapterOrder": c.order,
                            }
                            for c in chapters
                        ],
                        "docs": [[] for _ in chapters],
                    },
                    "isFinal": False,
                    "messageType": "extend",
                },
                ensure_ascii=False,
            )

            await asyncio.sleep(0.1)

            truncate_len = int(os.getenv("SINGLE_PAGE_MAX_SIZE", 200))
            flush_every = max(1, int(getattr(stream_mode, "token", None) or 5))
            async for event in self._iter_research_events(
                query=query,
                chapters=chapters,
                request_id=request_id,
                enable_light_reflection=enable_light_reflection,
                remaining_timeout=_remaining_timeout,
                truncate_len=truncate_len,
                flush_every=flush_every,
            ):
                yield event

            for chapter in chapters:
                self.current_docs.extend(chapter.docs)
                self.searched_queries.extend(chapter.queries)

            chapter_sections = [
                {
                    "id": c.chapter_id,
                    "title": c.title,
                    "content": c.content,
                    "order": c.order,
                    "summary": c.summary or f"（章节「{c.title}」暂无可用总结）",
                }
                for c in chapters
            ]

            answer = ""
            acc_content = ""
            acc_token = 0
            answer_stream = merge_chapters(
                query=query, chapter_sections=chapter_sections
            )
            while True:
                try:
                    chunk = await asyncio.wait_for(
                        answer_stream.__anext__(),
                        timeout=_remaining_timeout(),
                    )
                except StopAsyncIteration:
                    break

                if stream:
                    if acc_token >= stream_mode.token:
                        yield json.dumps(
                            {
                                "requestId": request_id,
                                "query": query,
                                "searchResult": {"query": [], "docs": []},
                                "answer": acc_content,
                                "isFinal": False,
                                "messageType": "report",
                            },
                            ensure_ascii=False,
                        )
                        acc_content = ""
                        acc_token = 0
                    acc_content += chunk
                    acc_token += 1
                answer += chunk

            if stream and acc_content:
                yield json.dumps(
                    {
                        "requestId": request_id,
                        "query": query,
                        "searchResult": {"query": [], "docs": []},
                        "answer": acc_content,
                        "isFinal": False,
                        "messageType": "report",
                    },
                    ensure_ascii=False,
                )

            yield json.dumps(
                {
                    "requestId": request_id,
                    "query": query,
                    "searchResult": {"query": [], "docs": []},
                    "answer": "" if stream else answer,
                    "isFinal": True,
                    "messageType": "report",
                },
                ensure_ascii=False,
            )

        except asyncio.TimeoutError:
            logger.warning(
                f"{request_id} deepsearch total timeout after {total_timeout_seconds}s"
            )
            fallback_answer = (
                "深度搜索超时，已返回当前可用结果，请基于已有搜索内容继续处理。"
            )
            yield json.dumps(
                {
                    "requestId": request_id,
                    "query": query,
                    "searchResult": {"query": [], "docs": []},
                    "answer": fallback_answer,
                    "isFinal": True,
                    "messageType": "report",
                },
                ensure_ascii=False,
            )

    def _dumps_search_event(
        self,
        query: str,
        request_id: str,
        chapters: List[ChapterState],
        truncate_len: int,
    ) -> str:
        """按章节顺序输出累计检索结果，前端按 query 原位更新卡片。"""
        queries = []
        docs = []
        for chapter in chapters:
            chapter_queries = chapter.queries or [chapter.content or chapter.title]
            for index, chapter_query in enumerate(chapter_queries):
                queries.append(chapter_query)
                docs.append(
                    [doc.to_dict(truncate_len=truncate_len) for doc in chapter.docs]
                    if index == 0
                    else []
                )
        return json.dumps(
            {
                "requestId": request_id,
                "query": query,
                "searchResult": {
                    "query": queries,
                    "docs": docs,
                },
                "isFinal": False,
                "messageType": "search",
            },
            ensure_ascii=False,
        )

    def _dumps_chapter_summary_event(
        self,
        query: str,
        request_id: str,
        chapter: ChapterState,
        summary: str,
        truncate_len: int,
        streaming: bool,
    ) -> str:
        chapter_queries = chapter.queries or [chapter.content or chapter.title]
        chapter_docs_groups = [
            [d.to_dict(truncate_len=truncate_len) for d in chapter.docs]
        ]
        while len(chapter_docs_groups) < len(chapter_queries):
            chapter_docs_groups.append([])
        return json.dumps(
            {
                "requestId": request_id,
                "query": query,
                "chapterId": chapter.chapter_id,
                "chapterTitle": chapter.title,
                "chapterContent": chapter.content,
                "chapterOrder": chapter.order,
                "chapterSummary": summary,
                "chapterStreaming": streaming,
                "answer": summary,
                "searchResult": {
                    "query": chapter_queries,
                    "docs": chapter_docs_groups,
                },
                "isFinal": False,
                "messageType": "chapter_summary",
            },
            ensure_ascii=False,
        )

    async def _iter_research_events(
        self,
        query: str,
        chapters: List[ChapterState],
        request_id: str,
        enable_light_reflection: bool,
        remaining_timeout,
        truncate_len: int,
        flush_every: int,
    ) -> AsyncGenerator[str, None]:
        """并发研究各章，检索完成和总结增量立刻推给前端。"""
        max_concurrent = max(
            1, int(os.getenv("CHAPTER_THREAD_NUM", os.getenv("SEARCH_THREAD_NUM", 5)))
        )
        semaphore = asyncio.Semaphore(max_concurrent)
        event_queue: asyncio.Queue = asyncio.Queue()
        chapter_by_id = {chapter.chapter_id: chapter for chapter in chapters}

        async def _one(chapter: ChapterState) -> None:
            async with semaphore:
                try:
                    await self._research_one_chapter(
                        query=query,
                        chapter=chapter,
                        request_id=request_id,
                        enable_light_reflection=enable_light_reflection,
                        remaining_timeout=remaining_timeout,
                        event_queue=event_queue,
                        flush_every=flush_every,
                    )
                finally:
                    await event_queue.put({"kind": "done"})

        workers = [asyncio.create_task(_one(chapter)) for chapter in chapters]
        finished = 0
        while finished < len(chapters):
            item = await event_queue.get()
            kind = item.get("kind")
            if kind == "done":
                finished += 1
                continue
            if kind == "search":
                yield self._dumps_search_event(
                    query, request_id, chapters, truncate_len
                )
                continue
            if kind == "chapter_summary":
                chapter = chapter_by_id.get(item.get("chapter_id"))
                if chapter is None:
                    continue
                yield self._dumps_chapter_summary_event(
                    query,
                    request_id,
                    chapter,
                    item.get("summary") or "",
                    truncate_len,
                    bool(item.get("streaming")),
                )

        await asyncio.gather(*workers, return_exceptions=True)

    async def _research_one_chapter(
        self,
        query: str,
        chapter: ChapterState,
        request_id: str,
        enable_light_reflection: bool,
        remaining_timeout,
        event_queue: asyncio.Queue,
        flush_every: int,
    ) -> ChapterState:
        """单章：初搜 → 流式总结 →（可选）轻量反思补搜 → 更新总结。"""
        answer_model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")

        async def _emit_search() -> None:
            await event_queue.put({"kind": "search"})

        async def _emit_summary(summary: str, streaming: bool) -> None:
            await event_queue.put(
                {
                    "kind": "chapter_summary",
                    "chapter_id": chapter.chapter_id,
                    "summary": summary,
                    "streaming": streaming,
                }
            )

        try:
            search_plan = await asyncio.wait_for(
                plan_chapter_search(
                    query=query,
                    chapter_title=chapter.title,
                    chapter_content=chapter.content,
                ),
                timeout=remaining_timeout(),
            )
            initial_queries = search_plan.get("search_queries") or [
                chapter.content or query
            ]
            chapter.queries.extend(
                item for item in initial_queries if item and item not in chapter.queries
            )
            docs, _ = await asyncio.wait_for(
                self._search_queries_and_dedup(
                    queries=chapter.queries,
                    request_id=request_id,
                ),
                timeout=remaining_timeout(),
            )
            chapter.docs = docs
            chapter.status = "searched"
            await _emit_search()

            acc = ""
            pending = 0
            stream = summarize_chapter_stream(
                query=query,
                chapter_title=chapter.title,
                chapter_content=chapter.content,
                chapter_order=chapter.order,
                search_content=docs_to_html(docs, model=answer_model),
            )
            while True:
                try:
                    chunk = await asyncio.wait_for(
                        stream.__anext__(),
                        timeout=remaining_timeout(),
                    )
                except StopAsyncIteration:
                    break
                if not chunk:
                    continue
                acc += chunk
                pending += 1
                chapter.summary = acc
                if pending >= flush_every:
                    await _emit_summary(acc, True)
                    pending = 0
            chapter.summary = acc
            chapter.status = "summarized"
            await _emit_summary(acc, False)

            if enable_light_reflection and chapter.summary:
                reasoning_result = await asyncio.wait_for(
                    search_reasoning(
                        request_id=request_id,
                        query=f"{query} / 章节：{chapter.title}",
                        content=chapter.summary,
                        history_query_list=list(chapter.queries),
                    ),
                    timeout=remaining_timeout(),
                )
                if reasoning_result.get("is_verify", "1") not in ["1", 1]:
                    rewrite_query = (
                        reasoning_result.get("rewrite_query") or ""
                    ).strip()
                    if rewrite_query and rewrite_query not in chapter.queries:
                        more_docs, _ = await asyncio.wait_for(
                            self._search_queries_and_dedup(
                                queries=[rewrite_query],
                                request_id=request_id,
                            ),
                            timeout=remaining_timeout(),
                        )
                        chapter.queries.append(rewrite_query)
                        seen = {d.content for d in chapter.docs if d.content}
                        for doc in more_docs:
                            if doc.content and doc.content not in seen:
                                chapter.docs.append(doc)
                                seen.add(doc.content)
                        await _emit_search()
                        chapter.summary = await asyncio.wait_for(
                            update_chapter_summary(
                                query=query,
                                chapter_title=chapter.title,
                                chapter_content=chapter.content,
                                chapter_order=chapter.order,
                                previous_summary=chapter.summary,
                                search_content=docs_to_html(
                                    more_docs, model=answer_model
                                ),
                            ),
                            timeout=remaining_timeout(),
                        )
                        chapter.status = "reflected"
                        await _emit_summary(chapter.summary, False)
                    else:
                        logger.info(
                            f"{request_id} chapter={chapter.chapter_id} 反思认为不足但无有效 rewrite_query"
                        )

            chapter.status = "completed"
            return chapter
        except Exception as exc:
            logger.exception(
                f"{request_id} chapter={chapter.chapter_id} research failed: {exc}"
            )
            chapter.status = "failed"
            chapter.error = str(exc)
            if not chapter.summary:
                chapter.summary = f"章节「{chapter.title}」研究失败：{exc}"
            await _emit_summary(chapter.summary, False)
            return chapter

    async def _search_queries_and_dedup(
        self,
        queries: List[str],
        request_id: str,
    ) -> Tuple[List[Doc], List[List[Doc]]]:
        """异步并行搜索多个查询并去重，避免阻塞当前 Uvicorn 事件循环。"""
        max_concurrent = max(1, int(os.getenv("SEARCH_THREAD_NUM", 5)))
        semaphore = asyncio.Semaphore(max_concurrent)

        async def _search_one(q: str) -> List[Doc]:
            async with semaphore:
                return await self._search_single_query(q, request_id)

        results = await asyncio.gather(*(_search_one(q) for q in queries))
        all_docs = [doc for docs in results for doc in docs]
        seen_content = set()
        deduped_docs = []
        for doc in all_docs:
            if doc.content and doc.content not in seen_content:
                deduped_docs.append(doc)
                seen_content.add(doc.content)
        return deduped_docs, results
