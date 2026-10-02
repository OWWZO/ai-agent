# -*- coding: utf-8 -*-
import asyncio
import hashlib
import os
import tempfile
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from reactor_tool.model.protocal import BashSandboxRequest
from reactor_tool.tool import bash_sandbox
from reactor_tool.tool.bash_sandbox import run_bash_sandbox
from reactor_tool.tool.user_sandbox_manager import (
    UserSandboxManager,
    reset_user_sandbox_manager,
)


class _FakeFiles:
    def __init__(self):
        self.data: dict[str, bytes] = {}
        self.writes: list[str] = []
        self.removes: list[str] = []

    def write_files(self, files, **kwargs):
        for item in files:
            path = item["path"]
            data = item["data"]
            self.data[path] = (
                data.encode("utf-8") if isinstance(data, str) else bytes(data)
            )
            self.writes.append(path)

    def read(self, path, format=None):
        return self.data[path]

    def remove(self, path):
        self.removes.append(path)
        self.data.pop(path, None)


class _FakeCommands:
    def __init__(self):
        self.calls: list[tuple[str, str | None, int | None, dict | None]] = []

    def run(self, command, cwd=None, timeout=None, envs=None, user=None):
        self.calls.append((command, cwd, timeout, envs))
        stdout = "ok\n"
        if "__SESSION_SNAPSHOT__" in command:
            stdout = "__SESSION_SNAPSHOT__{}\n"
        elif "__SKILLS_META__" in command:
            stdout = "__SKILLS_META__[]\n"
        return SimpleNamespace(exit_code=0, stdout=stdout, stderr="")


class _FakeSandbox:
    def __init__(self, sandbox_id="sandbox-1"):
        self.sandbox_id = sandbox_id
        self.files = _FakeFiles()
        self.commands = _FakeCommands()
        self.run_code_calls = 0
        self.pause_calls: list[bool] = []
        self.kill_calls = 0
        self.timeout_calls: list[int] = []

    def run_code(self, script, timeout=None):
        self.run_code_calls += 1
        return SimpleNamespace(
            logs=SimpleNamespace(stdout=[], stderr=[]), text="", error=None
        )

    def pause(self, keep_memory=True):
        self.pause_calls.append(bool(keep_memory))
        return True

    def kill(self):
        self.kill_calls += 1

    def set_timeout(self, timeout):
        self.timeout_calls.append(timeout)


def _install_manager(tmp_path: Path, sandbox: _FakeSandbox, *, db_name="sandbox.db"):
    creates = {"n": 0}

    def create(*args, **kwargs):
        creates["n"] += 1
        return sandbox

    manager = UserSandboxManager(
        db_path=str(tmp_path / db_name),
        create_fn=create,
        connect_fn=lambda sandbox_id, **kwargs: sandbox,
        pause_fn=lambda value, keep_memory: value.pause(keep_memory),
        start_reaper=False,
    )
    reset_user_sandbox_manager(manager)
    return manager, creates


def test_local_link_skills_and_run():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    skill = lib / "demo"
    skill.mkdir(parents=True)
    (skill / "SKILL.md").write_text("hello", encoding="utf-8")
    (skill / "scripts").mkdir()
    (skill / "scripts" / "run.py").write_text("print(1)\n", encoding="utf-8")

    workspace = tmp / "session_ws"
    workspace.mkdir()

    prev = os.environ.get("CODE_SANDBOX_BACKEND")
    os.environ["CODE_SANDBOX_BACKEND"] = "local"
    try:
        body = BashSandboxRequest(
            requestId="sess-1",
            command="python skills/demo/scripts/run.py",
            workspaceRoot=str(workspace),
            skillLibraryRoot=str(lib),
            timeoutSeconds=30,
        )
        result = asyncio.run(run_bash_sandbox(body))
    finally:
        if prev is None:
            os.environ.pop("CODE_SANDBOX_BACKEND", None)
        else:
            os.environ["CODE_SANDBOX_BACKEND"] = prev

    assert result.skills_materialized == ["demo"]
    assert not (workspace / "skills").exists()
    assert result.exit_code == 0, (result.exit_code, result.stdout, result.stderr)
    assert "1" in (result.stdout or "")


