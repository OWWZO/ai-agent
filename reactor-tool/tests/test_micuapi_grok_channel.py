# -*- coding: utf-8 -*-
"""
验证 micuapi 上 grok-4.5 是否可用，以及 503 model_not_found 是否会触发本地模型降级。

运行：
  cd reactor-tool
  uv run python -m unittest tests.test_micuapi_grok_channel -v
"""
from __future__ import annotations

import json
import os
import unittest
from pathlib import Path
from unittest.mock import patch

import httpx
from dotenv import load_dotenv

from reactor_tool.util.llm_util import (
    _is_permission_or_policy_block_error,
    _prepare_litellm_params,
    ask_llm,
)


ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env", override=False)


def _mask_key(key: str) -> str:
    text = (key or "").strip()
    if len(text) <= 12:
        return "***"
    return f"{text[:6]}...{text[-4:]}"


class ModelNotFoundFallbackGuardTest(unittest.IsolatedAsyncioTestCase):
    """确认 503 model_not_found 不会触发换模型 fallback。"""

    async def test_model_not_found_does_not_switch_to_fallback_model(self):
        call_models: list[str] = []

        async def fake_raw(*args, **kwargs):
            params = kwargs.get("params") or {}
            call_models.append(str(params.get("model")))
            raise RuntimeError(
                'raw_openai_like status=503, body={"error":{"code":"model_not_found",'
                '"message":"No available channel for model grok-4.5 under group vip_2_image '
                '(distributor)","type":"new_api_error"}}'
            )
            if False:  # pragma: no cover - keep async generator shape
                yield ""

        async def fake_acompletion(*args, **kwargs):
            raise AssertionError("LiteLLM primary path should not be used when raw HTTP raises")

        with patch.dict(
            os.environ,
            {
                "OPENAI_BASE_URL": "https://www.micuapi.ai/v1/chat/completions",
                "OPENAI_API_KEY": "test-key",
                "OPENAI_COMPAT_ALLOW_LITELLM_FALLBACK": "false",
                "OPENAI_COMPAT_FALLBACK_MODEL": "gpt-4",
                "OPENAI_FALLBACK_MODEL": "gpt-4",
            },
            clear=False,
        ), patch(
            "reactor_tool.util.llm_util._raw_openai_like_request",
            new=fake_raw,
        ), patch(
            "reactor_tool.util.llm_util.acompletion",
            new=fake_acompletion,
        ):
            with self.assertRaises(RuntimeError) as ctx:
                async for _ in ask_llm(
                    messages="hi",
                    model="grok-4.5",
                    stream=False,
                    only_content=True,
                ):
                    pass

        self.assertIn("model_not_found", str(ctx.exception))
        self.assertFalse(_is_permission_or_policy_block_error(ctx.exception))
        # 重试 2 次，模型名始终是 grok-4.5，不会被换成 gpt-4
        self.assertEqual(call_models, ["grok-4.5", "grok-4.5"])

    def test_prepare_params_uses_openai_api_key_for_grok(self):
        with patch.dict(
            os.environ,
            {
                "OPENAI_BASE_URL": "https://www.micuapi.ai/v1/chat/completions",
                "OPENAI_API_KEY": "sk-report-text-key",
                "IMAGE_GENERATION_API_KEY": "sk-image-key",
                "DASHSCOPE_API_KEY": "sk-dashscope-key",
            },
            clear=False,
        ):
            params = _prepare_litellm_params("grok-4.5")

        self.assertEqual(params["model"], "grok-4.5")
        self.assertEqual(params["api_key"], "sk-report-text-key")
        self.assertEqual(params["custom_llm_provider"], "openai_like")
        self.assertIn("micuapi.ai", params["api_base"])


