# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.plan_sop import PlanSOP


class PlanSopTest(unittest.TestCase):

    def test_should_not_treat_false_string_as_enabled_qdrant(self):
        env = {
            "SOP_QDRANT_ENABLE": "false",
            "QDRANT_URL": "",
            "QDRANT_HOST": "",
            "TR_QDRANT_URL": "",
        }
        with patch.dict(os.environ, env, clear=False):
            with patch.object(PlanSOP, "_search_qdrant_direct") as search_mock:
                plan_sop = PlanSOP("request-1")
                result = plan_sop.sop_recall("销售分析", vector_type="name")

        self.assertFalse(search_mock.called)
        self.assertTrue(len(result) > 0)
        self.assertEqual("对销售数据进行综合分析", result[0].sop_name)

    def test_should_auto_enable_when_qdrant_url_configured(self):
        env = {
            "SOP_QDRANT_ENABLE": "",
            "QDRANT_URL": "https://example.qdrant.cloud",
            "QDRANT_HOST": "",
            "TR_QDRANT_URL": "",
            "SOP_COLLECTION_NAME": "sop_plan",
        }
        fake_hits = [
            {
                "sop_id": "x1",
                "sop_name": "自定义SOP",
                "sop_type": "list",
                "description": "desc",
                "sop_string": "自定义SOP",
                "sop_json_string": "{}",
                "vector_type": "name",
                "status": "online",
                "score": 0.95,
            }
        ]
        with patch.dict(os.environ, env, clear=False):
            with patch.object(PlanSOP, "_search_qdrant_direct", return_value=fake_hits) as search_mock:
                plan_sop = PlanSOP("request-2")
                result = plan_sop.sop_recall("自定义", vector_type="name")

        self.assertTrue(search_mock.called)
        self.assertEqual("自定义SOP", result[0].sop_name)


if __name__ == "__main__":
    unittest.main()
