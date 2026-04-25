# -*- coding: utf-8 -*-
import unittest

from reactor_tool.tool.mrag.query.query_processor import QueryProcessor


class QueryProcessorJsonParseTest(unittest.TestCase):

    def test_should_parse_fenced_json_object(self):
        raw = """
        ```json
        {
          "is_answer": 1,
          "rewrite_query": "",
          "reason": "信息已经充分"
        }
        ```
        """

        result = QueryProcessor._parse_json_object(raw)

        self.assertEqual(1, result["is_answer"])
        self.assertEqual("", result["rewrite_query"])
        self.assertEqual("信息已经充分", result["reason"])

    def test_should_parse_plain_json_object_without_code_fence(self):
        raw = """
        {
          "is_answer": 1,
          "rewrite_query": "",
          "reason": "知识库为空，直接结束"
        }
        """

        result = QueryProcessor._parse_json_object(raw)

        self.assertEqual(1, result["is_answer"])
        self.assertEqual("", result["rewrite_query"])
        self.assertEqual("知识库为空，直接结束", result["reason"])

    def test_should_parse_json_object_wrapped_by_explanation_text(self):
        raw = """
        分析如下：
        {
          "is_answer": 0,
          "rewrite_query": "补充检索项目名称历史记录",
          "reason": "当前信息不足"
        }
        请继续执行。
        """

        result = QueryProcessor._parse_json_object(raw)

        self.assertEqual(0, result["is_answer"])
        self.assertEqual("补充检索项目名称历史记录", result["rewrite_query"])
        self.assertEqual("当前信息不足", result["reason"])


if __name__ == "__main__":
    unittest.main()