def test_local_bash_uploads_changed_workspace_files():
    tmp = Path(tempfile.mkdtemp())
    workspace = tmp / "session_ws"
    workspace.mkdir()
    uploaded: list[str] = []

    async def fake_upload(file_path: str, request_id: str):
        uploaded.append(file_path)
        return {
            "fileName": Path(file_path).name,
            "domainUrl": f"https://files.test/{Path(file_path).name}",
            "fileSize": Path(file_path).stat().st_size,
        }

    previous_upload = bash_sandbox.upload_file_by_path
    previous_backend = os.environ.get("CODE_SANDBOX_BACKEND")
    bash_sandbox.upload_file_by_path = fake_upload  # type: ignore[assignment]
    os.environ["CODE_SANDBOX_BACKEND"] = "local"
    try:
        result = asyncio.run(
            run_bash_sandbox(
                BashSandboxRequest(
                    requestId="sess-upload",
                    command="echo ok > output.txt",
                    workspaceRoot=str(workspace),
                    timeoutSeconds=30,
                )
            )
        )
    finally:
        bash_sandbox.upload_file_by_path = previous_upload  # type: ignore[assignment]
        if previous_backend is None:
            os.environ.pop("CODE_SANDBOX_BACKEND", None)
        else:
            os.environ["CODE_SANDBOX_BACKEND"] = previous_backend

    assert result.exit_code == 0
    assert uploaded == [str(workspace / "output.txt")]
    assert result.file_info[0]["domainUrl"] == "https://files.test/output.txt"
    assert result.produced_files[0]["file_name"] == "output.txt"


def test_incremental_write_skill_file_skips_same_content():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    skill = lib / "demo" / "scripts"
    skill.mkdir(parents=True)
    target = skill / "run.py"
    target.write_bytes(b"print(2)\n")

    changed = bash_sandbox._incremental_write_skill_file(
        lib, "demo/scripts/run.py", b"print(2)\n"
    )
    assert changed is False

    changed = bash_sandbox._incremental_write_skill_file(
        lib, "demo/scripts/run.py", b"print(3)\n"
    )
    assert changed is True
    assert target.read_bytes() == b"print(3)\n"

    changed = bash_sandbox._incremental_write_skill_file(
        lib, "new-skill/SKILL.md", b"# new\n"
    )
    assert changed is True
    assert (lib / "new-skill" / "SKILL.md").read_text(encoding="utf-8") == "# new\n"


def test_upload_tree_skips_skills_dir():
    tmp = Path(tempfile.mkdtemp())
    workspace = tmp / "ws"
    (workspace / "skills" / "demo").mkdir(parents=True)
    (workspace / "skills" / "demo" / "a.txt").write_text("skill", encoding="utf-8")
    (workspace / "out.txt").write_text("work", encoding="utf-8")
    sandbox = _FakeSandbox()

    bash_sandbox._e2b_upload_tree(
        sandbox,
        workspace,
        "/home/user/workspace",
        skip_top_dirs={"skills"},
        label="workspace",
    )
    assert any(p.endswith("/out.txt") for p in sandbox.files.writes)
    assert not any("/skills/" in p for p in sandbox.files.writes)


def test_backend_selection_uses_config():
    from reactor_tool.tool.sandbox_backend_config import get_sandbox_backend

    prev = os.environ.get("CODE_SANDBOX_BACKEND")
    try:
        os.environ["CODE_SANDBOX_BACKEND"] = "local"
        assert get_sandbox_backend() == "local"
        os.environ["CODE_SANDBOX_BACKEND"] = "e2b"
        assert get_sandbox_backend() == "e2b"
    finally:
        if prev is None:
            os.environ.pop("CODE_SANDBOX_BACKEND", None)
        else:
            os.environ["CODE_SANDBOX_BACKEND"] = prev


def test_command_needs_skills_heuristic():
    assert bash_sandbox._command_needs_skills("python skills/demo/scripts/run.py")
    assert bash_sandbox._command_needs_skills(r"python skills\demo\scripts\run.py")
    assert bash_sandbox._command_needs_skills("ls Skills/demo")
    assert not bash_sandbox._command_needs_skills("echo hi")
    assert not bash_sandbox._command_needs_skills("ls workspace")


