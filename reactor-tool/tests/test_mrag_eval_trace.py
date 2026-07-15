# -*- coding: utf-8 -*-
import unittest
from unittest.mock import patch

from reactor_tool.tool.mrag.generation import PromptManager
from reactor_tool.tool.mrag.query.aigent import AgenticRAG


class AgenticRagEvalTraceTest(unittest.TestCase):

    def test_should_expose_citation_url_in_ref_context_for_model(self):
        context = AgenticRAG.build_ref_context(
            [
                {
                    "score": 0.91,
                    "payload": {
                        "chunk_type": "text",
                        "text": "行业基准数据经过采集、审核、规格化后发布。",
                        "filename": "guide.pdf",
                        "file_url": "http://127.0.0.1:1601/download/req/guide.pdf",
                    },
                },
                {
                    "score": 0.82,
                    "payload": {
                        "chunk_type": "caption",
                        "text": "图 3.1 展示了行业基准数据处理流程。",
                        "filename": "guide.pdf",
                        "image_url": "http://127.0.0.1:1601/files/page_3.png",
                    },
                },
            ]
        )

        self.assertIn("资料标题: guide.pdf", context)
        self.assertIn("引用链接: http://127.0.0.1:1601/download/req/guide.pdf", context)
        self.assertIn("引用链接: http://127.0.0.1:1601/files/page_3.png", context)
        self.assertIn("资料正文:\n行业基准数据经过采集、审核、规格化后发布。", context)

    def test_should_require_clickable_markdown_citations_in_prompts(self):
        self.assertIn("[〔1〕](https://example.com/source-1)", PromptManager.TEXT_PROMPT)
        self.assertIn("禁止输出不可点击的 `〔1〕〔2〕` 纯文本编号", PromptManager.TEXT_PROMPT)
        self.assertIn("[〔1〕](https://example.com/source-1)", PromptManager.IMAGE_PROMPT)
        self.assertIn("禁止输出不可点击的 `〔1〕〔2〕` 纯文本编号", PromptManager.IMAGE_PROMPT)

    def test_should_keep_ocr_and_caption_as_text_context_while_preserving_visual_links(self):
        text_chunks, image_chunks, page_chunks = AgenticRAG.merge_retrieval_results(
            [
                {
                    "score": 0.91,
                    "payload": {
                        "chunk_type": "ocr_text",
                        "text": "发票号码 12345",
                        "filename": "demo.pdf",
                        "file_sorted": "img-1",
                        "image_id": "img-1",
                        "image_path": "images/img_1.png",
                        "image_url": "http://img",
                    },
                },
                {
                    "score": 0.82,
                    "payload": {
                        "chunk_type": "caption",
                        "text": "第一页是审批流程图",
                        "filename": "demo.pdf",
                        "file_sorted": "page-1",
                        "page_id": "page-1",
                        "page_path": "pages/page_1.png",
                        "image_url": "http://page",
                    },
                },
            ]
        )

        self.assertEqual(["ocr_text", "caption"], [chunk["payload"]["chunk_type"] for chunk in text_chunks])
        self.assertEqual(["img-1"], [chunk["payload"]["image_id"] for chunk in image_chunks])
        self.assertEqual(["page-1"], [chunk["payload"]["page_id"] for chunk in page_chunks])
        self.assertEqual(
            ["http://page", "http://img"],
            AgenticRAG.extract_answer_image_urls(page_chunks, image_chunks),
        )

    def test_should_collect_trace_for_retrieval_backed_query(self):
        agent = AgenticRAG("kb-demo")
        first_round_hits = [
            [{"score": 0.91, "payload": {"chunk_type": "text", "text": "A", "filename": "demo.pdf", "file_sorted": "f-1"}}],
            [{"score": 0.72, "payload": {"chunk_type": "page", "page_path": "pages/page_1.png", "filename": "demo.pdf", "page_id": "p-1"}}],
        ]

        with patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.simple_query_check",
            return_value=False,
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.extend_questions",
            return_value=["子问题1"],
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.summarize_subquery",
            return_value="足够了",
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.generate_next_instruction",
            return_value={"is_answer": True, "rewrite_query": ""},
        ), patch.object(
            agent,
            "multi_retrieval",
            return_value=first_round_hits,
        ), patch.object(
            AgenticRAG,
            "merge_retrieval_results",
            return_value=(
                [{"score": 0.91, "payload": {"chunk_type": "text", "text": "A", "filename": "demo.pdf", "file_sorted": "f-1"}}],
                [{"score": 0.61, "payload": {"chunk_type": "image", "image_path": "images/img_1.png", "filename": "demo.pdf", "image_id": "i-1", "image_url": "http://img"}}],
                [{"score": 0.72, "payload": {"chunk_type": "page", "page_path": "pages/page_1.png", "filename": "demo.pdf", "page_id": "p-1", "image_url": "http://page"}}],
            ),
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.get_text_reranker",
        ) as reranker_factory:
            reranker_factory.return_value.rerank.return_value = [0.88]

            trace = agent.collect_retrieval_trace("主问题")

        self.assertEqual("主问题", trace.question)
        self.assertEqual(1, len(trace.rounds))
        self.assertEqual(["主问题", "子问题1"], trace.rounds[0].queries)
        self.assertEqual("round1_raw", trace.rounds[0].stage)
        self.assertEqual(2, len(trace.rounds[0].hits))
        self.assertEqual("merged_text", trace.merged_text.stage)
        self.assertEqual("merged_image", trace.merged_image.stage)
        self.assertEqual("merged_page", trace.merged_page.stage)
        self.assertEqual("merged_all", trace.merged_all.stage)
        self.assertEqual("rerank_text", trace.rerank_text.stage)
        self.assertEqual(["http://page", "http://img"], trace.answer_image_urls)
        self.assertEqual("f-1", trace.rerank_text.hits[0].runtime_key)

    def test_should_keep_simple_query_fast_path_without_retrieval_trace(self):
        agent = AgenticRAG("kb-demo")

        with patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.simple_query_check",
            return_value=True,
        ), patch.object(
            agent,
            "llm_answer",
            return_value=iter(["直接回答"]),
        ) as llm_answer, patch.object(
            agent,
            "collect_retrieval_trace",
        ) as collect_trace:
            result = list(agent.run("今天天气怎么样"))

        self.assertEqual(["直接回答"], result)
        llm_answer.assert_called_once_with("今天天气怎么样")
        collect_trace.assert_not_called()

    def test_should_keep_simple_image_query_fast_path_without_retrieval_trace(self):
        agent = AgenticRAG("kb-demo")

        with patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.extract_image_content",
            return_value="这是一只猫",
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.simple_query_check",
            return_value=False,
        ), patch(
            "reactor_tool.tool.mrag.query.aigent.QueryProcessor.simple_image_query_check",
            return_value=True,
        ), patch.object(
            agent,
            "vlm_answer",
            return_value=iter(["图片直答"]),
        ) as vlm_answer, patch.object(
            agent,
            "collect_retrieval_trace",
        ) as collect_trace:
            result = list(agent.run("这张图里是什么", image_urls=["http://img"]))

        self.assertEqual(["图片直答"], result)
        vlm_answer.assert_called_once_with("这张图里是什么", ["http://img"])
        collect_trace.assert_not_called()


if __name__ == "__main__":
    unittest.main()
