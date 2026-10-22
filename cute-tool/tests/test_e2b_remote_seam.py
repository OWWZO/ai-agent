# -*- coding: utf-8 -*-
"""tool/e2b_remote seam 回归。

用假沙箱固化成败判定与错误详情拼装，这是原先 8 份拷贝最容易漂移的地方。
"""
import unittest
from types import SimpleNamespace

from cute_tool.tool.e2b_remote import (
    SANDBOX_RUN_UNSUPPORTED,
    check_command,
    command_detail,
    command_succeeded,
    remote_runner,
    run_checked,
)


def _result(exit_code=None, stdout="", stderr="", error=None):
    return SimpleNamespace(exit_code=exit_code, stdout=stdout, stderr=stderr, error=error)


class E2BRemoteSeamTest(unittest.TestCase):
    def test_remote_runner_rejects_unsupported_sandbox(self):
        with self.assertRaises(RuntimeError) as ctx:
            remote_runner(SimpleNamespace(commands=None))
        self.assertEqual(str(ctx.exception), SANDBOX_RUN_UNSUPPORTED)

        with self.assertRaises(RuntimeError) as ctx:
            remote_runner(SimpleNamespace(commands=SimpleNamespace(run=None)))
        self.assertEqual(str(ctx.exception), SANDBOX_RUN_UNSUPPORTED)

    def test_remote_runner_returns_callable(self):
        calls = []

        def fake_run(cmd, **kwargs):
            calls.append((cmd, kwargs))
            return _result(0)

        sandbox = SimpleNamespace(commands=SimpleNamespace(run=fake_run))
        self.assertIs(remote_runner(sandbox), fake_run)

    def test_command_succeeded_accepts_none_or_zero_exit_code(self):
        self.assertTrue(command_succeeded(_result(None)))
        self.assertTrue(command_succeeded(_result(0)))

    def test_command_succeeded_rejects_nonzero_and_error(self):
        self.assertFalse(command_succeeded(_result(1)))
        self.assertFalse(command_succeeded(_result(0, error=RuntimeError("boom"))))

    def test_command_detail_joins_stdout_and_stderr(self):
        result = _result(1, stdout=" out ", stderr=" err ")
        self.assertEqual(command_detail(result), "out err")

    def test_command_detail_empty_when_both_blank(self):
        self.assertEqual(command_detail(_result(1, stdout="  ", stderr="")), "")

    def test_check_command_uses_detail_as_message(self):
        with self.assertRaises(RuntimeError) as ctx:
            check_command(_result(2, stderr=" boom "), failure_prefix="E2B mkdir failed")
        self.assertEqual(str(ctx.exception), "boom")

    def test_check_command_falls_back_to_prefix_and_exit_code(self):
        with self.assertRaises(RuntimeError) as ctx:
            check_command(_result(3), failure_prefix="E2B mkdir failed with exit code")
        self.assertEqual(str(ctx.exception), "E2B mkdir failed with exit code 3")

    def test_check_command_returns_result_on_success(self):
        ok = _result(0)
        self.assertIs(check_command(ok, failure_prefix="unused"), ok)

    def test_run_checked_passes_timeout_and_user_only_when_given(self):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            seen["kwargs"] = kwargs
            return _result(0, stdout="ok")

        sandbox = SimpleNamespace(commands=SimpleNamespace(run=fake_run))

        result = run_checked(sandbox, "sync", timeout=30, failure_prefix="sync failed")
        self.assertEqual(seen["kwargs"], {"timeout": 30})
        self.assertEqual(result.stdout, "ok")

        run_checked(sandbox, "start", timeout=45, failure_prefix="start failed", user="root")
        self.assertEqual(seen["kwargs"], {"timeout": 45, "user": "root"})


if __name__ == "__main__":
    unittest.main()