def test_command_referenced_skill_names():
    assert bash_sandbox._command_referenced_skill_names(
        "python skills/demo/scripts/run.py"
    ) == ["demo"]
    assert bash_sandbox._command_referenced_skill_names(
        r"python skills\pptx\scripts\run.py"
    ) == ["pptx"]
    assert bash_sandbox._command_referenced_skill_names(
        'python "./skills/web-artifacts-builder/scripts/x.py"'
    ) == ["web-artifacts-builder"]
    assert bash_sandbox._command_referenced_skill_names(
        "python skills/a/x.py && python skills/b/y.py"
    ) == ["a", "b"]
    assert bash_sandbox._command_referenced_skill_names(
        "python skills/demo/a.py && cat skills/demo/b.py"
    ) == ["demo"]
    assert bash_sandbox._command_referenced_skill_names("ls skills") == []
    assert bash_sandbox._command_referenced_skill_names("ls skills/") == []
    assert bash_sandbox._command_referenced_skill_names("echo hi") == []
    assert bash_sandbox._command_referenced_skill_names(
        "python Skills/Demo/scripts/run.py"
    ) == ["Demo"]
    assert bash_sandbox._command_referenced_skill_names("cat myskills/foo") == []


def test_resolve_skills_to_push():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "pptx").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_text("d", encoding="utf-8")
    (lib / "pptx" / "SKILL.md").write_text("p", encoding="utf-8")

    assert bash_sandbox._resolve_skills_to_push(
        "python skills/demo/scripts/run.py && python skills/missing/x.py",
        lib,
        set(),
    ) == ["demo"]
    assert bash_sandbox._resolve_skills_to_push(
        "python Skills/PPTX/scripts/run.py", lib, set()
    ) == ["pptx"]
    assert (
        bash_sandbox._resolve_skills_to_push(
            "python skills/demo/scripts/run.py", lib, {"demo"}
        )
        == []
    )
    assert bash_sandbox._resolve_skills_to_push("ls skills/", lib, set()) == []
    assert bash_sandbox._resolve_skills_to_push(
        "python skills/demo/a.py && python skills/pptx/b.py", lib, set()
    ) == ["demo", "pptx"]


def test_session_sandbox_reuses_and_incremental_push(tmp_path):
    sandbox = _FakeSandbox()
    manager, creates = _install_manager(tmp_path, sandbox)
    lib = tmp_path / "runtime_skills" / "demo"
    lib.mkdir(parents=True)
    (lib / "SKILL.md").write_bytes(b"skill")
    workspace = tmp_path / "ws"
    workspace.mkdir()
    (workspace / "a.txt").write_bytes(b"1")
    owner = "user:test"
    remote = "/home/user/workspace/sessions/session-reuse-1"

    try:
        first = bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib.parent,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        first_writes = len(sandbox.files.writes)
        assert first[0] == 0
        assert creates["n"] == 1
        assert any(path == f"{remote}/a.txt" for path in sandbox.files.writes)
        assert any(
            path == f"{remote}/skills/demo/SKILL.md" for path in sandbox.files.writes
        )

        second = bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib.parent,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        assert second[0] == 0
        assert len(sandbox.files.writes) == first_writes

        (workspace / "a.txt").write_bytes(b"changed")
        bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib.parent,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        assert len(sandbox.files.writes) == first_writes + 1
        assert sandbox.files.writes[-1] == f"{remote}/a.txt"

        (workspace / "a.txt").unlink()
        bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib.parent,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        assert sandbox.files.removes == [f"{remote}/a.txt"]

        replacement = workspace / "a.txt"
        replacement.write_bytes(b"new")
        sandbox2 = _FakeSandbox("sandbox-2")
        manager.shutdown()
        creates2 = {"n": 0}

        def create2(*args, **kwargs):
            creates2["n"] += 1
            return sandbox2

        def gone(*args, **kwargs):
            raise RuntimeError("404 not found")

        manager2 = UserSandboxManager(
            db_path=str(tmp_path / "sandbox.db"),
            create_fn=create2,
            connect_fn=gone,
            pause_fn=lambda value, keep_memory: value.pause(keep_memory),
            start_reaper=False,
        )
        reset_user_sandbox_manager(manager2)
        bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib.parent,
            set(),
            30,
            64000,
            owner_key=owner,
        )
        assert creates2["n"] == 1
        assert f"{remote}/a.txt" in sandbox2.files.writes
        assert sandbox.kill_calls == 0
        assert sandbox2.kill_calls == 0
    finally:
        reset_user_sandbox_manager()


