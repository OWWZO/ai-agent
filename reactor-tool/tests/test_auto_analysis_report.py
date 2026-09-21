# -*- coding: utf-8 -*-
import asyncio
import threading
import unittest
from unittest.mock import AsyncMock, patch

from reactor_tool.model.protocal import AutoAnalysisRequest
from reactor_tool.tool.auto_analysis import AutoAnalysisAgent
from reactor_tool.model.context import AnalysisContext
from reactor_tool.tool.analysis_component.analysis_fc_agent import AnalysisStepEvent


class AutoAnalysisReportTest(unittest.IsolatedAsyncioTestCase):
    async def test_should_iterate_agent_on_bounded_worker_and_forward_events(self):
        observed_threads = []

        class FakeAgent:
            def run(self, task, stream):
                observed_threads.append(threading.current_thread().name)
                yield AnalysisStepEvent(
                    step=1,
                    thought="thinking",
                    code="print(1)",
                    observation="one",
                    is_final=True,
                    output="finished",
                )

        context = AnalysisContext(
            task="分析销售趋势",
            request_id="analysis-request",
            modelCodeList=["sales-model"],
            schemas=[],
            queue=asyncio.Queue(),
        )
        agent = AutoAnalysisAgent(queue=context.queue)

        with (
            patch(
                "reactor_tool.tool.auto_analysis.create_agent",
                return_value=FakeAgent(),
            ),
            patch(
                "reactor_tool.tool.auto_analysis.get_prompt",
                return_value={"analysis_auto_prompt": "{{ schema }}"},
            ),
        ):
            result = await agent.analysis(context)

        self.assertEqual("finished", result["summary"])
        self.assertTrue(observed_threads)
        self.assertTrue(observed_threads[0].startswith("reactor-tool-blocking"))
        events = []
        while not agent.queue.empty():
            events.append(await agent.queue.get())
        self.assertTrue(any("分析步骤 1" in event.get("data", "") for event in events))

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
