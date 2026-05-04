import os
import tempfile
import types
import unittest
from unittest.mock import patch

from PIL import Image, ImageDraw

from reactor_tool.tool.mrag.generation.vlm import VLLMClient
from reactor_tool.tool.mrag.utils.caption_utils import generate_caption
from reactor_tool.tool.mrag.utils.ocr_utils import VLMOCR, get_ocr_model


class _FakeOpenAIClient:
    def __init__(self, init_kwargs: dict):
        self.init_kwargs = init_kwargs
        self.requests = []
        self.chat = types.SimpleNamespace(
            completions=types.SimpleNamespace(create=self._create)
        )

    def _create(self, **kwargs):
        self.requests.append(kwargs)
        return types.SimpleNamespace(
            choices=[
                types.SimpleNamespace(
                    message=types.SimpleNamespace(content="fake-vlm-response")
                )
            ]
        )


class VLMClientConfigTest(unittest.TestCase):

    def _patch_openai_client(self):
        captured = {}

        def _factory(**kwargs):
            client = _FakeOpenAIClient(kwargs)
            captured["client"] = client
            return client

        return captured, _factory

    def _create_test_image(self) -> str:
        temp_dir = tempfile.mkdtemp(prefix="mrag-vlm-test-")
        image_path = os.path.join(temp_dir, "sample.png")
        image = Image.new("RGB", (320, 160), color="white")
        drawer = ImageDraw.Draw(image)
        drawer.text((24, 60), "OPENSPEC TEST", fill="black")
        image.save(image_path)
        return image_path

    def test_should_read_vlm_env_and_normalize_base_url(self):
        captured, factory = self._patch_openai_client()

        with patch.dict(
            os.environ,
            {
                "VLM_MODEL_BASE_URL": "https://www.openclaudecode.cn/v1/chat/completions",
                "VLM_MODEL_NAME": "gpt-5.2",
                "VLM_API_KEY": "test-vlm-key",
            },
            clear=False,
        ):
            with patch("reactor_tool.tool.mrag.generation.vlm.OpenAI", side_effect=factory):
                client = VLLMClient()

        self.assertEqual("gpt-5.2", client.model_name)
        self.assertEqual("https://www.openclaudecode.cn/v1", client.model_base_url)
        self.assertEqual("test-vlm-key", captured["client"].init_kwargs["api_key"])
        self.assertEqual("https://www.openclaudecode.cn/v1", captured["client"].init_kwargs["base_url"])

    def test_should_use_vlm_env_model_when_generating_caption(self):
        image_path = self._create_test_image()
        captured, factory = self._patch_openai_client()

        with patch.dict(
            os.environ,
            {
                "VLM_MODEL_BASE_URL": "https://www.openclaudecode.cn/v1/chat/completions",
                "VLM_MODEL_NAME": "gpt-5.2",
                "VLM_API_KEY": "test-vlm-key",
            },
            clear=False,
        ):
            with patch("reactor_tool.tool.mrag.generation.vlm.OpenAI", side_effect=factory):
                result = generate_caption(image_path)

        self.assertEqual("fake-vlm-response", result)
        request = captured["client"].requests[0]
        self.assertEqual("gpt-5.2", request["model"])
        self.assertEqual(500, request["max_tokens"])
        self.assertFalse(request["stream"])
        self.assertEqual("描述图片的内容，不要超过100个字", request["messages"][0]["content"][0]["text"])
        self.assertTrue(request["messages"][0]["content"][1]["image_url"]["url"].startswith("data:image/png;base64,"))

    def test_should_use_vlm_ocr_when_ocr_type_is_vlm_ocr(self):
        image_path = self._create_test_image()
        captured, factory = self._patch_openai_client()

        with patch.dict(
            os.environ,
            {
                "OCR_TYPE": "vlm-ocr",
                "VLM_MODEL_BASE_URL": "https://www.openclaudecode.cn/v1/chat/completions",
                "VLM_MODEL_NAME": "gpt-5.2",
                "VLM_API_KEY": "test-vlm-key",
            },
            clear=False,
        ):
            with patch("reactor_tool.tool.mrag.generation.vlm.OpenAI", side_effect=factory):
                ocr_model = get_ocr_model()
                result = ocr_model.ocr(image_path)

        self.assertIsInstance(ocr_model, VLMOCR)
        self.assertEqual("fake-vlm-response", result)
        request = captured["client"].requests[0]
        self.assertEqual("gpt-5.2", request["model"])
        self.assertEqual(1024, request["max_tokens"])
        self.assertEqual("提取图片中的文字", request["messages"][0]["content"][0]["text"])


class VLMEnvSmokeTest(unittest.TestCase):

    @unittest.skipUnless(
        os.getenv("RUN_MRAG_VLM_SMOKE_TEST") == "1",
        "默认跳过真实 VLM 联调测试，设置 RUN_MRAG_VLM_SMOKE_TEST=1 后执行。",
    )
    def test_should_call_real_vlm_with_current_env(self):
        temp_dir = tempfile.mkdtemp(prefix="mrag-vlm-smoke-")
        image_path = os.path.join(temp_dir, "smoke.png")
        image = Image.new("RGB", (320, 160), color="white")
        drawer = ImageDraw.Draw(image)
        drawer.text((24, 60), "OPENSPEC TEST", fill="black")
        image.save(image_path)

        caption = generate_caption(image_path)
        self.assertTrue(caption)
        self.assertTrue(caption.strip())


if __name__ == "__main__":
    unittest.main()
