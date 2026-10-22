# -*- coding: utf-8 -*-
"""util/env seam 回归。

重点固化两种布尔语义的差异（空串：env_bool → default，env_bool_strict → False），
迁移 8 个私有 helper 时必须按原语义选函数，否则开关行为会翻转。
"""
import os
import unittest
from unittest.mock import patch

from cute_tool.util.env import (
    env_bool,
    env_bool_strict,
    env_float,
    env_int,
    env_str,
    env_str_any,
)


class EnvSeamTest(unittest.TestCase):
    def test_env_str_treats_blank_as_unset(self):
        with patch.dict(os.environ, {"ENV_SEAM_BLANK": "   ", "ENV_SEAM_SET": " v "}):
            self.assertEqual(env_str("ENV_SEAM_BLANK", "fallback"), "fallback")
            self.assertEqual(env_str("ENV_SEAM_SET"), "v")
            self.assertEqual(env_str("ENV_SEAM_MISSING", "d"), "d")
            self.assertIsNone(env_str("ENV_SEAM_MISSING"))

    def test_env_str_any_returns_first_non_blank_in_order(self):
        env = {"ENV_SEAM_A": "", "ENV_SEAM_B": " from_b ", "ENV_SEAM_C": "from_c"}
        with patch.dict(os.environ, env, clear=False):
            self.assertEqual(env_str_any("ENV_SEAM_A", "ENV_SEAM_B", "ENV_SEAM_C"), "from_b")
            self.assertEqual(env_str_any("ENV_SEAM_ZZ", default="d"), "d")

    def test_env_int_and_float_fall_back_on_bad_value(self):
        env = {"ENV_SEAM_I": "42", "ENV_SEAM_BAD_I": "abc", "ENV_SEAM_F": "1.5"}
        with patch.dict(os.environ, env, clear=False):
            self.assertEqual(env_int("ENV_SEAM_I", 0), 42)
            self.assertEqual(env_int("ENV_SEAM_BAD_I", 7), 7)
            self.assertEqual(env_int("ENV_SEAM_MISSING", 7), 7)
            self.assertEqual(env_float("ENV_SEAM_F", 0.0), 1.5)
            self.assertEqual(env_float("ENV_SEAM_BAD_I", 2.0), 2.0)

    def test_env_bool_blank_falls_back_to_default(self):
        """语义 A（plan_sop._env_flag）：空串视为未设置。"""
        with patch.dict(os.environ, {"ENV_SEAM_EMPTY": ""}, clear=False):
            self.assertTrue(env_bool("ENV_SEAM_EMPTY", True))
            self.assertFalse(env_bool("ENV_SEAM_EMPTY", False))

    def test_env_bool_truthy_values(self):
        for raw in ("1", "true", "TRUE", "yes", "Y", "on", " on "):
            with patch.dict(os.environ, {"ENV_SEAM_VIZ": raw}, clear=False):
                self.assertTrue(env_bool("ENV_SEAM_VIZ"), raw)

    def test_env_bool_strict_blank_is_false(self):
        """语义 B（qdrant_utils._env_bool）：空串已设置 → 假值。"""
        with patch.dict(os.environ, {"ENV_SEAM_EMPTY": ""}, clear=False):
            self.assertFalse(env_bool_strict("ENV_SEAM_EMPTY", True))

    def test_env_bool_strict_missing_uses_default(self):
        with patch.dict(os.environ, {}, clear=False):
            os.environ.pop("ENV_SEAM_ABSENT", None)
            self.assertTrue(env_bool_strict("ENV_SEAM_ABSENT", True))
            self.assertFalse(env_bool_strict("ENV_SEAM_ABSENT"))


if __name__ == "__main__":
    unittest.main()
