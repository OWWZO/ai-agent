import ast
import contextlib
import io
import json
import os
import shlex
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from reactor_tool.tool.code_interpreter_policy import build_permission_policy
from reactor_tool.tool.e2b_sandbox_backend import E2BPythonSandboxExecutor
from reactor_tool.tool.python_sandbox_executor import (
    PythonSandboxExecutionError,
    PythonSandboxExecutor,
)
from reactor_tool.tool.user_sandbox_manager import (
    UserSandboxManager,
    reset_user_sandbox_manager,
)

_REMOTE_ROOT = "/home/user/workspace/sessions/anonymous"


class _FakeFiles:
    def __init__(self, store: dict[str, bytes]):
        self.store = store
        self.write_files_count = 0

    def write(self, path: str, data, **kwargs):
        if hasattr(data, "read"):
            data = data.read()
        if isinstance(data, str):
            data = data.encode("utf-8")
        self.store[str(path).replace("\\", "/")] = bytes(data)

    def write_files(self, files, **kwargs):
        self.write_files_count += 1
        for item in files:
            self.write(item["path"], item["data"])

    def remove(self, path: str):
        self.store.pop(str(path).replace("\\", "/"), None)

    def read(self, path: str, format: str | None = None):
        data = self.store.get(str(path).replace("\\", "/"), b"")
        if format == "bytes":
            return data
        return data


