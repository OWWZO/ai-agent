# -*- coding: utf-8 -*-
import asyncio
import unittest
from unittest.mock import AsyncMock, patch

from reactor_tool.model.protocal import AutoAnalysisRequest
from reactor_tool.tool.auto_analysis import AutoAnalysisAgent


class AutoAnalysisReportTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_use_requested_report_file_name(self):
        request = AutoAnalysisRequest(
            request_id="analysis-request",
            task="统计销售趋势",
            modelCodeList=["sales-model"],
            reportFileName="销售趋势报告.md",
        )
        agent = AutoAnalysisAgent(queue=asyncio.Queue())

        with (
            patch(
                "reactor_tool.tool.auto_analysis.get_schema",
                return_value={"schemaInfo": []},
            ),
            patch.object(
                agent,
                "analysis",
                new=AsyncMock(return_value={"insights": [], "summary": "分析完成"}),
            ),
            patch(
                "reactor_tool.tool.auto_analysis.upload_file",
                new=AsyncMock(return_value={"fileName": "销售趋势报告.md"}),
            ) as upload,
        ):
            result = await agent.run(**request.model_dump())

        self.assertEqual("分析完成", result["summary"])
        self.assertEqual("销售趋势报告.md", upload.await_args.kwargs["file_name"])
        self.assertEqual("md", upload.await_args.kwargs["file_type"])

    async def test_should_keep_analysis_result_when_report_upload_fails(self):
        agent = AutoAnalysisAgent(queue=asyncio.Queue())

        with (
            patch(
                "reactor_tool.tool.auto_analysis.get_schema",
                return_value={"schemaInfo": []},
            ),
            patch.object(
                agent,
                "analysis",
                new=AsyncMock(return_value={"insights": [], "summary": "完整分析结论"}),
            ),
            patch(
                "reactor_tool.tool.auto_analysis.upload_file",
                new=AsyncMock(side_effect=RuntimeError("HTTP 500")),
            ),
        ):
            result = await agent.run(
                task="统计销售趋势",
                modelCodeList=["sales-model"],
                request_id="analysis-request",
                report_file_name="销售趋势报告.md",
            )

        self.assertEqual("完整分析结论", result["summary"])
        events = []
        while not agent.queue.empty():
            events.append(await agent.queue.get())
        final_events = [
            event
            for event in events
            if isinstance(event, dict) and event.get("isFinal") is True
        ]
        self.assertEqual(1, len(final_events))
        self.assertEqual("\n完整分析结论\n", final_events[0]["data"])


if __name__ == "__main__":
    unittest.main()
