# -*- coding: utf-8 -*-
import unittest

from reactor_tool.tool.analysis_component.analysis_fc_agent import extract_code_from_message


class AnalysisFcExtractCodeTest(unittest.TestCase):
    def test_extract_from_tool_call(self):
        tool_calls = [
            {
                "id": "1",
                "type": "function",
                "function": {
                    "name": "python_interpreter",
                    "arguments": '{"code": "df = get_data(query=\\"x\\")\\nprint(df)"}',
                },
            }
        ]
        code, source = extract_code_from_message("", tool_calls)
        self.assertEqual("tool_call:python_interpreter", source)
        self.assertIn("get_data", code)

    def test_extract_from_markdown_fence(self):
        content = "Thought: 先取数\n```python\ndf = get_data(query='a')\n```\n"
        code, source = extract_code_from_message(content, None)
        self.assertEqual("markdown_fence", source)
        self.assertIn("get_data", code)

    def test_extract_from_code_tag(self):
        content = "Thought: x\n<code>\nfinal_answer('done')\n</code>"
        code, source = extract_code_from_message(content, None)
        self.assertEqual("code_tag", source)
        self.assertIn("final_answer", code)

    def test_wrap_markdown_conclusion(self):
        content = "**销售数据时间趋势分析结论**\n上升趋势明确。"
        code, source = extract_code_from_message(content, None)
        self.assertEqual("wrapped_final_answer", source)
        self.assertTrue(code.startswith("final_answer("))


if __name__ == "__main__":
    unittest.main()
