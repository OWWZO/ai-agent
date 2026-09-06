# -*- coding: utf-8 -*-
import os
import unittest

from reactor_tool.tool.sandbox_backend_config import get_e2b_proxy


class GetE2BProxyTest(unittest.TestCase):
    def setUp(self):
        self._prev_e2b = os.environ.get("E2B_PROXY")
        self._prev_web_fetch = os.environ.get("REACTOR_WEB_FETCH_PROXY")

    def tearDown(self):
        self._restore("E2B_PROXY", self._prev_e2b)
        self._restore("REACTOR_WEB_FETCH_PROXY", self._prev_web_fetch)

    @staticmethod
    def _restore(key, value):
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value

    def test_prefers_e2b_proxy(self):
        os.environ["E2B_PROXY"] = " http://e2b-proxy:8080 "
        os.environ["REACTOR_WEB_FETCH_PROXY"] = "http://fallback:8080"
        self.assertEqual("http://e2b-proxy:8080", get_e2b_proxy())

    def test_falls_back_to_web_fetch_proxy(self):
        os.environ.pop("E2B_PROXY", None)
        os.environ["REACTOR_WEB_FETCH_PROXY"] = "http://fallback:8080"
        self.assertEqual("http://fallback:8080", get_e2b_proxy())

    def test_explicit_empty_e2b_proxy_disables_fallback(self):
        os.environ["E2B_PROXY"] = ""
        os.environ["REACTOR_WEB_FETCH_PROXY"] = "http://fallback:8080"
        self.assertIsNone(get_e2b_proxy())


if __name__ == "__main__":
    unittest.main()
