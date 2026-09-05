# -*- coding: utf-8 -*-
import asyncio
import unittest
from unittest.mock import patch

from reactor_tool.tool.table_rag.table_column_filter import ColumnFilterModule


class TableColumnFilterRetryTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_reset_llm_response_before_retrying_invalid_json(self):
        responses = [
            '{"relatedFlag":true,"columnIndexes":[1,2}',
            '{"relatedFlag":true,"columnIndexes":[1]}',
        ]
        calls = 0

        async def fake_ask_llm(*args, **kwargs):
            nonlocal calls
            response = responses[calls]
            calls += 1
            yield response

        module = ColumnFilterModule(
            request_id="retry-test",
            query="查询销售额",
            current_date_info="",
            table_id_list=["sales"],
            column_info=[],
        )
        table_schema_info = {
            "modelCode": "sales",
            "schemaList": [{"columnId": "sales", "columnIndex": 1, "defaultRecall": 0}],
        }

        with patch(
            "reactor_tool.tool.table_rag.table_column_filter.ask_llm",
            new=fake_ask_llm,
        ):
            result = await module._filter_single_table(
                asyncio.Semaphore(1), table_schema_info
            )

        if result is None:
            self.fail("column filter unexpectedly discarded the table")
        self.assertEqual(2, calls)
        self.assertEqual("sales", result["modelCode"])
        self.assertEqual(["sales"], [item["columnId"] for item in result["schemaList"]])
