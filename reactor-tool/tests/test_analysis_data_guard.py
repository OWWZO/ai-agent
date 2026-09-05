# -*- coding: utf-8 -*-
import asyncio
import json
import unittest
from typing import Any, cast
from unittest.mock import patch

import pandas as pd
from requests.exceptions import ReadTimeout

from reactor_tool.model.context import AnalysisContext
from reactor_tool.tool.analysis_component.analysis_fc_agent import (
    AnalysisFCCodeAgent,
    AnalysisPythonInterpreterTool,
)
from reactor_tool.tool.analysis_component.analysis_tool import (
    AnalysisDataFetchError,
    GetDataTool,
    SaveInsightTool,
)


class AnalysisDataGuardTest(unittest.TestCase):
    def _context(self):
        return AnalysisContext(
            task="统计销售数量",
            request_id="analysis-guard-test",
            modelCodeList=["sales-model"],
            schemas=[],
            queue=asyncio.Queue(),
        )

    def test_get_data_should_abort_on_timeout(self):
        context = self._context()
        tool = GetDataTool(context=context)

        with patch(
            "reactor_tool.tool.analysis_component.analysis_tool.get_data",
            side_effect=ReadTimeout("backend did not respond"),
        ):
            with self.assertRaises(AnalysisDataFetchError) as raised:
                tool.forward("统计销售数量")

        self.assertIn("取数超时", str(raised.exception))
        self.assertEqual(str(raised.exception), context.data_fetch_error)

    def test_get_data_should_abort_on_empty_result(self):
        context = self._context()
        tool = GetDataTool(context=context)

        with patch(
            "reactor_tool.tool.analysis_component.analysis_tool.get_data",
            return_value=[],
        ):
            with self.assertRaises(AnalysisDataFetchError) as raised:
                tool.forward("统计销售数量")

        self.assertEqual("取数返回空结果，无法完成分析", str(raised.exception))
        self.assertEqual(str(raised.exception), context.data_fetch_error)

    def test_get_data_should_abort_before_http_for_invalid_query_type(self):
        context = self._context()
        tool = GetDataTool(context=context)

        with patch(
            "reactor_tool.tool.analysis_component.analysis_tool.get_data"
        ) as fetch:
            with self.assertRaises(AnalysisDataFetchError) as raised:
                tool.forward(cast(Any, {"table": "销售明细"}))

        fetch.assert_not_called()
        self.assertIn("query 必须是非空字符串", str(raised.exception))

    def test_get_data_should_still_return_dataframe_for_valid_result(self):
        context = self._context()
        tool = GetDataTool(context=context)
        result = {
            "columnList": [
                {"name": "销售数量", "guid": "sales_quantity", "col": "sales_quantity"}
            ],
            "dataList": [{"sales_quantity": 12}],
            "dimCols": [],
            "measureCols": ["sales_quantity"],
            "filters": [],
            "nl2sqlResult": "select sum(quantity) as sales_quantity from sales_data",
        }

        with patch(
            "reactor_tool.tool.analysis_component.analysis_tool.get_data",
            return_value=[result],
        ):
            dataframe = tool.forward("统计销售数量")

        self.assertIsInstance(dataframe, pd.DataFrame)
        self.assertEqual(["销售数量"], list(dataframe.columns))
        self.assertEqual(12, dataframe.iloc[0]["销售数量"])
        self.assertIsNone(context.data_fetch_error)

    def test_interpreter_should_stop_after_data_fetch_failure(self):
        context = self._context()
        tool = GetDataTool(context=context)
        runner = AnalysisPythonInterpreterTool(
            inner_tools={"get_data": tool},
            context=context,
        )

        with patch(
            "reactor_tool.tool.analysis_component.analysis_tool.get_data",
            side_effect=ReadTimeout("backend did not respond"),
        ):
            observation = runner.forward("data = get_data(query='统计销售数量')")

        self.assertTrue(runner.last_is_final)
        self.assertEqual(
            {"insights": [], "summary": context.data_fetch_error},
            runner.last_output,
        )
        self.assertIn("Code execution stopped", observation)

    def test_agent_should_not_request_another_llm_step_after_data_fetch_failure(self):
        context = self._context()
        tool = GetDataTool(context=context)
        agent = AnalysisFCCodeAgent(
            instructions="",
            inner_tools={"get_data": tool},
            max_steps=3,
            context=context,
        )
        response = {
            "choices": [
                {
                    "message": {
                        "content": "",
                        "tool_calls": [
                            {
                                "id": "call-1",
                                "type": "function",
                                "function": {
                                    "name": "python_interpreter",
                                    "arguments": json.dumps(
                                        {
                                            "code": "data = get_data(query='统计销售数量')"
                                        }
                                    ),
                                },
                            }
                        ],
                    }
                }
            ]
        }

        with (
            patch(
                "reactor_tool.tool.analysis_component.analysis_fc_agent.chat_completion_with_tools",
                return_value=response,
            ) as completion,
            patch(
                "reactor_tool.tool.analysis_component.analysis_tool.get_data",
                side_effect=ReadTimeout("backend did not respond"),
            ),
        ):
            events = list(agent._run_stream("统计销售数量"))

        self.assertEqual(1, completion.call_count)
        self.assertTrue(events[-1].is_final)
        self.assertEqual(context.data_fetch_error, events[-1].output["summary"])

    def test_system_prompt_should_document_inner_tool_signatures(self):
        agent = AnalysisFCCodeAgent(instructions="", inner_tools={})
        prompt = agent._system_prompt()

        self.assertIn("get_data(query: str)", prompt)
        self.assertIn("save_insight(df, insight: str, analysis_process: str)", prompt)
        self.assertIn("不要给 get_data 传 table、columns、table_name、metrics", prompt)

    def test_save_insight_should_log_non_dataframe_without_name_error(self):
        context = self._context()
        tool = SaveInsightTool(context=context)

        result = tool.forward("not-a-dataframe", "一个结论", "直接分析")

        self.assertIn("保存洞察", result)
        self.assertEqual("", context.insights[0]["data"])


if __name__ == "__main__":
    unittest.main()