class _FakeSandbox:
    def __init__(self, **kwargs):
        self.kwargs = kwargs
        self.files = _FakeFiles({})
        self.sandbox_id = "fake-sandbox"
        self.jupyter_ready = False
        self.commands = _FakeCommands(self)
        self.killed = False
        self.pauses: list[bool] = []
        self._remote_root = _REMOTE_ROOT
        self._kernel: dict[str, object] = {"__remote_writes__": {}}

    def run_code(self, code: str, timeout: int = 60):
        if (
            "__SESSION_SNAPSHOT__" in code
            or "__SANDBOX_SNAPSHOT__" in code
            or "__reactor_sandbox_snapshot__" in code
        ):
            self._set_remote_root_from_code(code)
            files = {}
            prefix = self._remote_root.rstrip("/") + "/"
            for path, data in self.files.store.items():
                norm = path.replace("\\", "/")
                if not norm.startswith(prefix):
                    continue
                rel = norm[len(prefix) :]
                if (
                    not rel
                    or rel.startswith("input/")
                    or any(part.startswith(".") for part in rel.split("/"))
                ):
                    continue
                if Path(rel).name.startswith("__last_source__"):
                    continue
                files[rel] = [len(data), 1_000_000 + len(data)]
            stdout = [f"__SANDBOX_SNAPSHOT__{__import__('json').dumps(files)}"]
            return SimpleNamespace(
                logs=SimpleNamespace(stdout=stdout, stderr=[]),
                error=None,
                results=[],
                text=None,
            )

        self._set_remote_root_from_code(code)
        local_ns = dict(self._kernel)
        local_ns.setdefault("__remote_writes__", {})
        from pathlib import Path as PathCls

        orig_write_text = PathCls.write_text
        orig_write_bytes = PathCls.write_bytes
        orig_read_text = PathCls.read_text
        orig_read_bytes = PathCls.read_bytes

        def _norm_remote(path_obj: Path) -> str:
            text = str(path_obj).replace("\\", "/")
            if text.startswith("home/user/workspace"):
                text = "/" + text
            if text.startswith(self._remote_root):
                return text
            marker = self._remote_root.rstrip("/") + "/"
            if marker in text:
                return f"{self._remote_root}/{text.split(marker, 1)[1]}"
            return text

        def _rel(path_obj: Path) -> str:
            text = _norm_remote(path_obj)
            marker = self._remote_root.rstrip("/") + "/"
            if text.startswith(marker):
                return text[len(marker) :]
            if text.startswith(self._remote_root.rstrip("/")):
                return text[len(self._remote_root.rstrip("/")) :].lstrip("/")
            return Path(text).name

        def _write_text(self, data, encoding="utf-8", errors="strict", newline=None):
            rel = _rel(self)
            payload = data if isinstance(data, str) else str(data)
            raw = payload.encode(encoding, errors=errors)
            local_ns["__remote_writes__"][rel] = raw
            return len(raw)

        def _write_bytes(self, data):
            rel = _rel(self)
            raw = data if isinstance(data, (bytes, bytearray)) else bytes(data)
            local_ns["__remote_writes__"][rel] = bytes(raw)
            return len(raw)

        store = self.files.store

        def _read_text(path_self, encoding="utf-8", errors="strict"):
            remote = _norm_remote(path_self)
            data = store.get(remote)
            if data is None:
                rel = _rel(path_self)
                data = dict(local_ns.get("__remote_writes__") or {}).get(rel)
            if data is None:
                raise FileNotFoundError(remote)
            return data.decode(encoding, errors=errors)

        def _read_bytes(path_self):
            remote = _norm_remote(path_self)
            data = store.get(remote)
            if data is None:
                rel = _rel(path_self)
                data = dict(local_ns.get("__remote_writes__") or {}).get(rel)
            if data is None:
                raise FileNotFoundError(remote)
            return data

        PathCls.write_text = _write_text  # type: ignore[method-assign]
        PathCls.write_bytes = _write_bytes  # type: ignore[method-assign]
        PathCls.read_text = _read_text  # type: ignore[method-assign]
        PathCls.read_bytes = _read_bytes  # type: ignore[method-assign]
        stdout_buf = io.StringIO()
        stderr_buf = io.StringIO()
        try:
            with (
                contextlib.redirect_stdout(stdout_buf),
                contextlib.redirect_stderr(stderr_buf),
            ):
                exec(code, local_ns, local_ns)
            self._kernel = local_ns
            for rel, content in dict(local_ns.get("__remote_writes__") or {}).items():
                self.files.write(f"{self._remote_root}/{rel}", content)
            return SimpleNamespace(
                logs=SimpleNamespace(
                    stdout=[stdout_buf.getvalue()], stderr=[stderr_buf.getvalue()]
                ),
                error=None,
                results=[],
                text=None,
            )
        except Exception as exc:
            return SimpleNamespace(
                logs=SimpleNamespace(
                    stdout=[stdout_buf.getvalue()],
                    stderr=[stderr_buf.getvalue(), str(exc)],
                ),
                error=SimpleNamespace(
                    name=type(exc).__name__, value=str(exc), traceback=""
                ),
                results=[],
                text=None,
            )
        finally:
            PathCls.write_text = orig_write_text  # type: ignore[method-assign]
            PathCls.write_bytes = orig_write_bytes  # type: ignore[method-assign]
            PathCls.read_text = orig_read_text  # type: ignore[method-assign]
            PathCls.read_bytes = orig_read_bytes  # type: ignore[method-assign]

    def kill(self):
        self.killed = True

    def pause(self, keep_memory=True):
        self.pauses.append(bool(keep_memory))
        return True

    def _set_remote_root_from_code(self, code: str):
        try:
            tree = ast.parse(code)
        except SyntaxError:
            return
        for node in ast.walk(tree):
            if isinstance(node, ast.Assign):
                if (
                    any(
                        isinstance(target, ast.Name) and target.id == "workspace_root"
                        for target in node.targets
                    )
                    and isinstance(node.value, ast.Constant)
                    and isinstance(node.value.value, str)
                ):
                    self._remote_root = node.value.value
                    return
            if not isinstance(node, ast.Call) or not node.args:
                continue
            if isinstance(node.func, ast.Name) and node.func.id == "Path":
                value = node.args[0]
            elif isinstance(node.func, ast.Attribute) and node.func.attr == "chdir":
                value = node.args[0]
            else:
                continue
            if isinstance(value, ast.Constant) and isinstance(value.value, str):
                self._remote_root = value.value
                return


