import os
import unittest
from unittest.mock import AsyncMock, patch

from fastapi import FastAPI
from fastapi.testclient import TestClient

from reactor_tool.api.tool import router


class ImageGenerationApiTest(unittest.TestCase):
    def setUp(self):
        app = FastAPI()
        app.include_router(router, prefix="/v1/tool")
        self.client = TestClient(app)

    def test_should_normalize_relative_file_names_before_calling_service(self):
        service_mock = AsyncMock(
            return_value={
                "data": "图片生成完成",
                "fileInfo": [
                    {
                        "fileName": "结果图.png",
                        "ossUrl": "https://file.example.com/download/result.png",
                        "domainUrl": "https://file.example.com/preview/result.png",
                        "fileSize": 128,
                    }
                ],
                "requestId": "req-001",
            }
        )

        with patch.dict(os.environ, {"FILE_SERVER_URL": "http://127.0.0.1:1601/v1/file_tool"}):
            with patch("reactor_tool.tool.image_generation.generate_images", service_mock):
                response = self.client.post(
                    "/v1/tool/image_generation",
                    json={
                        "requestId": "req-001",
                        "prompt": "生成一张产品海报",
                        "mode": "edits",
                        "fileNames": ["origin.png"],
                        "maskFileNames": ["origin_mask.png"],
                        "stream": False,
                    },
                )

        self.assertEqual(200, response.status_code)
        body = response.json()
        self.assertEqual("图片生成完成", body["data"])

        called_request = service_mock.await_args.args[0]
        self.assertEqual(
            ["http://127.0.0.1:1601/v1/file_tool/preview/req-001/origin.png"],
            called_request.file_names,
        )
        self.assertEqual(
            ["http://127.0.0.1:1601/v1/file_tool/preview/req-001/origin_mask.png"],
            called_request.mask_file_names,
        )

    def test_should_stream_final_file_info(self):
        service_mock = AsyncMock(
            return_value={
                "data": "图片生成完成",
                "fileInfo": [
                    {
                        "fileName": "结果图.png",
                        "ossUrl": "https://file.example.com/download/result.png",
                        "domainUrl": "https://file.example.com/preview/result.png",
                        "fileSize": 128,
                    }
                ],
                "requestId": "req-002",
            }
        )

        with patch("reactor_tool.tool.image_generation.generate_images", service_mock):
            with self.client.stream(
                "POST",
                "/v1/tool/image_generation",
                json={
                    "requestId": "req-002",
                    "prompt": "生成一张蓝色海报",
                    "stream": True,
                },
            ) as response:
                lines = [line for line in response.iter_lines() if line]

        self.assertEqual(200, response.status_code)
        self.assertTrue(any("开始执行图片生成任务" in line for line in lines))
        self.assertTrue(any("结果图.png" in line for line in lines))
        self.assertTrue(any("[DONE]" in line for line in lines))


if __name__ == "__main__":
    unittest.main()