def test_e2b_reuses_manager_sandbox_without_kill(tmp_path):
    sandbox = _FakeSandbox()
    _, creates = _install_manager(tmp_path, sandbox)
    workspace = tmp_path / "ws"
    workspace.mkdir()
    (workspace / "a.txt").write_text("1", encoding="utf-8")

    try:
        for command in ("echo hi", "echo again"):
            result = bash_sandbox._exec_e2b(
                "session-reuse",
                command,
                workspace,
                None,
                set(),
                30,
                64000,
                owner_key="visitor:one",
            )
            assert result[0] == 0
        assert creates["n"] == 1
        assert sandbox.kill_calls == 0
        assert all("/sessions/session-reuse/" in path for path in sandbox.files.writes)
    finally:
        reset_user_sandbox_manager()


def test_e2b_bash_uses_shell_and_files_without_code_interpreter(tmp_path):
    sandbox = _FakeSandbox()
    _install_manager(tmp_path, sandbox)
    workspace = tmp_path / "ws"
    workspace.mkdir()

    try:
        result = bash_sandbox._exec_e2b(
            "session-readiness",
            "echo ready",
            workspace,
            None,
            set(),
            30,
            64000,
            owner_key="visitor:readiness",
        )

        assert result[0] == 0
        assert sandbox.run_code_calls == 0
        assert any("__SESSION_SNAPSHOT__" in call[0] for call in sandbox.commands.calls)
        assert not any(
            "49999" in call[0] or "jupyter" in call[0]
            for call in sandbox.commands.calls
        )
    finally:
        reset_user_sandbox_manager()


def test_e2b_bash_passes_desktop_display_to_commands_run():
    sandbox = _FakeSandbox()

    result = bash_sandbox._e2b_run_command(
        sandbox,
        "chromium --no-sandbox --new-window about:blank",
        "/workspace",
        30,
    )

    assert result[0] == 0
    assert sandbox.commands.calls[-1][3] == {"DISPLAY": ":0"}


def test_e2b_bash_command_failure_does_not_fallback_to_run_code():
    class FailingSandbox:
        run_code_calls = 0

        class Commands:
            def run(self, command, cwd=None, timeout=None, envs=None, user=None):
                raise RuntimeError("commands unavailable")

        commands = Commands()

        def run_code(self, script, timeout=None):
            self.run_code_calls += 1
            raise AssertionError("Bash must not invoke run_code")

    sandbox = FailingSandbox()
    result = bash_sandbox._e2b_run_command(sandbox, "echo test", "/workspace", 30)

    assert result == (1, "", "commands unavailable", False)
    assert sandbox.run_code_calls == 0


def test_e2b_commands_share_manager_sandbox(tmp_path):
    sandbox = _FakeSandbox()
    _, creates = _install_manager(tmp_path, sandbox)
    lib = tmp_path / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"x")
    workspace = tmp_path / "ws"
    workspace.mkdir()

    try:
        for command in ("cat skills/demo/SKILL.md", "echo after"):
            result = bash_sandbox._exec_e2b(
                "session-shared",
                command,
                workspace,
                lib,
                set(),
                30,
                64000,
                owner_key="user:shared",
            )
            assert result[0] == 0
        assert creates["n"] == 1
        assert sandbox.kill_calls == 0
        assert any(
            "/sessions/session-shared/skills/demo/SKILL.md" in p
            for p in sandbox.files.writes
        )
    finally:
        reset_user_sandbox_manager()


def test_e2b_pushes_only_referenced_skills(tmp_path):
    sandbox = _FakeSandbox()
    _, creates = _install_manager(tmp_path, sandbox)
    lib = tmp_path / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"d")
    (lib / "heavy").mkdir(parents=True)
    (lib / "heavy" / "SKILL.md").write_bytes(b"h")
    workspace = tmp_path / "ws"
    workspace.mkdir()

    try:
        bash_sandbox._exec_e2b(
            "session-filter",
            "python skills/demo/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
            owner_key="user:filter",
        )
        assert any("/skills/demo/SKILL.md" in p for p in sandbox.files.writes)
        assert not any("/skills/heavy/" in p for p in sandbox.files.writes)
        writes_after_demo = len(sandbox.files.writes)

        bash_sandbox._exec_e2b(
            "session-filter",
            "echo after",
            workspace,
            lib,
            set(),
            30,
            64000,
            owner_key="user:filter",
        )
        assert len(sandbox.files.writes) == writes_after_demo

        bash_sandbox._exec_e2b(
            "session-filter",
            "python skills/heavy/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
            owner_key="user:filter",
        )
        assert any("/skills/heavy/SKILL.md" in p for p in sandbox.files.writes)
        assert creates["n"] == 1
    finally:
        reset_user_sandbox_manager()


