"""
Agentic RAG 模块（MRAG 查询主入口）。

智能化 RAG：查询规划 → 多轮检索 → 重排 → 生成，支持图文混合与会话。

主要能力：
1. 查询分解和规划
2. 多步推理与证据补全
3. 文本/图片检索与重排
4. 结果评估与 SSE 流式输出
"""
import concurrent.futures
import uuid
from typing import List, Dict, Tuple

from ..eval.canonical_keys import build_canonical_key, build_runtime_key
from ..eval.trace import (
    RetrievalTrace,
    RetrievalTraceHit,
    RetrievalTraceRound,
    RetrievalTraceStage,
)
from .query_processor import QueryProcessor
from ..generation import PromptManager
from ..generation.llm import LLMClient
from ..generation.vlm import VLLMClient
from ..rerank.text_reranker import get_text_reranker
from ..retrieval import BaseRetriever
from ..utils.logger_utils import logger
from ..utils.time_utils import time_it


def beautify_messages(messages: List):
    """日志友好：截断消息内容便于打印。"""
    output_content = ""
    for message in messages:
        content = message["content"]
        if isinstance(content, list):
            content = "\n".join([c["text"][:100] if c["type"] == "text" else "[图片]" for c in content])
        else:
            content = message["content"]
            if len(content) > 100:
                content = content[:100] + "..."
        output_content += f"[{message['role']}]: {content}\n"
    return output_content


def display_chunks(chunks: List[Dict]):
    """调试打印检索 chunk 摘要。"""
    for i, chunk in enumerate(chunks):
        print(f"=======================Chunk {i}: ")
        print(f"score: {chunk['score']}")
        print(f"chunk: {chunk['payload']['text'][:100]}")


