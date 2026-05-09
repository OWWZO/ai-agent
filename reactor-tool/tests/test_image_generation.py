import asyncio
import unittest
from unittest.mock import patch

import httpx

from reactor_tool.tool.image_generation import (
    DEFAULT_IMAGE_MODEL,
    _build_generation_requests,
    _execute_generation_request,
    _resolve_api_key,
    _resolve_base_url,
    _resolve_model_name,
    extract_generated_images,
    generate_images,
    resolve_generation_mode,
)
from reactor_tool.model.protocal import ImageGenerationRequest


TINY_PNG_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z5x8AAAAASUVORK5CYII="
)


class ImageGenerationToolTest(unittest.TestCase):
    def test_should_extract_images_from_responses_output(self):
        payload = {
            "output": [
                {
                    "type": "image_generation_call",
                    "result": TINY_PNG_BASE64,
                }
            ]
        }

        images = extract_generated_images(payload)

        self.assertEqual(1, len(images))
        self.assertTrue(images[0].data_url.startswith("data:image/png;base64,"))

    def test_should_extract_images_from_legacy_image_api(self):
        payload = {
            "data": [
                {
                    "b64_json": TINY_PNG_BASE64,
                },
                {
                    "url": "https://example.com/generated.png",
                },
            ]
        }

        images = extract_generated_images(payload)

        self.assertEqual(2, len(images))
        self.assertTrue(images[0].data_url.startswith("data:image/png;base64,"))
        self.assertEqual("https://example.com/generated.png", images[1].url)

    def test_should_auto_resolve_edit_mode_when_files_exist(self):
        request = ImageGenerationRequest.model_validate(
            {
                "requestId": "req-001",
                "prompt": "把图片里的天空改成晚霞",
                "fileNames": ["dog.png"],
            }
        )

        self.assertEqual("edits", resolve_generation_mode(request))

    def test_should_only_read_dedicated_image_generation_env(self):
        with patch.dict(
            "os.environ",
            {
                "IMAGE_GENERATION_BASE_URL": "https://image.example.com",
                "IMAGE_GENERATION_API_KEY": "image-key",
                "IMAGE_GENERATION_MODEL": "image-model",
                "OPENAI_BASE_URL": "https://openai.example.com",
                "OPENAI_API_BASE": "https://openai-api-base.example.com",
                "OPENAI_API_KEY": "openai-key",
            },
            clear=True,
        ):
            self.assertEqual("https://image.example.com", _resolve_base_url())
            self.assertEqual("image-key", _resolve_api_key())
            self.assertEqual("image-model", _resolve_model_name())

    def test_should_not_fallback_to_openai_env(self):
        with patch.dict(
            "os.environ",
            {
                "OPENAI_BASE_URL": "https://openai.example.com",
                "OPENAI_API_BASE": "https://openai-api-base.example.com",
                "OPENAI_API_KEY": "openai-key",
            },
            clear=True,
        ):
            self.assertEqual("", _resolve_base_url())
            self.assertEqual("", _resolve_api_key())
            self.assertEqual(DEFAULT_IMAGE_MODEL, _resolve_model_name())

    def test_should_raise_actionable_error_when_image_generation_env_missing(self):
        request = ImageGenerationRequest.model_validate(
            {
                "requestId": "req-002",
                "prompt": "生成一张橘猫照片",
            }
        )

        with patch.dict("os.environ", {}, clear=True):
            with self.assertRaisesRegex(
                ValueError,
                "IMAGE_GENERATION_BASE_URL",
            ):
                asyncio.run(generate_images(request))

    def test_should_build_native_edits_request_for_single_edit_without_mask(self):
        data_url = f"data:image/png;base64,{TINY_PNG_BASE64}"
        request = ImageGenerationRequest.model_validate(
            {
                "requestId": "req-edits-001",
                "prompt": "把图片改成黄昏氛围",
                "mode": "edits",
                "fileNames": [data_url],
                "size": "1024x1024",
            }
        )

        async def _run():
            async with httpx.AsyncClient() as client:
                primary, fallback = await _build_generation_requests(
                    request=request,
                    mode="edits",
                    base_url="https://example.com/v1",
                    model_name="gpt-image-2",
                    client=client,
                )
                self.assertEqual("https://example.com/v1/images/edits", primary["url"])
                self.assertTrue(primary.get("multipart"))
                self.assertEqual(("model", (None, "gpt-image-2")), primary["body"][0])
                self.assertEqual(("prompt", (None, "把图片改成黄昏氛围")), primary["body"][1])
                self.assertEqual("https://example.com/v1/chat/completions", fallback["url"])

        asyncio.run(_run())

    def test_should_attach_alpha_mask_when_single_edit_contains_mask(self):
        data_url = f"data:image/png;base64,{TINY_PNG_BASE64}"
        request = ImageGenerationRequest.model_validate(
            {
                "requestId": "req-edits-002",
                "prompt": "只修改红色标记区域",
                "mode": "edits",
                "fileNames": [data_url],
                "maskFileNames": [data_url],
            }
        )

        async def _run():
            async with httpx.AsyncClient() as client:
                primary, fallback = await _build_generation_requests(
                    request=request,
                    mode="edits",
                    base_url="https://example.com/v1",
                    model_name="gpt-image-2",
                    client=client,
                )
                self.assertEqual("https://example.com/v1/images/edits", primary["url"])
                self.assertTrue(primary.get("multipart"))
                self.assertEqual("https://example.com/v1/chat/completions", fallback["url"])

        asyncio.run(_run())

    def test_should_post_multipart_request_when_primary_request_requires_native_edits(self):
        class FakeResponse:
            def __init__(self):
                self.is_success = True
                self.status_code = 200
                self.text = '{"data":[{"b64_json":"' + TINY_PNG_BASE64 + '"}]}'

            def json(self):
                return {"data": [{"b64_json": TINY_PNG_BASE64}]}

        class FakeClient:
            def __init__(self):
                self.calls = []

            async def post(self, url, headers=None, json=None, files=None):
                self.calls.append(
                    {
                        "url": url,
                        "headers": headers or {},
                        "json": json,
                        "files": files,
                    }
                )
                return FakeResponse()

        async def _run():
            client = FakeClient()
            payload, used_fallback = await _execute_generation_request(
                client=client,
                api_key="test-key",
                primary_request={
                    "url": "https://example.com/v1/images/edits",
                    "body": [("model", "gpt-image-2"), ("prompt", "edit")],
                    "multipart": True,
                },
                fallback_request=None,
            )
            self.assertFalse(used_fallback)
            self.assertEqual(TINY_PNG_BASE64, payload["data"][0]["b64_json"])
            self.assertEqual("https://example.com/v1/images/edits", client.calls[0]["url"])
            self.assertIsNone(client.calls[0]["json"])
            self.assertIsNotNone(client.calls[0]["files"])
            self.assertNotIn("Content-Type", client.calls[0]["headers"])

        asyncio.run(_run())


if __name__ == "__main__":
    unittest.main()
