# -*- coding: utf-8 -*-
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from reactor_tool.tool.mrag.utils.ocr_utils import get_ocr_model


class _FakeResponse:
    def __init__(self, status_code=200, json_data=None, text=""):
        self.status_code = status_code
        self._json_data = json_data or {}
        self.text = text
        self.content = text.encode("utf-8")

    def json(self):
        return self._json_data

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"http status error: {self.status_code}")


class PaddleOCRProviderTest(unittest.TestCase):

    def _create_test_image(self) -> str:
        temp_dir = tempfile.mkdtemp(prefix="mrag-paddle-ocr-")
        image_path = os.path.join(temp_dir, "sample.png")
        Path(image_path).write_bytes(b"fake-image-bytes")
        return image_path

    def test_should_use_paddle_ocr_provider_and_extract_markdown_text(self):
        image_path = self._create_test_image()
        jsonl_url = "https://paddle.example.com/result/job-1.jsonl"
        status_responses = [
            _FakeResponse(
                json_data={
                    "data": {
                        "state": "running",
                        "extractProgress": {"totalPages": 2, "extractedPages": 1},
                    }
                }
            ),
            _FakeResponse(
                json_data={
                    "data": {
                        "state": "done",
                        "extractProgress": {"extractedPages": 2},
                        "resultUrl": {"jsonUrl": jsonl_url},
                    }
                }
            ),
        ]

        def fake_get(url, headers=None, timeout=None):
            if url == "https://paddle.example.com/api/v2/ocr/jobs/job-1":
                return status_responses.pop(0)
            if url == jsonl_url:
                return _FakeResponse(
                    text="\n".join(
                        [
                            json.dumps(
                                {
                                    "result": {
                                        "layoutParsingResults": [
                                            {"markdown": {"text": "第一页标题"}}
                                        ]
                                    }
                                },
                                ensure_ascii=False,
                            ),
                            json.dumps(
                                {
                                    "result": {
                                        "layoutParsingResults": [
                                            {"markdown": {"text": "第二页正文"}}
                                        ]
                                    }
                                },
                                ensure_ascii=False,
                            ),
                        ]
                    )
                )
            raise AssertionError(f"unexpected GET url: {url}")

        with patch.dict(
            os.environ,
            {
                "OCR_TYPE": "paddleocr-vl",
                "PADDLE_OCR_JOB_URL": "https://paddle.example.com/api/v2/ocr/jobs",
                "PADDLE_OCR_TOKEN": "test-token",
                "PADDLE_OCR_MODEL_NAME": "PaddleOCR-VL-1.6",
                "PADDLE_OCR_POLL_INTERVAL_SECONDS": "0",
                "PADDLE_OCR_TIMEOUT_SECONDS": "60",
            },
            clear=False,
        ):
            with patch("requests.post", return_value=_FakeResponse(json_data={"data": {"jobId": "job-1"}})) as mock_post:
                with patch("requests.get", side_effect=fake_get):
                    ocr_model = get_ocr_model()
                    result = ocr_model.ocr(image_path)

        self.assertEqual("PaddleOCRVLOCR", ocr_model.__class__.__name__)
        self.assertEqual("第一页标题\n\n第二页正文", result)
        _, kwargs = mock_post.call_args
        self.assertEqual("bearer test-token", kwargs["headers"]["Authorization"])
        self.assertEqual("PaddleOCR-VL-1.6", kwargs["data"]["model"])
        self.assertEqual(
            {
                "useDocOrientationClassify": False,
                "useDocUnwarping": False,
                "useChartRecognition": False,
            },
            json.loads(kwargs["data"]["optionalPayload"]),
        )
        self.assertIn("file", kwargs["files"])

    def test_should_return_empty_string_when_paddle_ocr_job_failed(self):
        image_path = self._create_test_image()

        with patch.dict(
            os.environ,
            {
                "OCR_TYPE": "paddleocr-vl",
                "PADDLE_OCR_JOB_URL": "https://paddle.example.com/api/v2/ocr/jobs",
                "PADDLE_OCR_TOKEN": "test-token",
                "PADDLE_OCR_MODEL_NAME": "PaddleOCR-VL-1.6",
                "PADDLE_OCR_POLL_INTERVAL_SECONDS": "0",
                "PADDLE_OCR_TIMEOUT_SECONDS": "60",
            },
            clear=False,
        ):
            with patch("requests.post", return_value=_FakeResponse(json_data={"data": {"jobId": "job-1"}})):
                with patch(
                    "requests.get",
                    return_value=_FakeResponse(
                        json_data={"data": {"state": "failed", "errorMsg": "quota exceeded"}}
                    ),
                ):
                    with patch("reactor_tool.tool.mrag.utils.ocr_utils.logger.error"):
                        result = get_ocr_model().ocr(image_path)

        self.assertEqual("", result)


if __name__ == "__main__":
    unittest.main()