class AgenticRAG:
    """智能 RAG 系统：按知识库范围多轮检索并生成回答。"""

    def __init__(self, kb_id: str | list[str], n_round: int = 5):
        self._n_round = n_round  # 最大检索-推理轮数
        self._retriever = BaseRetriever()
        self._kb_id = kb_id  # 单库 ID 或多库列表

    def retrieval(self, questions: list[str]) -> List[List[Dict]]:
        """对多个子问题批量文本检索。"""
        text_resp = self._retriever.retrieval_by_texts(self._kb_id, questions)
        return text_resp

    @staticmethod
    def _keep_best_chunk(chunk_map: Dict[str, Dict], key: str, chunk: Dict) -> None:
        """同一关联键命中多次时，只保留分数更高的候选。"""
        current = chunk_map.get(key)
        if current is None or chunk["score"] > current["score"]:
            chunk_map[key] = chunk

    @staticmethod
    def merge_retrieval_results(resp: List[Dict]) -> Tuple[List[Dict], List[Dict], List[Dict]]:
        # 根据类型， 去重
        # OCR/caption 既可能来自图片也可能来自页面，按稳定 asset id 归并后再分到三类展示结果。
        text_chunk_map = {}
        image_chunk_map = {}
        page_chunk_map = {}
        for ret in resp:
            chunk_type = ret['payload']['chunk_type']
            if chunk_type in {"text", "ocr_text", "caption"}:
                key = f"{chunk_type}:{ret['payload'].get('file_sorted') or ret['payload'].get('image_id') or ret['payload'].get('page_id')}"
                AgenticRAG._keep_best_chunk(text_chunk_map, key, ret)

            if chunk_type == "image":
                if "image_id" in ret['payload']:
                    key = ret['payload']['image_id']
                    AgenticRAG._keep_best_chunk(image_chunk_map, key, ret)
            elif chunk_type in {"ocr_text", "caption"}:
                if "image_id" in ret['payload']:
                    key = ret['payload']['image_id']
                    AgenticRAG._keep_best_chunk(image_chunk_map, key, ret)
                elif "page_id" in ret['payload']:
                    key = ret['payload']['page_id']
                    AgenticRAG._keep_best_chunk(page_chunk_map, key, ret)
            elif chunk_type == "page":
                key = ret['payload']['page_path']
                AgenticRAG._keep_best_chunk(page_chunk_map, key, ret)

        text_chunks = list(sorted(text_chunk_map.values(), key=lambda k: k['score'], reverse=True))
        image_chunks = list(sorted(image_chunk_map.values(), key=lambda k: k['score'], reverse=True))
        page_chunks = list(sorted(page_chunk_map.values(), key=lambda k: k['score'], reverse=True))

        def build_text_context():
            context = "文本检索内容：\n"
            for doc in text_chunks:
                context += doc["payload"]['text'][:100] + "\n"

            print(context)

        def build_image_context():
            context = ""
            for doc in image_chunks:
                context += f'{doc["payload"]["image_path"]} {doc["score"]}' + "\n"
            print(context)

        def build_page_context():
            context = ""
            for doc in page_chunks:
                context += f'{doc["payload"]["page_path"]} {doc["score"]}' + "\n"
            print(context)

        # build_text_context()
        build_image_context()
        build_page_context()

        return text_chunks, image_chunks, page_chunks

    @staticmethod
    def build_ref_context(docs: List[Dict]):
        context = ""
        for i, doc in enumerate(docs):
            payload = doc.get("payload", {})
            text = payload.get("text")
            if not text:
                continue

            # 给模型补充稳定的引用链接，要求它直接输出 Markdown 可点击引用。
            citation_url = payload.get("image_url") or payload.get("file_url") or ""
            title = payload.get("filename") or payload.get("title") or f"参考资料{i + 1}"
            context += (
                f"\n[ref {i + 1} start]\n"
                f"资料标题: {title}\n"
                f"引用链接: {citation_url}\n"
                f"资料正文:\n{text}\n"
                f"[ref {i + 1} end]\n"
            )
        return context

    @staticmethod
    def extract_answer_image_urls(page_chunks: List[Dict], image_chunks: List[Dict] = None) -> List[str]:
        """从召回结果中提取可直接回答的图片 URL。

        首期 MRAG 前端不会上传查询图片，这里的图片 URL 来自知识库召回结果。
        因此回答阶段不能依赖请求体中的 image_urls，而应该从 page/image chunk 中兜底提取。
        """
        collected_urls = []
        visited_urls = set()
        merged_chunks = list(page_chunks or [])
        if image_chunks:
            merged_chunks.extend(image_chunks)

        for chunk in merged_chunks:
            payload = chunk.get("payload", {})
            image_url = payload.get("image_url")
            if not image_url or image_url in visited_urls:
                continue
            visited_urls.add(image_url)
            collected_urls.append(image_url)
        return collected_urls

    @staticmethod
    def build_image_markdown(image_url: str) -> str:
        """返回附加到回答末尾的 Markdown 图片片段。"""
        return f"\n\n![图片]({image_url})"

    @staticmethod
    def _stage_event(stage: str, data: str, is_final: bool = False, **meta) -> dict:
        """细粒度 SSE 过程事件（对齐 auto_analysis / code_interpreter）。"""
        event = {
            "stage": stage,
            "data": data or "",
            "isFinal": is_final,
        }
        if meta:
            event["meta"] = {k: v for k, v in meta.items() if v is not None}
        return event

    @staticmethod
    def _preview_text(text: str, limit: int = 100) -> str:
        normalized = " ".join(str(text or "").split())
        if len(normalized) <= limit:
            return normalized
        return normalized[:limit] + "..."

    @staticmethod
    def _format_hit_preview(chunks: List[Dict], top_k: int = 3) -> str:
        if not chunks:
            return "_无命中_"
        lines = []
        for idx, chunk in enumerate(chunks[:top_k]):
            payload = chunk.get("payload") or {}
            score = chunk.get("score", 0)
            title = payload.get("filename") or payload.get("title") or payload.get("chunk_type") or "chunk"
            body = AgenticRAG._preview_text(payload.get("text") or payload.get("image_path") or "")
            lines.append(f"- [{score:.3f}] {title}: {body}")
        if len(chunks) > top_k:
            lines.append(f"- …共 {len(chunks)} 条，仅展示 Top {top_k}")
        return "\n".join(lines)

    @time_it
    def multi_retrieval(self, questions: List[str]):
        # 多路检索查询
        results = self.retrieval(questions)
        return results

    @staticmethod
    def _build_trace_hit(stage: str, query: str, chunk: Dict) -> RetrievalTraceHit:
        """将现有 chunk 结构转成评测 trace hit。"""

        payload = chunk.get("payload", {})
        return RetrievalTraceHit(
            stage=stage,
            query=query,
            score=float(chunk.get("score", 0.0)),
            runtime_key=build_runtime_key(payload),
            canonical_key=build_canonical_key(payload),
            payload=payload,
        )

    def collect_retrieval_trace(self, question: str, image_urls: List[str] = None) -> RetrievalTrace:
        """采集 retrieval-backed query 的内部检索 trace（评测/兼容入口）。"""
        trace = None
        for item in self.iter_retrieval(question, image_urls):
            if isinstance(item, RetrievalTrace):
                trace = item
        if trace is None:
            raise RuntimeError("retrieval pipeline did not produce RetrievalTrace")
        return trace

    def iter_retrieval(self, question: str, image_urls: List[str] = None):
        """多轮检索 pipeline：yield 过程 SSE 事件，最后 yield RetrievalTrace。"""
        # 每轮先检索再并发总结，规划模型决定是否继续；过程事件与最终 trace 共用同一批命中数据。
        loop = 1
        answer_question = question
        total_sub_questions = []
        total_sub_summaries = []
        total_chunks = []
        trace_rounds: list[RetrievalTraceRound] = []

        if image_urls:
            image_descs = [QueryProcessor.extract_image_content(uuid.uuid4().hex, image_url) for image_url in image_urls]
        else:
            image_descs = []

        while True:
            logger.info(f"第{loop}轮查询")
            if loop == 1 and image_urls:
                sub_questions = QueryProcessor.expand_question_with_images(answer_question, image_descs)
            else:
                sub_questions = QueryProcessor.extend_questions(answer_question)

            if loop == 1:
                sub_questions.insert(0, question)

            total_sub_questions.extend(sub_questions)

            logger.info("开始多路检索阶段")
            current_chunks = self.multi_retrieval(sub_questions)
            round_hits = []
            flat_round_chunks = []
            for sub_question, query_chunks in zip(sub_questions, current_chunks):
                for chunk in query_chunks:
                    total_chunks.append(chunk)
                    flat_round_chunks.append(chunk)
                    round_hits.append(self._build_trace_hit(f"round{loop}_raw", sub_question, chunk))
            trace_rounds.append(
                RetrievalTraceRound(
                    stage=f"round{loop}_raw",
                    queries=list(sub_questions),
                    hits=round_hits,
                )
            )
            sub_q_lines = "\n".join(f"- {q}" for q in sub_questions) or "- _无_"
            yield self._stage_event(
                "retrieve_round",
                f"\n## 第 {loop} 轮检索  \n\n### 子问题\n{sub_q_lines}\n\n"
                f"### 命中预览（{len(flat_round_chunks)}）\n{self._format_hit_preview(flat_round_chunks)}\n",
                round=loop,
                hitCount=len(flat_round_chunks),
                subQuestions=list(sub_questions),
            )

            loop += 1
            if loop > 3:
                break

            tasks = {}
            summarized_infos = {}
            # 总结任务按 future 完成顺序回收，但最终按 sub_questions 顺序写回，保证展示和 trace 稳定。
            with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
                for sub_question, query_chunks in zip(sub_questions, current_chunks):
                    task = executor.submit(QueryProcessor.summarize_subquery, sub_question, query_chunks)
                    tasks[task] = sub_question

                for future in concurrent.futures.as_completed(tasks):
                    sub_question = tasks[future]
                    try:
                        result = future.result()
                        logger.info(f"总结结果: {result}")
                        summarized_infos[sub_question] = result
                    except Exception as e:
                        logger.error(f"Error occurred while summarizing {sub_question}: {e}")
                        summarized_infos[sub_question] = {"summary": f"总结失败: {e}"}

            for sub_question in sub_questions:
                total_sub_summaries.append(summarized_infos.get(sub_question, {}))

            summary_lines = []
            for sub_question in sub_questions:
                info = summarized_infos.get(sub_question) or {}
                summary = info.get("summary") if isinstance(info, dict) else str(info)
                summary_lines.append(f"- **{sub_question}**: {self._preview_text(summary, 160)}")
            yield self._stage_event(
                "summarize",
                f"\n### 子问总结\n" + ("\n".join(summary_lines) if summary_lines else "_无_") + "\n",
                round=loop - 1,
            )

            next_instruction = QueryProcessor.generate_next_instruction(
                question,
                total_sub_questions,
                total_sub_summaries,
            )
            if next_instruction['is_answer']:
                yield self._stage_event(
                    "plan_next",
                    "\n### 检索规划\n证据已足够，进入答案生成。\n",
                    continueSearch=False,
                )
                break
            answer_question = next_instruction['rewrite_query']
            yield self._stage_event(
                "plan_next",
                f"\n### 检索规划\n继续检索，改写查询：`{answer_question}`\n",
                continueSearch=True,
                rewriteQuery=answer_question,
            )

        text_chunks, image_chunks, page_chunks = self.merge_retrieval_results(total_chunks)
        page_chunks = page_chunks[:1]
        answer_image_urls = self.extract_answer_image_urls(page_chunks, image_chunks)
        yield self._stage_event(
            "merge",
            f"\n## 结果融合  \n- 文本: {len(text_chunks)}\n- 图片: {len(image_chunks)}\n- 页面: {len(page_chunks)}\n",
            textCount=len(text_chunks),
            imageCount=len(image_chunks),
            pageCount=len(page_chunks),
        )

        texts = [text_chunk['payload']['text'] for text_chunk in text_chunks]
        scores = get_text_reranker().rerank(question, texts) if texts else []
        reranked_text_chunks = []
        for text_chunk, score in zip(text_chunks, scores):
            updated_chunk = {
                "score": score,
                "payload": text_chunk["payload"],
            }
            reranked_text_chunks.append(updated_chunk)
        reranked_text_chunks = sorted(reranked_text_chunks, key=lambda k: k['score'], reverse=True)
        kept = [c for c in reranked_text_chunks if c.get("score", 0) > 0.3]
        yield self._stage_event(
            "rerank",
            f"\n## 文本重排  \n保留 score>0.3 共 {len(kept)} 条（候选 {len(reranked_text_chunks)}）\n\n"
            f"{self._format_hit_preview(kept or reranked_text_chunks)}\n",
            keptCount=len(kept),
            candidateCount=len(reranked_text_chunks),
        )

        merged_text_hits = [self._build_trace_hit("merged_text", question, chunk) for chunk in text_chunks]
        merged_image_hits = [self._build_trace_hit("merged_image", question, chunk) for chunk in image_chunks]
        merged_page_hits = [self._build_trace_hit("merged_page", question, chunk) for chunk in page_chunks]
        merged_all_hits = merged_text_hits + merged_image_hits + merged_page_hits
        rerank_hits = [self._build_trace_hit("rerank_text", question, chunk) for chunk in reranked_text_chunks]

        yield RetrievalTrace(
            question=question,
            rounds=trace_rounds,
            round1_raw=RetrievalTraceStage(
                stage="round1_raw",
                hits=trace_rounds[0].hits if trace_rounds else [],
            ),
            all_rounds_raw=RetrievalTraceStage(
                stage="all_rounds_raw",
                hits=[hit for round_trace in trace_rounds for hit in round_trace.hits],
            ),
            merged_text=RetrievalTraceStage(stage="merged_text", hits=merged_text_hits),
            merged_image=RetrievalTraceStage(stage="merged_image", hits=merged_image_hits),
            merged_page=RetrievalTraceStage(stage="merged_page", hits=merged_page_hits),
            merged_all=RetrievalTraceStage(stage="merged_all", hits=merged_all_hits),
            rerank_text=RetrievalTraceStage(stage="rerank_text", hits=rerank_hits),
            answer_image_urls=answer_image_urls,
        )

    @staticmethod
    def llm_answer(question: str):
        prompt = PromptManager.DEFAULT_PROMPT.format(question=question)
        messages = LLMClient.convert_messages(prompt)
        response = LLMClient().completions(messages, stream=True, )
        return response

    @staticmethod
    def vlm_answer(question: str, image_urls: List[str]):
        prompt = f"根据图片回答问题：{question}"
        client = VLLMClient()
        messages = client.convert_messages_with_image_path(prompt, image_urls[0])
        response = client.completions(messages, stream=True)
        return response

    def fast_answer(self, question: str, image_urls: List[str] = None):
        if not image_urls:
            return self.llm_answer(question)
        else:
            return self.vlm_answer(question, image_urls)

    def _yield_answer_stream(self, stream):
        """答案 token 流：保留 OpenAI chunk 兼容，并包一层 stage=answer 便于前端过程区。"""
        yield self._stage_event("answer", "\n# 生成答案  \n", mode="stream_start")
        for chunk in stream:
            yield chunk

    @time_it
    def run(self, question: str, image_urls: List[str] = None):
        logger.info(f"AIAgent: {question}, {image_urls}")
        kb_label = self._kb_id if isinstance(self._kb_id, str) else ",".join(self._kb_id or [])
        yield self._stage_event(
            "task",
            f"# 检索任务  \n{question}  \n\n- kb: `{kb_label or 'default'}`\n"
            f"- images: {len(image_urls or [])}\n",
            kbScope=self._kb_id,
            imageCount=len(image_urls or []),
        )

        if image_urls:
            image_descs = [QueryProcessor.extract_image_content(uuid.uuid4().hex, image_url) for image_url in
                           image_urls]
        else:
            image_descs = []

        # 0.判断用户的问题是否需要检索
        # 先做轻量路由，再进入 Agentic 检索；无文本命中时继续尝试图片回答，最后才回退到普通 LLM。
        simple_check_flag = QueryProcessor.simple_query_check(question)
        if simple_check_flag:
            yield self._stage_event("route", "\n## 路由  \n无需检索，直接 LLM 回答。\n", mode="simple_llm")
            yield from self._yield_answer_stream(self.llm_answer(question))
            yield self._stage_event("final", "", is_final=True, mode="simple_llm")
            return

        simple_image_query = QueryProcessor.simple_image_query_check(question, image_descs)
        if image_urls and simple_image_query:
            yield self._stage_event("route", "\n## 路由  \n图片直答（VLM）。\n", mode="simple_vlm")
            yield from self._yield_answer_stream(self.vlm_answer(question, image_urls))
            yield self._stage_event("final", "", is_final=True, mode="simple_vlm")
            return

        yield self._stage_event("route", "\n## 路由  \n进入 Agentic 多轮检索。\n", mode="agentic")

        trace = None
        for item in self.iter_retrieval(question, image_urls):
            if isinstance(item, RetrievalTrace):
                trace = item
            else:
                yield item
        if trace is None:
            yield self._stage_event("error", "检索链路未返回有效结果。", is_final=True)
            return

        text_chunks = [hit.to_chunk() for hit in trace.merged_text.hits]
        image_chunks = [hit.to_chunk() for hit in trace.merged_image.hits]
        page_chunks = [hit.to_chunk() for hit in trace.merged_page.hits]

        logger.info(
            f"Agentic search results: 文本: {len(text_chunks)}, 图片: {len(image_chunks)}, 页面: {len(page_chunks)}")

        answer_image_urls = list(trace.answer_image_urls)

        # 3. 文本重排结果（iter_retrieval 已推 rerank 事件）
        logger.info("开始重排阶段")
        text_chunks = [hit.to_chunk() for hit in trace.rerank_text.hits]
        display_chunks(text_chunks)

        text_chunks = [text_chunk for text_chunk in text_chunks if text_chunk['score'] > 0.3]

        if not text_chunks:
            logger.info("没有找到文本检索结果")

            if answer_image_urls:
                logger.info("使用图片问答")
                yield from self._yield_answer_stream(self.vlm_answer(question, answer_image_urls))
                # 不复用大模型最后一个 chunk，避免 SDK 结束包 choices 为空时再次崩溃。
                yield self.build_image_markdown(answer_image_urls[0])
                yield self._stage_event("final", "", is_final=True, mode="vlm_fallback")
                return

            logger.info("使用LLM回答")
            yield from self._yield_answer_stream(self.fast_answer(question, image_urls))
            yield self._stage_event("final", "", is_final=True, mode="llm_fallback")
            return

        context = self.build_ref_context(text_chunks)

        if not answer_image_urls:
            # 只有文本证据时使用文本提示；有页面/图片证据时切换 VLM，并在回答后显式补回图片 Markdown。
            logger.info("没有找到图片, 使用LLM回答")
            prompt = PromptManager.TEXT_PROMPT.format(context=context, question=question)
            messages = LLMClient().convert_messages(prompt)

            response = LLMClient().completions(messages, stream=True)
            yield from self._yield_answer_stream(response)
            yield self._stage_event("final", "", is_final=True, mode="text_rag")
            return

        logger.info("使用多模态模型回答")
        prompt = PromptManager.IMAGE_PROMPT.format(context=context, question=question)
        image_path = answer_image_urls[0]

        client = VLLMClient()
        messages = client.convert_messages_with_image_path(prompt, image_path)

        response = client.completions(messages, stream=True)
        yield from self._yield_answer_stream(response)
        # 追加独立的 Markdown 图片结果，避免依赖供应商 SDK chunk 结构。
        yield self.build_image_markdown(image_path)
        yield self._stage_event("final", "", is_final=True, mode="image_rag")
