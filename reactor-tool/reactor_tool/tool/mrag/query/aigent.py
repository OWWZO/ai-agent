"""
Agentic RAG模块

该模块实现智能化的RAG系统，具备推理和决策能力：
- 智能查询规划
- 多步推理
- 工具调用
- 自我反思和优化

主要功能：
1. 查询分解和规划
2. 多步推理和验证
3. 外部工具调用集成
4. 结果评估和自我修正
5. 对话记忆管理
6. 个性化推荐策略
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
    for i, chunk in enumerate(chunks):
        print(f"=======================Chunk {i}: ")
        print(f"score: {chunk['score']}")
        print(f"chunk: {chunk['payload']['text'][:100]}")


class AgenticRAG:
    """智能RAG系统类"""

    def __init__(self, kb_id: str, n_round: int = 5):
        self._n_round = n_round
        self._retriever = BaseRetriever()
        self._kb_id = kb_id

    def retrieval(self, questions: list[str]) -> List[List[Dict]]:
        text_resp = self._retriever.retrieval_by_texts(self._kb_id, questions)
        return text_resp

    @staticmethod
    def merge_retrieval_results(resp: List[Dict]) -> Tuple[List[Dict], List[Dict], List[Dict]]:
        # 根据类型， 去重
        text_chunk_map = {}
        image_chunk_map = {}
        page_chunk_map = {}
        for ret in resp:
            chunk_type = ret['payload']['chunk_type']
            if chunk_type == "text":
                # print(ret)
                key = ret['payload']['file_sorted']
                text_chunk_map[key] = ret
            elif chunk_type == "image" or chunk_type == "ocr_text" or chunk_type == "caption":
                # print("image: ", ret)
                if "image_id" in ret['payload']:
                    key = ret['payload']['image_id']
                    image_chunk_map[key] = ret
                elif "page_id" in ret['payload']:
                    key = ret['payload']['page_id']
                    page_chunk_map[key] = ret
            elif chunk_type == "page":
                key = ret['payload']['page_path']
                page_chunk_map[key] = ret

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
            if doc['payload'].get("text"):
                context += f"\n[ref {i + 1} start]\n{doc['payload']['text']}\n[ref {i + 1} end]\n"
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
        """采集 retrieval-backed query 的内部检索 trace。"""

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
            for sub_question, query_chunks in zip(sub_questions, current_chunks):
                for chunk in query_chunks:
                    total_chunks.append(chunk)
                    round_hits.append(self._build_trace_hit(f"round{loop}_raw", sub_question, chunk))
            trace_rounds.append(
                RetrievalTraceRound(
                    stage=f"round{loop}_raw",
                    queries=list(sub_questions),
                    hits=round_hits,
                )
            )

            loop += 1
            if loop > 3:
                break

            tasks = {}
            summarized_infos = {}
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

            for sub_question in sub_questions:
                total_sub_summaries.append(summarized_infos[sub_question])

            next_instruction = QueryProcessor.generate_next_instruction(
                question,
                total_sub_questions,
                total_sub_summaries,
            )
            if next_instruction['is_answer']:
                break
            answer_question = next_instruction['rewrite_query']

        text_chunks, image_chunks, page_chunks = self.merge_retrieval_results(total_chunks)
        page_chunks = page_chunks[:1]
        answer_image_urls = self.extract_answer_image_urls(page_chunks, image_chunks)

        texts = [text_chunk['payload']['text'] for text_chunk in text_chunks]
        scores = get_text_reranker().rerank(question, texts)
        reranked_text_chunks = []
        for text_chunk, score in zip(text_chunks, scores):
            updated_chunk = {
                "score": score,
                "payload": text_chunk["payload"],
            }
            reranked_text_chunks.append(updated_chunk)
        reranked_text_chunks = sorted(reranked_text_chunks, key=lambda k: k['score'], reverse=True)

        merged_text_hits = [self._build_trace_hit("merged_text", question, chunk) for chunk in text_chunks]
        merged_image_hits = [self._build_trace_hit("merged_image", question, chunk) for chunk in image_chunks]
        merged_page_hits = [self._build_trace_hit("merged_page", question, chunk) for chunk in page_chunks]
        merged_all_hits = merged_text_hits + merged_image_hits + merged_page_hits
        rerank_hits = [self._build_trace_hit("rerank_text", question, chunk) for chunk in reranked_text_chunks]

        return RetrievalTrace(
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

    @time_it
    def run(self, question: str, image_urls: List[str] = None):
        logger.info(f"AIAgent: {question}, {image_urls}")
        if image_urls:
            image_descs = [QueryProcessor.extract_image_content(uuid.uuid4().hex, image_url) for image_url in
                           image_urls]
        else:
            image_descs = []

        # 0.判断用户的问题是否需要检索
        simple_check_flag = QueryProcessor.simple_query_check(question)
        if simple_check_flag:
            yield from self.llm_answer(question)
            return

        simple_image_query = QueryProcessor.simple_image_query_check(question, image_descs)
        if image_urls and simple_image_query:
            yield from self.vlm_answer(question, image_urls)
            return

        trace = self.collect_retrieval_trace(question, image_urls)
        text_chunks = [hit.to_chunk() for hit in trace.merged_text.hits]
        image_chunks = [hit.to_chunk() for hit in trace.merged_image.hits]
        page_chunks = [hit.to_chunk() for hit in trace.merged_page.hits]

        logger.info(
            f"Agentic search results: 文本: {len(text_chunks)}, 图片: {len(image_chunks)}, 页面: {len(page_chunks)}")

        answer_image_urls = list(trace.answer_image_urls)

        # 3. 文本重排
        logger.info("开始重排阶段")
        text_chunks = [hit.to_chunk() for hit in trace.rerank_text.hits]
        # 重排结果
        display_chunks(text_chunks)

        text_chunks = [text_chunk for text_chunk in text_chunks if text_chunk['score'] > 0.3]

        if not text_chunks:
            logger.info("没有找到文本检索结果")

            if answer_image_urls:
                logger.info("使用图片问答")
                for chunk in self.vlm_answer(question, answer_image_urls):
                    yield chunk
                # 不复用大模型最后一个 chunk，避免 SDK 结束包 choices 为空时再次崩溃。
                yield self.build_image_markdown(answer_image_urls[0])
                return

            logger.info("使用LLM回答")
            yield from self.fast_answer(question, image_urls)
            return

        context = self.build_ref_context(text_chunks)

        if not answer_image_urls:
            logger.info("没有找到图片, 使用LLM回答")
            prompt = PromptManager.TEXT_PROMPT.format(context=context, question=question)
            messages = LLMClient().convert_messages(prompt)

            response = LLMClient().completions(messages, stream=True)
            yield from response
            return

        logger.info("使用多模态模型回答")
        prompt = PromptManager.IMAGE_PROMPT.format(context=context, question=question)
        image_path = answer_image_urls[0]

        client = VLLMClient()
        messages = client.convert_messages_with_image_path(prompt, image_path)

        response = client.completions(messages, stream=True)
        for chunk in response:
            yield chunk
        # 追加独立的 Markdown 图片结果，避免依赖供应商 SDK chunk 结构。
        yield self.build_image_markdown(image_path)