class _FakeCommands:
    def __init__(self, sandbox: _FakeSandbox):
        self.sandbox = sandbox
        self.calls: list[tuple[str, str | None, int | None]] = []

    def run(self, command, cwd=None, timeout=None, user=None):
        self.calls.append((command, cwd, timeout))
        if (
            "127.0.0.1:49999/health" in command
            and "systemctl start jupyter" not in command
        ):
            code = 0 if self.sandbox.jupyter_ready else 7
            return SimpleNamespace(exit_code=code, stdout="", stderr="")
        if "systemctl start jupyter" in command:
            self.sandbox.jupyter_ready = True
            return SimpleNamespace(exit_code=0, stdout="", stderr="")
        if "__SESSION_SNAPSHOT__" in command:
            script = shlex.split(command)[2]
            self.sandbox._set_remote_root_from_code(script)
            prefix = self.sandbox._remote_root.rstrip("/") + "/"
            files = {}
            for path, data in self.sandbox.files.store.items():
                norm = path.replace("\\", "/")
                if not norm.startswith(prefix):
                    continue
                rel = norm[len(prefix) :]
                if (
                    not rel
                    or rel.startswith("input/")
                    or any(part.startswith(".") for part in rel.split("/"))
                ):
                    continue
                files[rel] = [len(data), 1_000_000 + len(data)]
            return SimpleNamespace(
                exit_code=0,
                stdout=f"__SESSION_SNAPSHOT__{json.dumps(files)}\n",
                stderr="",
            )
        return SimpleNamespace(exit_code=0, stdout="", stderr="")


