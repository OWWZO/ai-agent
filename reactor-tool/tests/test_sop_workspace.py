# -*- coding: utf-8 -*-
import unittest

from reactor_tool.tool.sop_workspace import (
    build_sop_string,
    normalize_steps,
    payload_to_sop_record,
    point_id_for,
)


class SopWorkspaceHelpersTest(unittest.TestCase):
    def test_normalize_steps(self):
        steps = normalize_steps(
            [
                {"title": " 分析 ", "steps": ["a", "", "b"]},
                {"title": "汇总", "steps": "x"},
            ]
        )
        self.assertEqual(
            steps,
            [
                {"title": "分析", "steps": ["a", "b"]},
                {"title": "汇总", "steps": ["x"]},
            ],
        )

    def test_build_sop_string(self):
        text = build_sop_string(
            "销售分析",
            "综合分析",
            [{"title": "趋势", "steps": ["按月统计"]}],
        )
        self.assertIn("销售分析", text)
        self.assertIn("趋势", text)
        self.assertIn("按月统计", text)

    def test_point_id_stable(self):
        a = point_id_for("abc", "name")
        b = point_id_for("abc", "name")
        c = point_id_for("abc", "sop_string")
        self.assertEqual(a, b)
        self.assertNotEqual(a, c)

    def test_payload_to_sop_record(self):
        record = payload_to_sop_record(
            {
                "sop_id": "1",
                "sop_name": "销售",
                "sop_desc": "desc",
                "sop_type": "list",
                "status": "ONLINE",
                "sop_json_string": '{"sop_name":"销售","sop_desc":"desc","sop_steps":[{"title":"t","steps":["s"]}]}',
            }
        )
        self.assertEqual(record["status"], "online")
        self.assertEqual(record["sop_steps"][0]["title"], "t")


if __name__ == "__main__":
    unittest.main()
