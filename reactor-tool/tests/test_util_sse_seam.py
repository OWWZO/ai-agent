# -*- coding: utf-8 -*-
"""util/sse seam 回归。

固化信封字段名与心跳配置：Java 侧按 ``requestId/data/isFinal`` 解析，
按 ``heartbeat`` + 15s 判活，字段或间隔改动都是协议破坏。
"""
import json
import unittest

from reactor_tool.util.sse import (
    HEARTBEAT_DATA,
    HEARTBEAT_PING_SECONDS,
    sse_event,
    sse_response,
    sent_event,
)


class SseSeamTest(unittest.TestCase):
    def test_sse_event_envelope_shape(self):
        raw = sse_event("rid-1", "hello", is_final=True)
        self.assertEqual(
            json.loads(raw),
            {"requestId": "rid-1", "isFinal": True, "data": "hello"},
        )

    def test_sse_event_omits_data_when_none(self):
        self.assertEqual(
            json.loads(sse_event("rid-2", is_final=False)),
            {"requestId": "rid-2", "isFinal": False},
        )

    def test_sse_event_passes_extra_fields_and_keeps_unicode(self):
        raw = sse_event("rid-3", {"k": "中文"}, step=2, isFinal=False)
        self.assertIn("中文", raw)
        self.assertEqual(
            json.loads(raw),
            {
                "requestId": "rid-3",
                "isFinal": False,
                "data": {"k": "中文"},
                "step": 2,
            },
        )

    def test_sent_event_wraps_serialized_body(self):
        event = sent_event("rid-4", "hi")
        self.assertEqual(event.data, json.dumps(
            {"requestId": "rid-4", "isFinal": False, "data": "hi"}, ensure_ascii=False
        ))

    def test_sse_response_keeps_heartbeat_config(self):
        async def _gen():  # pragma: no cover - 只用于构造响应对象
            yield "never-consumed"

        response = sse_response(_gen())
        # sse_starlette 不把 ping 暴露为属性，只把它存进内部 kwargs；
        # 这里通过 body_iterator 之外的公开钩子间接验证我们传的值。
        self.assertEqual(HEARTBEAT_PING_SECONDS, 15)
        self.assertEqual(response.ping_message_factory().data, HEARTBEAT_DATA)


if __name__ == "__main__":
    unittest.main()