class E2BSandboxBackendTest(unittest.TestCase):
    @staticmethod
    def _close_test_executor(executor):
        executor.close()
        implementation = getattr(executor, "_impl", executor)
        implementation._manager.shutdown()

    def test_executor_passes_configured_proxy_to_sandbox_factory(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            captured: dict[str, object] = {}

            def factory(**kwargs):
                captured.update(kwargs)
                return _FakeSandbox()

            previous_e2b_proxy = os.environ.get("E2B_PROXY")
            previous_web_fetch_proxy = os.environ.get("PROXY")
            os.environ["E2B_PROXY"] = "http://e2b-proxy.test:7890"
            os.environ.pop("PROXY", None)
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=factory,
            )
            try:
                executor.execute("print('done')")
                self.assertEqual("http://e2b-proxy.test:7890", captured["proxy"])
            finally:
                self._close_test_executor(executor)
                if previous_e2b_proxy is None:
                    os.environ.pop("E2B_PROXY", None)
                else:
                    os.environ["E2B_PROXY"] = previous_e2b_proxy
                if previous_web_fetch_proxy is None:
                    os.environ.pop("PROXY", None)
                else:
                    os.environ["PROXY"] = previous_web_fetch_proxy

    def test_executor_facade_routes_to_e2b_and_downloads_outputs(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            (workspace_root / "input").mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            fake = _FakeSandbox()
            executor = PythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                backend="e2b",
                sandbox_factory=lambda **kwargs: fake,
            )
            try:
                result = executor.execute(
                    "from pathlib import Path\n"
                    "Path(build_output_path('hello.txt')).write_text('hi', encoding='utf-8')\n"
                    "print('done')\n"
                )
                self.assertEqual(0, result.returncode)
                self.assertIn("done", result.stdout)
                self.assertTrue((output_dir / "hello.txt").is_file())
                self.assertEqual(
                    "hi", (output_dir / "hello.txt").read_text(encoding="utf-8")
                )
                self.assertEqual(
                    ["hello.txt"], [item["name"] for item in result.produced_files]
                )
            finally:
                self._close_test_executor(executor)
            self.assertFalse(fake.killed)

    def test_e2b_keeps_kernel_state_across_executes(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            fake = _FakeSandbox()
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: fake,
            )
            try:
                first = executor.execute("counter = 40\nprint(counter)\n")
                second = executor.execute("print(counter + 2)\n")
                self.assertIn("40", first.stdout)
                self.assertIn("42", second.stdout)
                self.assertTrue(executor._bootstrapped)
                self.assertTrue(fake.jupyter_ready)
                self.assertEqual(
                    1,
                    sum(
                        "systemctl start jupyter" in call[0]
                        for call in fake.commands.calls
                    ),
                )
            finally:
                self._close_test_executor(executor)

    def test_e2b_raises_execution_error(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: _FakeSandbox(),
            )
            try:
                with self.assertRaises(PythonSandboxExecutionError):
                    executor.execute("raise RuntimeError('boom')\n")
            finally:
                self._close_test_executor(executor)

    def test_missing_api_key_fails_fast_without_factory(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            reset_user_sandbox_manager()
            old = os.environ.pop("E2B_API_KEY", None)
            executor = E2BPythonSandboxExecutor(policy, timeout_seconds=5)
            try:
                with self.assertRaisesRegex(RuntimeError, "E2B_API_KEY"):
                    executor.execute("print(1)")
            finally:
                reset_user_sandbox_manager()
                if old is not None:
                    os.environ["E2B_API_KEY"] = old
                executor.close()

    def test_injects_file_and_main_name(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            script = workspace_root / "demo_run.py"
            script.write_text("# demo\n", encoding="utf-8")
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: _FakeSandbox(),
            )
            try:
                result = executor.execute(
                    "print(__name__)\nprint(__file__)\n",
                    source_file=str(script),
                )
                self.assertIn("__main__", result.stdout)
                self.assertIn("demo_run.py", result.stdout)
            finally:
                self._close_test_executor(executor)

    def test_same_owner_executes_reuse_one_sandbox_from_shared_manager(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            manager_dir = workspace_root / ".reactor"
            manager_dir.mkdir()
            created: list[dict[str, object]] = []
            fake = _FakeSandbox()

            def factory(**kwargs):
                created.append(kwargs)
                return fake

            manager = UserSandboxManager(
                db_path=str(manager_dir / "manager.sqlite"),
                create_fn=factory,
                start_reaper=False,
            )
            first = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                owner_key="visitor:shared",
                session_id="shared-session",
                manager=manager,
            )
            second = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                owner_key="visitor:shared",
                session_id="shared-session",
                manager=manager,
            )
            try:
                first.execute("print('first')")
                first.close()
                second.execute("print('second')")
                self.assertEqual(1, len(created))
                self.assertFalse(fake.killed)
            finally:
                first.close()
                second.close()
                manager.shutdown()

    def test_second_execute_without_local_changes_skips_workspace_upload(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            (workspace_root / "unchanged.txt").write_text("same", encoding="utf-8")
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            fake = _FakeSandbox()
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: fake,
            )
            try:
                executor.execute(
                    "from pathlib import Path\n"
                    "Path(build_output_path('generated.txt')).write_text('same', encoding='utf-8')"
                )
                writes_after_first = fake.files.write_files_count
                executor.execute("print('second')")
                self.assertEqual(writes_after_first, fake.files.write_files_count)
            finally:
                self._close_test_executor(executor)

    def test_uploads_local_input_files(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            input_dir = workspace_root / "input"
            output_dir.mkdir()
            input_dir.mkdir()
            seed = input_dir / "seed.csv"
            seed.write_bytes(b"a,1\n")
            policy = build_permission_policy(
                profile="analysis",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[{"name": "seed.csv", "path": str(seed)}],
            )
            fake = _FakeSandbox()
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: fake,
            )
            try:
                result = executor.execute(
                    "from pathlib import Path\n"
                    "text = Path(resolve_input_path('seed.csv')).read_text(encoding='utf-8')\n"
                    "Path(build_output_path('out.txt')).write_text(text, encoding='utf-8')\n"
                    "print(text.strip())\n"
                )
                self.assertIn("a,1", result.stdout)
                self.assertEqual(
                    "a,1\n", (output_dir / "out.txt").read_text(encoding="utf-8")
                )
                remote_seed = fake.files.store.get(f"{_REMOTE_ROOT}/input/seed.csv")
                self.assertEqual(b"a,1\n", remote_seed)
            finally:
                self._close_test_executor(executor)

    def test_workspace_profile_wrap_chdirs_to_workspace_root(self):
        with tempfile.TemporaryDirectory() as workspace:
            workspace_root = Path(workspace)
            output_dir = workspace_root / "output"
            output_dir.mkdir()
            policy = build_permission_policy(
                profile="workspace",
                workspace_root=str(workspace_root),
                output_dir=str(output_dir),
                input_files=[],
            )
            executor = E2BPythonSandboxExecutor(
                policy,
                timeout_seconds=15,
                sandbox_factory=lambda **kwargs: _FakeSandbox(),
            )
            try:
                wrapped = executor._wrap_user_code("print(1)", include_bootstrap=True)
                self.assertIn(f"os.chdir({_REMOTE_ROOT!r})", wrapped)
                self.assertNotIn(f"os.chdir({(_REMOTE_ROOT + '/output')!r})", wrapped)
                self.assertNotIn("def build_output_path", wrapped)
                self.assertNotIn("def resolve_input_path", wrapped)
            finally:
                self._close_test_executor(executor)


if __name__ == "__main__":
    unittest.main()
