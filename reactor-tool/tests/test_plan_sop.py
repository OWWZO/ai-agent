# -*- coding: utf-8 -*-
import os
import unittest
from unittest.mock import patch

from reactor_tool.tool.plan_sop import PlanSOP


class PlanSopTest(unittest.TestCase):

    def test_should_not_treat_false_string_as_enabled_qdrant(self):
        with patch.dict(os.environ, {"SOP_QDRANT_ENABLE": "false"}, clear=False):
            with patch("reactor_tool.tool.plan_sop.QdrantRecall") as qdrant_recall_cls:
                plan_sop = PlanSOP("request-1")
                result = plan_sop.sop_recall("销售分析", vector_type="name")

        self.assertFalse(qdrant_recall_cls.called)
        self.assertTrue(len(result) > 0)
        self.assertEqual("对销售数据进行综合分析", result[0].sop_name)


if __name__ == "__main__":
    unittest.main()
