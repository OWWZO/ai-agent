# -*- coding: utf-8 -*-
import os
import unittest

from reactor_tool.tool.sandbox_backend_config import (
    get_e2b_fs_only_idle_sec,
    get_e2b_full_pause_debounce_sec,
    get_e2b_proxy,
)


class GetE2BProxyTest(unittest.TestCase):
    def setUp(self):
        self._prev_e2b = os.environ.get("E2B_PROXY")
        self._prev_web_fetch = os.environ.get("REACTOR_WEB_FETCH_PROXY")
        self._prev_debounce = os.environ.get("E2B_FULL_PAUSE_DEBOUNCE_SEC")
        self._prev_idle = os.environ.get("E2B_FS_ONLY_IDLE_SEC")

    def tearDown(self):
        self._restore("E2B_PROXY", self._prev_e2b)
        self._restore("REACTOR_WEB_FETCH_PROXY", self._prev_web_fetch)
        self._restore("E2B_FULL_PAUSE_DEBOUNCE_SEC", self._prev_debounce)
        self._restore("E2B_FS_ONLY_IDLE_SEC", self._prev_idle)

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

    def test_explicit_off_e2b_proxy_disables_fallback(self):
        os.environ["E2B_PROXY"] = "off"
        os.environ["REACTOR_WEB_FETCH_PROXY"] = "http://fallback:8080"
        self.assertIsNone(get_e2b_proxy())

    def test_pause_and_idle_defaults(self):
        os.environ.pop("E2B_FULL_PAUSE_DEBOUNCE_SEC", None)
        os.environ.pop("E2B_FS_ONLY_IDLE_SEC", None)
        self.assertEqual(3.0, get_e2b_full_pause_debounce_sec())
        self.assertEqual(1200.0, get_e2b_fs_only_idle_sec())

    def test_pause_and_idle_environment_overrides(self):
        os.environ["E2B_FULL_PAUSE_DEBOUNCE_SEC"] = "7.5"
        os.environ["E2B_FS_ONLY_IDLE_SEC"] = "45"
        self.assertEqual(7.5, get_e2b_full_pause_debounce_sec())
        self.assertEqual(45.0, get_e2b_fs_only_idle_sec())


if __name__ == "__main__":
    unittest.main()
