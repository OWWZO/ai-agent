# -*- coding: utf-8 -*-
import json
import unittest
from unittest.mock import patch

from reactor_tool.tool.analysis_component.analysis_fc_agent import (
    chat_completion_with_tools,
)


class _FakeResponse:
    status_code = 200

    def __init__(self, lines):
        self._lines = lines

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def iter_lines(self):
        return iter(self._lines)


class _FakeClient:
    requests = []

    def __init__(self, **kwargs):
        self.timeout_kwargs = kwargs

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def stream(self, method, url, headers, json):
        self.requests.append(
            {"method": method, "url": url, "headers": headers, "json": json}
        )
        return _FakeResponse(
            [
                "data: "
                + json_module_dumps(
                    {
                        "id": "chat-1",
                        "choices": [
                            {
                                "delta": {
                                    "role": "assistant",
                                    "tool_calls": [
                                        {
                                            "index": 0,
                                            "id": "call-1",
                                            "type": "function",
                                            "function": {
                                                "name": "python_interpreter",
                                                "arguments": '{"code": "df = ',
                                            },
                                        }
                                    ],
                                }
                            }
                        ],
                    }
                ),
                "data: "
                + json_module_dumps(
                    {
                        "choices": [
                            {
                                "delta": {
                                    "tool_calls": [
                                        {
                                            "index": 0,
                                            "function": {
                                                "arguments": 'get_data(query=\\"x\\")"}',
                                            },
                                        }
                                    ]
                                }
                            }
                        ]
                    }
                ),
                'data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}',
                "data: [DONE]",
            ]
        )


def json_module_dumps(value):
    """Keep the fake SSE payload readable while avoiding hand-escaped JSON."""
    return json.dumps(value, ensure_ascii=False)


class AnalysisFcStreamTest(unittest.TestCase):
    def test_stream_request_and_tool_call_fragments_are_merged(self):
        _FakeClient.requests = []
        with patch(
            "reactor_tool.tool.analysis_component.analysis_fc_agent.DefaultHttpxClient",
            _FakeClient,
        ):
            response = chat_completion_with_tools(
                messages=[{"role": "user", "content": "分析销售"}],
                tools=[
                    {
                        "type": "function",
                        "function": {"name": "python_interpreter"},
                    }
                ],
                model="gpt-test",
                api_base="https://example.test/v1",
                api_key="test-key",
            )

        request = _FakeClient.requests[0]
        self.assertTrue(request["json"]["stream"])
        self.assertEqual("text/event-stream", request["headers"]["Accept"])
        tool_call = response["choices"][0]["message"]["tool_calls"][0]
        self.assertEqual("python_interpreter", tool_call["function"]["name"])
        self.assertEqual(
            '{"code": "df = get_data(query=\\"x\\")"}',
            tool_call["function"]["arguments"],
        )
        self.assertEqual("tool_calls", response["choices"][0]["finish_reason"])


if __name__ == "__main__":
    unittest.main()
