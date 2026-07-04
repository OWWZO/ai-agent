# -*- coding: utf-8 -*-
import unittest
from unittest.mock import patch

from reactor_tool.tool.mrag.query.aigent import AgenticRAG


class AgenticRagEvalTraceTest(unittest.TestCase):

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