def test_e2b_push_skills_respects_only_names():
    sandbox = _FakeSandbox()
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "lib"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"d")
    (lib / "heavy").mkdir(parents=True)
    (lib / "heavy" / "SKILL.md").write_bytes(b"h")

    up, _ = bash_sandbox._e2b_push_skills_incremental(
        sandbox, lib, "/home/user/workspace/sessions/s1", set(), {}, only_names={"demo"}
    )
    assert up == 1
    assert any("/skills/demo/" in p for p in sandbox.files.writes)
    assert not any("/skills/heavy/" in p for p in sandbox.files.writes)

    n = len(sandbox.files.writes)
    up_empty, sk_empty = bash_sandbox._e2b_push_skills_incremental(
        sandbox, lib, "/home/user/workspace/sessions/s1", set(), {}, only_names=set()
    )
    assert up_empty == 0 and sk_empty == 0
    assert len(sandbox.files.writes) == n


def test_e2b_skill_sync_same_size_different_hash_downloads():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "lib"
    (lib / "demo").mkdir(parents=True)
    local = lib / "demo" / "run.py"
    local.write_bytes(b"aaaaa")
    remote_data = b"bbbbb"
    remote_hash = hashlib.sha256(remote_data).hexdigest()
    remote = "/home/user/workspace/sessions/s1/skills/demo/run.py"
    sandbox = _FakeSandbox()
    sandbox.files.data[remote] = remote_data

    sandbox.commands.run = (
        lambda command, cwd=None, timeout=None, envs=None, user=None: SimpleNamespace(
            exit_code=0,
            stdout=(
                '__SKILLS_META__[{"rel": "demo/run.py", "size": 5, "sha256": "'
                + remote_hash
                + '"}]\n'
            ),
            stderr="",
        )
    )

    uploaded: dict = {}
    synced = bash_sandbox._e2b_incremental_sync_skills(
        sandbox, "/home/user/workspace/sessions/s1", lib, uploaded
    )
    assert synced == ["demo"]
    assert local.read_bytes() == remote_data
    assert uploaded[remote] == bash_sandbox._file_sig(local)


def test_e2b_skill_sync_same_hash_skips_download():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "lib"
    (lib / "demo").mkdir(parents=True)
    local = lib / "demo" / "run.py"
    local.write_bytes(b"aaaaa")
    remote_hash = hashlib.sha256(b"aaaaa").hexdigest()
    sandbox = _FakeSandbox()
    reads: list[str] = []

    def read(path, format=None):
        reads.append(path)
        return b"aaaaa"

    sandbox.files.read = read
    sandbox.commands.run = (
        lambda command, cwd=None, timeout=None, envs=None, user=None: SimpleNamespace(
            exit_code=0,
            stdout=(
                '__SKILLS_META__[{"rel": "demo/run.py", "size": 5, "sha256": "'
                + remote_hash
                + '"}]\n'
            ),
            stderr="",
        )
    )

    synced = bash_sandbox._e2b_incremental_sync_skills(
        sandbox, "/home/user/workspace/sessions/s1", lib, {}
    )
    assert synced == []
    assert reads == []
    assert local.read_bytes() == b"aaaaa"


def test_e2b_skips_task_description_as_filename():
    long_name = f"{'中' * 80}.md"
    assert bash_sandbox._utf8_len(long_name) > bash_sandbox._E2B_MAX_NAME_BYTES
    assert not bash_sandbox._e2b_path_component_ok(long_name)
    assert not bash_sandbox._e2b_remote_path_ok(f"/home/user/workspace/{long_name}")

    tmp = Path(tempfile.mkdtemp())
    workspace = tmp / "ws"
    workspace.mkdir()
    (workspace / "ok.txt").write_bytes(b"1")
    (workspace / long_name).write_bytes(b"bad")
    sandbox = _FakeSandbox()

    bash_sandbox._e2b_upload_tree(
        sandbox,
        workspace,
        "/home/user/workspace",
        skip_top_dirs=set(),
        label="workspace",
    )
    assert sandbox.files.writes == ["/home/user/workspace/ok.txt"]


