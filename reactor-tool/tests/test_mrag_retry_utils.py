import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.mrag.utils.retry_utils import (
    call_with_retry,
    is_transient_error,
    stream_with_retry,
)


class MragRetryUtilsTest(unittest.TestCase):

    def test_should_detect_upstream_request_failed_as_transient(self):
        self.assertTrue(is_transient_error(RuntimeError("Upstream request failed")))
        self.assertTrue(is_transient_error(TimeoutError("request timed out")))
        self.assertFalse(is_transient_error(ValueError("invalid json payload")))

    def test_should_retry_tls_handshake_interrupt_but_not_cert_errors(self):
        import ssl

        self.assertTrue(is_transient_error(ssl.SSLError("TLS/SSL connection has been closed (EOF)")))
        self.assertTrue(is_transient_error(RuntimeError("SSL handshake failed: unexpected eof")))
        self.assertFalse(
            is_transient_error(ssl.SSLCertVerificationError("certificate verify failed: self signed certificate"))
        )

    def test_call_with_retry_should_retry_transient_errors(self):
        attempts = {"count": 0}

        def _flaky():
            attempts["count"] += 1
            if attempts["count"] < 3:
                raise RuntimeError("Upstream request failed")
            return "ok"

        with patch.dict(os.environ, {"MRAG_LLM_MAX_RETRIES": "2", "MRAG_LLM_RETRY_BASE_DELAY": "0"}, clear=False):
            result = call_with_retry(_flaky, label="test-call")

        self.assertEqual("ok", result)
        self.assertEqual(3, attempts["count"])

    def test_call_with_retry_should_not_retry_non_transient_errors(self):
        attempts = {"count": 0}

        def _permanent():
            attempts["count"] += 1
            raise ValueError("bad request")

        with patch.dict(os.environ, {"MRAG_LLM_MAX_RETRIES": "3", "MRAG_LLM_RETRY_BASE_DELAY": "0"}, clear=False):
            with self.assertRaises(ValueError):
                call_with_retry(_permanent, label="test-call")

        self.assertEqual(1, attempts["count"])

    def test_stream_with_retry_should_retry_before_first_chunk(self):
        attempts = {"count": 0}

        def _open_stream():
            attempts["count"] += 1
            if attempts["count"] < 2:
                raise RuntimeError("Upstream request failed")

            def _gen():
                yield "a"
                yield "b"

            return _gen()

        with patch.dict(os.environ, {"MRAG_LLM_MAX_RETRIES": "2", "MRAG_LLM_RETRY_BASE_DELAY": "0"}, clear=False):
            chunks = list(stream_with_retry(_open_stream, label="test-stream"))

        self.assertEqual(["a", "b"], chunks)
        self.assertEqual(2, attempts["count"])

    def test_stream_with_retry_should_not_retry_after_first_chunk(self):
        attempts = {"count": 0}

        def _open_stream():
            attempts["count"] += 1

            def _gen():
                yield "a"
                raise RuntimeError("Upstream request failed")

            return _gen()

        with patch.dict(os.environ, {"MRAG_LLM_MAX_RETRIES": "3", "MRAG_LLM_RETRY_BASE_DELAY": "0"}, clear=False):
            with self.assertRaises(RuntimeError):
                list(stream_with_retry(_open_stream, label="test-stream"))

        self.assertEqual(1, attempts["count"])


class LLMClientRetryTest(unittest.TestCase):

    def test_llm_completions_should_retry_transient_errors(self):
        from reactor_tool.tool.mrag.generation.llm import LLMClient

        attempts = {"count": 0}

        class _FakeCompletions:
            def create(self, **kwargs):
                attempts["count"] += 1
                if attempts["count"] < 2:
                    raise RuntimeError("Upstream request failed")
                return type(
                    "Resp",
                    (),
                    {
                        "choices": [
                            type("Choice", (), {"message": type("Msg", (), {"content": "ok"})()})()
                        ]
                    },
                )()

        class _FakeClient:
            def __init__(self):
                self.chat = type("Chat", (), {"completions": _FakeCompletions()})()

        with patch.dict(
            os.environ,
            {
                "LLM_API_KEY": "k",
                "LLM_MODEL_NAME": "gpt-test",
                "LLM_MODEL_BASE_URL": "https://example.com/v1",
                "MRAG_LLM_MAX_RETRIES": "2",
                "MRAG_LLM_RETRY_BASE_DELAY": "0",
            },
            clear=False,
        ):
            with patch("reactor_tool.tool.mrag.generation.llm.OpenAI", return_value=_FakeClient()):
                client = LLMClient()
                result = client.completions([{"role": "user", "content": "hi"}], stream=False)

        self.assertEqual("ok", result)
        self.assertEqual(2, attempts["count"])


if __name__ == "__main__":
    unittest.main()
