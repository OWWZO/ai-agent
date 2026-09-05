# -*- coding: utf-8 -*-
import os
import unittest

from reactor_tool.tool.sandbox_backend_config import apply_e2b_no_proxy


class ApplyE2BNoProxyTest(unittest.TestCase):
    def setUp(self):
        self._prev_no = os.environ.get("NO_PROXY")
        self._prev_low = os.environ.get("no_proxy")

    def tearDown(self):
        self._restore("NO_PROXY", self._prev_no)
        self._restore("no_proxy", self._prev_low)

    @staticmethod
    def _restore(key, value):
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value

    @staticmethod
    def _tokens(value):
        return [p.strip() for p in (value or "").split(",") if p.strip()]

    def test_appends_e2b_hosts_and_keeps_existing(self):
        os.environ["NO_PROXY"] = "127.0.0.1,localhost"
        os.environ["no_proxy"] = "127.0.0.1,localhost"
        apply_e2b_no_proxy()
        tokens = self._tokens(os.environ["NO_PROXY"])
        self.assertIn("127.0.0.1", tokens)
        self.assertIn("e2b.app", tokens)
        self.assertIn(".e2b.app", tokens)
        self.assertEqual(tokens.count("e2b.app"), 1)
        self.assertIn("e2b.app", self._tokens(os.environ["no_proxy"]))

    def test_is_idempotent(self):
        os.environ.pop("NO_PROXY", None)
        os.environ.pop("no_proxy", None)
        apply_e2b_no_proxy()
        first = os.environ["NO_PROXY"]
        apply_e2b_no_proxy()
        self.assertEqual(first, os.environ["NO_PROXY"])
        self.assertEqual(self._tokens(first).count("e2b.app"), 1)
        self.assertEqual(self._tokens(first).count(".e2b.app"), 1)


if __name__ == "__main__":
    unittest.main()