def test_e2b_file_upload_retries_transient_transport_errors():
    import httpx
    from reactor_tool.tool import e2b_file_upload

    for error in (
        httpx.RemoteProtocolError("peer closed connection"),
        httpx.ReadTimeout("operation timed out"),
    ):

        class FakeFiles:
            def __init__(self):
                self.calls = 0

            def write_files(self, files, **kwargs):
                self.calls += 1
                if self.calls < 3:
                    raise error

        files_api = FakeFiles()

        class FakeSandbox:
            files = files_api

        with (
            patch.object(e2b_file_upload.random, "uniform", return_value=0.0),
            patch.object(e2b_file_upload.time, "sleep") as sleep,
        ):
            bash_sandbox._e2b_write_files(
                FakeSandbox(), [{"path": "/home/user/workspace/a.txt", "data": b"a"}]
            )

        assert files_api.calls == 3
        assert sleep.call_count == 2


def test_e2b_file_upload_does_not_retry_non_transient_errors():
    class FakeFiles:
        def __init__(self):
            self.calls = 0

        def write_files(self, files, **kwargs):
            self.calls += 1
            raise ValueError("invalid file path")

    files_api = FakeFiles()

    class FakeSandbox:
        files = files_api

    from reactor_tool.tool import e2b_file_upload

    with patch.object(e2b_file_upload.time, "sleep") as sleep:
        try:
            bash_sandbox._e2b_write_files(
                FakeSandbox(), [{"path": "/home/user/workspace/a.txt", "data": b"a"}]
            )
        except ValueError:
            pass
        else:
            raise AssertionError("non-transient upload error should be raised")

    assert files_api.calls == 1
    sleep.assert_not_called()


def test_e2b_file_upload_passes_request_timeout():
    from reactor_tool.tool import e2b_file_upload

    captured: dict[str, object] = {}

    class FakeFiles:
        def write_files(self, files, **kwargs):
            captured["files"] = files
            captured.update(kwargs)

    e2b_file_upload.write_e2b_files(
        FakeFiles(),
        [{"path": "/home/user/workspace/a.txt", "data": b"a"}],
        label="bash_sandbox",
    )
    assert captured["request_timeout"] == 300.0


def test_e2b_file_upload_splits_by_payload_bytes():
    from reactor_tool.tool import e2b_file_upload

    calls: list[list[str]] = []

    class FakeFiles:
        def write_files(self, files, **kwargs):
            calls.append([item["path"] for item in files])

    items = [
        {"path": "/a", "data": b"x" * 200_000},
        {"path": "/b", "data": b"y" * 200_000},
        {"path": "/c", "data": b"z" * 200_000},
        {"path": "/big", "data": b"w" * 600_000},
    ]
    with patch.dict(os.environ, {"E2B_FILE_UPLOAD_MAX_BYTES": "512000"}, clear=False):
        e2b_file_upload.write_e2b_files(FakeFiles(), items, label="bash_sandbox")
    assert calls == [["/a", "/b"], ["/c"], ["/big"]]


def test_e2b_file_upload_retries_only_failed_chunk():
    import httpx
    from reactor_tool.tool import e2b_file_upload

    class FakeFiles:
        def __init__(self):
            self.calls: list[list[str]] = []

        def write_files(self, files, **kwargs):
            names = [item["path"] for item in files]
            self.calls.append(names)
            if names == ["/b"] and self.calls.count(["/b"]) < 3:
                raise httpx.RemoteProtocolError("peer closed connection")

    files_api = FakeFiles()
    items = [
        {"path": "/a", "data": b"x" * 200_000},
        {"path": "/b", "data": b"y" * 200_000},
    ]
    with (
        patch.dict(os.environ, {"E2B_FILE_UPLOAD_MAX_BYTES": "200000"}, clear=False),
        patch.object(e2b_file_upload.random, "uniform", return_value=0.0),
        patch.object(e2b_file_upload.time, "sleep"),
    ):
        e2b_file_upload.write_e2b_files(files_api, items, label="bash_sandbox")
    assert files_api.calls == [["/a"], ["/b"], ["/b"], ["/b"]]
