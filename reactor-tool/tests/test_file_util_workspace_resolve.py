import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from reactor_tool.util.file_util import (
    download_all_files_in_path,
    get_file_path,
    _resolve_workspace_relative_file,
)


class FileUtilWorkspaceResolveTest(unittest.IsolatedAsyncioTestCase):
    async def test_bare_name_resolves_from_workspace_root(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            target = workspace / "run_backtest.py"
            target.write_text("print(1)\n", encoding="utf-8")
            input_dir = workspace / "input"
            input_dir.mkdir()

            resolved = await get_file_path(
                "run_backtest.py",
                word_dir=str(input_dir),
                workspace_root=str(workspace),
            )

            self.assertEqual(str(target.resolve()), str(Path(resolved).resolve()))

    async def test_bare_name_resolves_from_workspace_input(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            input_dir = workspace / "input"
            input_dir.mkdir()
            target = input_dir / "prices.csv"
            target.write_text("a,b\n1,2\n", encoding="utf-8")

            resolved = await get_file_path(
                "prices.csv",
                word_dir=str(input_dir),
                workspace_root=str(workspace),
            )

            self.assertEqual(str(target.resolve()), str(Path(resolved).resolve()))

    async def test_relative_path_under_workspace(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            data = workspace / "data"
            data.mkdir()
            target = data / "sp500.csv"
            target.write_text("Date,Close\n", encoding="utf-8")
            input_dir = workspace / "input"
            input_dir.mkdir()

            resolved = await get_file_path(
                "data/sp500.csv",
                word_dir=str(input_dir),
                workspace_root=str(workspace),
            )

            self.assertEqual(str(target.resolve()), str(Path(resolved).resolve()))

    async def test_path_traversal_rejected(self):
        with tempfile.TemporaryDirectory() as root:
            outer = Path(root)
            workspace = outer / "session"
            workspace.mkdir()
            secret = outer / "secret.txt"
            secret.write_text("nope", encoding="utf-8")
            input_dir = workspace / "input"
            input_dir.mkdir()

            hit = _resolve_workspace_relative_file(
                "../secret.txt",
                workspace_root=str(workspace),
                work_dir=str(input_dir),
            )
            self.assertIsNone(hit)

            resolved = await get_file_path(
                "../secret.txt",
                word_dir=str(input_dir),
                workspace_root=str(workspace),
            )
            self.assertIsNone(resolved)

    async def test_missing_bare_name_does_not_http_get(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            input_dir = workspace / "input"
            input_dir.mkdir()
            with patch("reactor_tool.util.file_util.aiohttp.ClientSession") as session_cls:
                resolved = await get_file_path(
                    "missing.py",
                    word_dir=str(input_dir),
                    workspace_root=str(workspace),
                )
                self.assertIsNone(resolved)
                session_cls.assert_not_called()

    async def test_download_all_files_maps_workspace_hit(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            target = workspace / "dual_ma_backtest.py"
            target.write_text("x=1\n", encoding="utf-8")
            input_dir = workspace / "input"
            input_dir.mkdir()

            rows = await download_all_files_in_path(
                ["dual_ma_backtest.py", "missing.py"],
                work_dir=str(input_dir),
                workspace_root=str(workspace),
            )

            self.assertEqual("dual_ma_backtest.py", rows[0]["file_name"])
            self.assertEqual(str(target.resolve()), str(Path(rows[0]["file_path"]).resolve()))
            self.assertEqual("missing.py", rows[1]["file_name"])
            self.assertEqual("", rows[1]["file_path"])

    async def test_http_url_still_downloads_to_work_dir(self):
        with tempfile.TemporaryDirectory() as root:
            workspace = Path(root)
            input_dir = workspace / "input"
            input_dir.mkdir()

            class FakeResponse:
                def raise_for_status(self):
                    return None

                @property
                def content(self):
                    return self

                async def read(self, _size):
                    if getattr(self, "_done", False):
                        return b""
                    self._done = True
                    return b"remote-bytes"

            class FakeGetCM:
                async def __aenter__(self):
                    return FakeResponse()

                async def __aexit__(self, *args):
                    return False

            class FakeSession:
                async def __aenter__(self):
                    return self

                async def __aexit__(self, *args):
                    return False

                def get(self, url, timeout=None):
                    self.url = url
                    return FakeGetCM()

            with patch("reactor_tool.util.file_util.aiohttp.ClientSession", return_value=FakeSession()):
                resolved = await get_file_path(
                    "https://example.com/files/data.csv",
                    word_dir=str(input_dir),
                    workspace_root=str(workspace),
                )

            self.assertEqual(str((input_dir / "data.csv").resolve()), str(Path(resolved).resolve()))
            self.assertEqual(b"remote-bytes", Path(resolved).read_bytes())


if __name__ == "__main__":
    unittest.main()
