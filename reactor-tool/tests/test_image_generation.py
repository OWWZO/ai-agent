import asyncio
import unittest
from unittest.mock import patch

from reactor_tool.tool.image_generation import (
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
            self.assertEqual("", _resolve_model_name())

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


if __name__ == "__main__":
    unittest.main()
