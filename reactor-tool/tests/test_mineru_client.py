# -*- coding: utf-8 -*-
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, mock_open, patch


from reactor_tool.tool.mrag.document.mineru_client import MinerUClient


class MinerUClientTest(unittest.TestCase):

    def test_should_prepare_signed_upload_target_for_single_file(self):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "code": 0,
            "data": {
                "batch_id": "batch-1",
                "file_urls": ["https://upload.example.com/file-1"],
            },
        }

        with patch.dict(
                os.environ,
                {
                    "MINERU_API_KEY": "token",
                    "MINERU_API_BASE_URL": "https://mineru.net/api/v4",
                },
                clear=False,
        ):
            with patch("reactor_tool.tool.mrag.document.mineru_client.requests.post", return_value=mock_response) as mock_post:
                client = MinerUClient()

                target = client.prepare_file_upload(
                    file_name="sample.pdf",
                    data_id="data-1",
                    model_version="vlm",
                )

        self.assertEqual("batch-1", target.batch_id)
        self.assertEqual("https://upload.example.com/file-1", target.upload_url)
        self.assertEqual("data-1", target.data_id)
        request_body = mock_post.call_args.kwargs["json"]
        self.assertEqual("vlm", request_body["model_version"])
        self.assertEqual(
            [{"name": "sample.pdf", "data_id": "data-1"}],
            request_body["files"],
        )

    def test_should_upload_file_to_signed_url(self):
        with tempfile.TemporaryDirectory(prefix="mineru-upload-") as temp_dir:
            file_path = Path(temp_dir) / "sample.pdf"
            file_path.write_bytes(b"pdf-bytes")

            mock_response = MagicMock()
            mock_response.status_code = 200

            with patch.dict(
                    os.environ,
                    {
                        "MINERU_API_KEY": "token",
                        "MINERU_API_BASE_URL": "https://mineru.net/api/v4",
                    },
                    clear=False,
            ):
                with patch("reactor_tool.tool.mrag.document.mineru_client.requests.put", return_value=mock_response) as mock_put:
                    client = MinerUClient()
                    client.upload_file("https://upload.example.com/file-1", str(file_path))

        self.assertEqual("https://upload.example.com/file-1", mock_put.call_args.args[0])

    def test_should_wait_batch_result_and_pick_matching_data_id(self):
        mock_response = MagicMock()
        mock_response.status_code = 200
        mock_response.json.return_value = {
            "code": 0,
            "data": {
                "extract_result": [
                    {
                        "file_name": "sample.pdf",
                        "data_id": "data-1",
                        "state": "done",
                        "full_zip_url": "https://download.example.com/result.zip",
                    }
                ]
            },
        }

        with patch.dict(
                os.environ,
                {
                    "MINERU_API_KEY": "token",
                    "MINERU_API_BASE_URL": "https://mineru.net/api/v4",
                },
                clear=False,
        ):
            with patch("reactor_tool.tool.mrag.document.mineru_client.requests.get", return_value=mock_response):
                client = MinerUClient()
                result_url = client.wait_batch_result(
                    batch_id="batch-1",
                    file_name="sample.pdf",
                    data_id="data-1",
                    timeout_seconds=1,
                    poll_interval_seconds=0,
                )

        self.assertEqual("https://download.example.com/result.zip", result_url)


if __name__ == "__main__":
    unittest.main()