@unittest.skipUnless(
    os.getenv("RUN_LIVE_MICUAPI_TEST", "").strip().lower() in {"1", "true", "yes"}
    or bool((os.getenv("OPENAI_API_KEY") or "").strip()),
    "需要 OPENAI_API_KEY；默认有 key 即跑 live 探测",
)
class MicuapiGrokLiveProbeTest(unittest.TestCase):
    """
    用当前 .env 的 OPENAI_API_KEY 直连 micuapi，验证 grok-4.5 渠道。
    不经过 ask_llm 的重试/降级逻辑，直接看网关原始响应。
    """

    def test_live_micuapi_grok_channel(self):
        api_key = (os.getenv("OPENAI_API_KEY") or "").strip()
        api_base = (
            os.getenv("OPENAI_BASE_URL")
            or os.getenv("OPENAI_API_BASE")
            or "https://www.micuapi.ai/v1/chat/completions"
        ).strip()
        model = (os.getenv("REPORT_MODEL") or os.getenv("DEFAULT_MODEL") or "grok-4.5").strip()

        self.assertTrue(api_key, "OPENAI_API_KEY 为空")

        # 归一化到 chat/completions
        base = api_base.rstrip("/")
        if base.endswith("/chat/completions"):
            url = base
        elif base.endswith("/v1"):
            url = f"{base}/chat/completions"
        else:
            url = f"{base}/v1/chat/completions" if not base.endswith("/v1/chat/completions") else base

        payload = {
            "model": model,
            "stream": False,
            "messages": [{"role": "user", "content": "只回复 ok"}],
            "max_tokens": 16,
        }
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        }

        print("\n=== Micuapi live probe ===")
        print(f"url={url}")
        print(f"model={model}")
        print(f"key={_mask_key(api_key)}")

        with httpx.Client(timeout=60.0) as client:
            resp = client.post(url, headers=headers, json=payload)

        body_text = resp.text
        try:
            body = resp.json()
        except Exception:
            body = {"raw": body_text[:800]}

        print(f"status={resp.status_code}")
        print(f"body={json.dumps(body, ensure_ascii=False)[:800]}")

        if resp.status_code >= 400:
            err = body.get("error") if isinstance(body, dict) else None
            message = ""
            if isinstance(err, dict):
                message = str(err.get("message") or "")
            message = message or body_text

            # 明确诊断：分组无渠道 vs 别的错误
            if "vip_2_image" in message or "No available channel" in message:
                self.fail(
                    "网关拒绝：当前 OPENAI_API_KEY 所在分组没有该模型渠道"
                    f"（不是本地降级逻辑）。detail={message}"
                )
            self.fail(
                f"micuapi 调用失败 status={resp.status_code}, body={body_text[:500]}"
            )

        # 成功则至少有 choices
        choices = body.get("choices") if isinstance(body, dict) else None
        self.assertTrue(choices, f"成功响应但缺少 choices: {body}")
        print("RESULT=OK 当前 key 可以访问该模型，问题不在 key/分组渠道。")


@unittest.skipUnless(
    os.getenv("RUN_LIVE_MICUAPI_TEST", "").strip().lower() in {"1", "true", "yes"}
    or bool((os.getenv("OPENAI_API_KEY") or "").strip()),
    "需要 OPENAI_API_KEY",
)
class MicuapiGrokAskLlmLiveTest(unittest.IsolatedAsyncioTestCase):
    """走完整 ask_llm 路径，观察是否仍是 grok-4.5 且无换模。"""

    async def test_live_ask_llm_report_model(self):
        model = (os.getenv("REPORT_MODEL") or "grok-4.5").strip()
        chunks: list[str] = []
        try:
            async for piece in ask_llm(
                messages="只回复 ok",
                model=model,
                stream=True,
                only_content=True,
                max_tokens=16,
            ):
                if isinstance(piece, str) and piece:
                    chunks.append(piece)
        except Exception as exc:
            text = str(exc)
            print(f"\nask_llm failed: {text[:600]}")
            self.assertIn("grok-4.5", model)
            # 若失败，断言不是“换模型后”的错误文案特征
            self.assertNotIn("retrying with fallback model", text)
            if "vip_2_image" in text or "No available channel" in text:
                self.fail(
                    "ask_llm 直连网关失败：Key 分组无 grok-4.5 渠道（未触发本地模型降级）。"
                    f" error={text[:400]}"
                )
            raise

        content = "".join(chunks)
        print(f"\nask_llm content={content!r}")
        self.assertTrue(content.strip(), "ask_llm 成功但内容为空")


if __name__ == "__main__":
    unittest.main()
