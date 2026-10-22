# -*- coding: utf-8 -*-
"""api/tool 路由层契约回归。

这一组测试存在的理由：改动前 8 个 docgen 端点、2 个 file-tool dispatcher
是逐字复制的，只有一次全量跑过后才会发现某族的 payload 少了一个键，
或者某族把 400 写成了 200。这里把"路由集合 + 各类响应形状 + 状态码"
固化下来，让重构后的等价性可被验证。

补丁打在 ``service.RUNNERS`` 表里的 runner 上（路由按名字从该表取值），
所以这里必须把 runner 名与方法原型对齐。
"""
import unittest
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from cute_tool.api.tool import (
    _DOCGEN_TOOLS,
    _post_dataprep,
    _post_docread,
    _tool_params,
    router,
)
from cute_tool.model.protocal import DocgenRequest


def _build_client() -> TestClient:
    app = FastAPI()
    app.include_router(router)
    return TestClient(app)


class ToolRouterContractTest(unittest.TestCase):
    def test_all_tool_routes_are_registered(self):
        """路由集合必须与重构前一致（35 条）。"""
        paths = {route.path for route in router.routes}
        expected = {
            "/auto_analysis", "/cal_engine", "/chart_generator", "/checklist_generate",
            "/citation_extractor", "/code_interpreter", "/csv_processor",
            "/data_aggregate", "/data_clean", "/data_merge", "/data_transform",
            "/data_validate", "/deepsearch", "/document_generate", "/document_template",
            "/embedding/text", "/excel_generator", "/excel_reader", "/html_processor",
            "/image_ocr", "/markdown_processor", "/mragQuery", "/nl2sql", "/pdf_reader",
            "/pdf_structure", "/script_runner", "/slides_generate", "/sopRecall",
            "/sql_query", "/table_rag", "/template_filler", "/text_processor",
            "/theme_designer", "/web_fetch", "/word_reader",
        }
        self.assertEqual(paths, expected)

    def test_every_docgen_route_is_registered_in_table(self):
        """注册表没有登记的 tool_name 会 KeyError；这里确保 8 条路由都在表里。"""
        self.assertEqual(
            sorted(_DOCGEN_TOOLS),
            sorted(
                [
                    "document_generate", "slides_generate", "excel_generator",
                    "checklist_generate", "template_filler", "document_template",
                    "theme_designer", "chart_generator",
                ]
            ),
        )

    def test_tool_params_strips_transport_only_fields(self):
        rid, params = _tool_params(DocgenRequest(requestId="r1", format="pdf", extra="x"))
        self.assertEqual(rid, "r1")
        self.assertNotIn("extra", params)
        self.assertNotIn("request_id", params)
        self.assertEqual(params["format"], "pdf")

    def test_docgen_file_payload_shape_and_status(self):
        client = _build_client()
        payload = {
            "success": True,
            "message": "done",
            "output_path": "/tmp/x.pdf",
            "stats": {"blocks": 2},
            "warnings": [],
        }
        with patch(
            "cute_tool.tool.docgen.service.run_document_generate",
            new=AsyncMock(return_value=payload),
        ) as runner:
            resp = client.post("/document_generate", json={"requestId": "rid-doc"})
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["requestId"], "rid-doc")
        self.assertTrue(body["success"])
        self.assertEqual(body["outputPath"], "/tmp/x.pdf")
        self.assertEqual(body["stats"], {"blocks": 2})
        self.assertEqual(body["warnings"], [])
        self.assertEqual(body["fileInfo"], [])
        # runner 收到的第一个参数就是 request_id
        self.assertEqual(runner.call_args[0][0], "rid-doc")

    def test_docgen_meta_payload_passes_through_keys(self):
        client = _build_client()
        result = {"themes": ["a", "b"], "message": "", "warnings": []}
        with patch(
            "cute_tool.tool.docgen.service.run_theme_designer",
            new=AsyncMock(return_value=result),
        ):
            resp = client.post("/theme_designer", json={"requestId": "rid-t"})
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["themes"], ["a", "b"])
        # 没有产物时也要有 success/fileInfo，Java 侧按它们判活
        self.assertTrue(body["success"])
        self.assertEqual(body["fileInfo"], [])

    def test_docgen_error_returns_400_with_message(self):
        client = _build_client()
        with patch(
            "cute_tool.tool.docgen.service.run_slides_generate",
            new=AsyncMock(side_effect=RuntimeError("render boom")),
        ):
            resp = client.post("/slides_generate", json={"requestId": "rid-e"})
        self.assertEqual(resp.status_code, 400)
        self.assertEqual(resp.json()["message"], "render boom")

    def test_docread_failure_uses_400(self):
        client = _build_client()
        result = {"success": False, "message": "no such file", "data": None}
        with patch.dict(
            "cute_tool.tool.docread.service.RUNNERS",
            {"csv_processor": AsyncMock(return_value=result)},
        ):
            resp = client.post("/csv_processor", json={"requestId": "rid-c"})
        self.assertEqual(resp.status_code, 400)
        body = resp.json()
        self.assertEqual(body["success"], False)
        self.assertEqual(body["message"], "no such file")
        self.assertEqual(body["data"], None)
        self.assertEqual(body["requestId"], "rid-c")

    def test_docread_success_normalizes_file_info(self):
        client = _build_client()
        result = {
            "success": True,
            "data": {"rows": 1},
            "file_info": [
                {
                    "file_name": "out.csv",
                    "oss_url": "http://oss/out.csv",
                    "domain_url": "http://dom/out.csv",
                    "download_url": "http://dl/out.csv",
                    "file_size": 12,
                }
            ],
        }
        with patch.dict(
            "cute_tool.tool.docread.service.RUNNERS",
            {"csv_processor": AsyncMock(return_value=result)},
        ):
            resp = client.post("/csv_processor", json={"requestId": "rid-f"})
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(
            body["fileInfo"],
            [
                {
                    "fileName": "out.csv",
                    "ossUrl": "http://oss/out.csv",
                    "domainUrl": "http://dom/out.csv",
                    "downloadUrl": "http://dl/out.csv",
                    "fileSize": 12,
                }
            ],
        )
        self.assertEqual(body["data"], {"rows": 1})

    def test_dataprep_failure_uses_400_and_shares_payload_shape(self):
        client = _build_client()
        result = {"success": False, "message": "bad column", "data": None}
        with patch.dict(
            "cute_tool.tool.dataprep.service.RUNNERS",
            {"data_clean": AsyncMock(return_value=result)},
        ):
            resp = client.post("/data_clean", json={"requestId": "rid-d"})
        self.assertEqual(resp.status_code, 400)
        body = resp.json()
        self.assertEqual(body["message"], "bad column")
        self.assertEqual(set(body), {"requestId", "success", "message", "data", "fileInfo"})

    def test_unknown_tool_returns_404(self):
        """registry 里没有的 tool 必须 404，而不是 KeyError 变 500。"""
        import asyncio

        body = DocgenRequest(requestId="rid-404")
        for coro in (
            _post_docread("nope_tool", body),
            _post_dataprep("nope_tool", body),
        ):
            resp = asyncio.run(coro)
            self.assertEqual(resp.status_code, 404)


if __name__ == "__main__":
    unittest.main()
