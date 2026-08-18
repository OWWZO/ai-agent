# -*- coding: utf-8 -*-
import os
import unittest

from server import resolve_worker_count
from reactor_tool.service_role import get_sandbox_base_url, get_service_role, sandbox_requires_single_worker


class ServiceRoleRoutingTest(unittest.TestCase):
    def setUp(self):
        self._prev_role = os.environ.get("REACTOR_TOOL_ROLE")
        self._prev_url = os.environ.get("REACTOR_SANDBOX_URL")

    def tearDown(self):
        self._restore("REACTOR_TOOL_ROLE", self._prev_role)
        self._restore("REACTOR_SANDBOX_URL", self._prev_url)

    @staticmethod
    def _restore(key: str, value):
        if value is None:
            os.environ.pop(key, None)
        else:
            os.environ[key] = value

    def test_default_role_is_all(self):
        os.environ.pop("REACTOR_TOOL_ROLE", None)
        self.assertEqual("all", get_service_role())

    def test_sandbox_forces_single_worker(self):
        os.environ["REACTOR_TOOL_ROLE"] = "sandbox"
        self.assertTrue(sandbox_requires_single_worker())
        self.assertEqual(
            1,
            resolve_worker_count(4, reload_enabled=False, force_single_worker=True),
        )

    def test_api_router_proxies_sandbox_paths(self):
        os.environ["REACTOR_TOOL_ROLE"] = "api"
        os.environ["REACTOR_SANDBOX_URL"] = "http://127.0.0.1:1602"
        from reactor_tool.api import build_api_router

        router = build_api_router()
        paths = {getattr(r, "path", None) for r in router.routes}
        # 反代挂在 /v1 + /tool + /bash
        self.assertTrue(any(p and p.endswith("/bash") for p in paths), paths)
        self.assertTrue(any(p and p.endswith("/code_execution") for p in paths), paths)
        self.assertEqual("http://127.0.0.1:1602", get_sandbox_base_url())

    def test_sandbox_router_only_sandbox_tools(self):
        os.environ["REACTOR_TOOL_ROLE"] = "sandbox"
        from reactor_tool.api import build_api_router

        router = build_api_router()
        paths = sorted(p for p in (getattr(r, "path", None) for r in router.routes) if p)
        self.assertTrue(any(p.endswith("/bash") for p in paths), paths)
        self.assertTrue(any(p.endswith("/code_execution") for p in paths), paths)
        self.assertFalse(any("file_tool" in p for p in paths), paths)


if __name__ == "__main__":
    unittest.main()
