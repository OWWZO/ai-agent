# -*- coding: utf-8 -*-
"""docread/_service_common 共享协议层回归。

固化"单文件上传失败不阻塞其余、errors 仅 warn 模式产出"的契约。
"""
import asyncio
import os
import tempfile
import unittest
from unittest.mock import patch

from reactor_tool.tool.docread._service_common import (
    MAX_JSON_CHARS,
    fail_payload,
    ok_payload,
    strip_internal,
    truncate_payload,
    workspace_root,
)
from reactor_tool.util.artifacts import upload_artifacts


class DocreadServiceCommonTest(unittest.TestCase):
    def test_workspace_root_accepts_snake_and_camel(self):
        # 与原 dataprep/docread._workspace 行为逐字一致：空白输入取到空串而非 None，
        # 调用方把它继续当"无 workspace"用；这里固化这个事实，避免迁移时改成 None
        # 让下游 resolve 路径产生差异。
        self.assertEqual(workspace_root({"workspace_root": "  "}), "")
        self.assertEqual(workspace_root({"workspace_root": "/a"}), "/a")
        self.assertEqual(workspace_root({"workspaceRoot": " /b "}), "/b")
        self.assertIsNone(workspace_root({}))

    def test_strip_internal_drops_transport_only_keys(self):
        out = strip_internal(
            {
                "request_id": "r1",
                "requestId": "r1",
                "workspace_root": "/w",
                "workspaceRoot": "/w",
                "file_path": "a.csv",
            }
        )
        self.assertEqual(out, {"file_path": "a.csv"})

    def test_truncate_payload_passes_small_data_and_truncates_large(self):
        self.assertEqual(truncate_payload({"a": 1}), {"a": 1})
        blob = "x" * (MAX_JSON_CHARS + 10)
        out = truncate_payload({"blob": blob})
        self.assertTrue(out["truncated"])
        # original_chars 统计整个 payload 序列化后的长度；preview 取序列化结果的
        # 前 limit 个字符并追加尾注 —— 这里只固化这三点，不硬编码 blob 边界。
        expected_total = len('{"blob": "' + blob + '"}')
        self.assertEqual(out["original_chars"], expected_total)
        self.assertTrue(out["preview"].endswith(f"...[truncated {expected_total - MAX_JSON_CHARS} chars]"))
        self.assertEqual(
            out["preview"][:-len(f"...[truncated {expected_total - MAX_JSON_CHARS} chars]")],
            '{"blob": "' + blob[: MAX_JSON_CHARS - len('{"blob": "')],
        )

    def test_ok_and_fail_payload_shapes(self):
        ok = ok_payload([1, 2])
        self.assertEqual(ok["success"], True)
        self.assertEqual(ok["message"], "ok")
        self.assertEqual(ok["data"], [1, 2])

        fail = fail_payload("boom")
        self.assertEqual(fail, {"success": False, "message": "boom", "data": None})


class UploadArtifactsTest(unittest.TestCase):
    def test_swallow_mode_collects_errors_only_when_warn(self):
        """单个文件上传失败不能影响其余文件，也不该默认污染 errors。"""
        with tempfile.TemporaryDirectory() as tmp:
            good = os.path.join(tmp, "good.txt")
            with open(good, "w", encoding="utf-8") as handle:
                handle.write("ok")

            async def fake_upload(file_path, request_id):
                if "bad" in file_path:
                    raise RuntimeError("503 upstream")
                return {"fileName": os.path.basename(file_path), "requestId": request_id}

            with patch(
                "reactor_tool.util.artifacts.upload_file_by_path",
                side_effect=fake_upload,
            ):
                file_info, errors = asyncio.run(
                    upload_artifacts(
                        [good, os.path.join(tmp, "bad.txt")], "rid-1", on_error="swallow"
                    )
                )
                self.assertEqual(len(file_info), 1)
                self.assertEqual(errors, [])

                file_info, errors = asyncio.run(
                    upload_artifacts(
                        [good, os.path.join(tmp, "bad.txt")], "rid-1", on_error="warn"
                    )
                )
                self.assertEqual(len(file_info), 1)
                # 与 script_runner 原有的 stderr 提示格式一致："<文件名>: <错误>"
                self.assertEqual(errors, ["bad.txt: 503 upstream"])

    def test_missing_files_and_empty_results_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            missing = os.path.join(tmp, "nope.txt")

            async def fake_upload(file_path, request_id):
                return None

            with patch(
                "reactor_tool.util.artifacts.upload_file_by_path",
                side_effect=fake_upload,
            ):
                file_info, errors = asyncio.run(
                    upload_artifacts([missing, ""], "rid-2")
                )
                self.assertEqual(file_info, [])
                self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
