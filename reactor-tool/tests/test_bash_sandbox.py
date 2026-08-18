# -*- coding: utf-8 -*-
import asyncio
import os
import tempfile
import time
from pathlib import Path

from reactor_tool.model.protocal import BashSandboxRequest
from reactor_tool.tool import bash_sandbox
from reactor_tool.tool.bash_sandbox import run_bash_sandbox


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


def test_upload_tree_skips_skills_dir(tmp_path=None):
    tmp = Path(tempfile.mkdtemp())
    workspace = tmp / "ws"
    (workspace / "skills" / "demo").mkdir(parents=True)
    (workspace / "skills" / "demo" / "a.txt").write_text("skill", encoding="utf-8")
    (workspace / "out.txt").write_text("work", encoding="utf-8")

    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

    class FakeSandbox:
        files = FakeFiles()

    bash_sandbox._e2b_upload_tree(
        FakeSandbox(),
        workspace,
        "/home/user/workspace",
        skip_top_dirs={"skills"},
        label="workspace",
    )
    assert any(p.endswith("/out.txt") for p in written)
    assert not any("/skills/" in p for p in written)


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


def test_session_sandbox_reuses_and_incremental_push():
    """skill 模式同 session：只 create 一次；无变更 skip；改 skill 后只推脏文件。"""
    creates = {"n": 0}
    kills = {"n": 0}
    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

        def write(self, path, data):
            written.append(path)

        def read(self, path, format=None):
            return b""

    class FakeSandbox:
        def __init__(self):
            self.files = FakeFiles()
            self.commands = self

        def run(self, command, cwd=None, timeout=None):
            class R:
                exit_code = 0
                stdout = "ok\n"
                stderr = ""

            return R()

        def run_code(self, script, timeout=None):
            class Logs:
                stdout = ["__SKILLS_META__[]\n"]
                stderr = []

            class E:
                logs = Logs()
                text = ""
                error = None

            return E()

        def kill(self):
            kills["n"] += 1

        def set_timeout(self, sec):
            return None

    def fake_create(timeout_sec):
        creates["n"] += 1
        return FakeSandbox()

    prev_ttl = os.environ.get("BASH_SANDBOX_IDLE_TTL_SEC")
    os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = "300"
    bash_sandbox._shutdown_all_sessions()

    originals = {
        "_create_e2b_sandbox": bash_sandbox._create_e2b_sandbox,
        "_e2b_mkdir": bash_sandbox._e2b_mkdir,
        "_e2b_incremental_sync_skills": bash_sandbox._e2b_incremental_sync_skills,
    }
    bash_sandbox._create_e2b_sandbox = fake_create  # type: ignore
    bash_sandbox._e2b_mkdir = lambda *a, **k: None  # type: ignore
    bash_sandbox._e2b_incremental_sync_skills = lambda *a, **k: []  # type: ignore

    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"x")
    workspace = tmp / "ws"
    workspace.mkdir()
    (workspace / "a.txt").write_bytes(b"1")

    try:
        r1 = bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
        )
        n_after_first = len(written)
        r2 = bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
        )
        assert r1[0] == 0 and r2[0] == 0
        assert creates["n"] == 1, creates
        assert kills["n"] == 0
        assert n_after_first >= 2  # a.txt + SKILL.md
        assert len(written) == n_after_first  # 第二次无脏文件

        (lib / "demo" / "SKILL.md").write_bytes(b"changed")
        r3 = bash_sandbox._exec_e2b(
            "session-reuse-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
        )
        assert r3[0] == 0
        assert creates["n"] == 1
        assert len(written) == n_after_first + 1
        assert written[-1].endswith("/skills/demo/SKILL.md")
    finally:
        bash_sandbox._shutdown_all_sessions()
        for name, fn in originals.items():
            setattr(bash_sandbox, name, fn)
        if prev_ttl is None:
            os.environ.pop("BASH_SANDBOX_IDLE_TTL_SEC", None)
        else:
            os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = prev_ttl


def test_e2b_ephemeral_skips_skills_and_kills():
    """无 skills/：一次性沙箱，只推 workspace，跑完 kill，不进会话池。"""
    creates = {"n": 0}
    kills = {"n": 0}
    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

    class FakeSandbox:
        def __init__(self):
            self.files = FakeFiles()
            self.commands = self

        def run(self, command, cwd=None, timeout=None):
            class R:
                exit_code = 0
                stdout = "hi\n"
                stderr = ""

            return R()

        def kill(self):
            kills["n"] += 1

    def fake_ephemeral_create(timeout_sec):
        creates["n"] += 1
        return FakeSandbox()

    bash_sandbox._shutdown_all_sessions()
    originals = {
        "_create_ephemeral_e2b_sandbox": bash_sandbox._create_ephemeral_e2b_sandbox,
        "_e2b_mkdir": bash_sandbox._e2b_mkdir,
    }
    bash_sandbox._create_ephemeral_e2b_sandbox = fake_ephemeral_create  # type: ignore
    bash_sandbox._e2b_mkdir = lambda *a, **k: None  # type: ignore

    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"skill-bytes")
    workspace = tmp / "ws"
    workspace.mkdir()
    (workspace / "a.txt").write_bytes(b"1")

    try:
        r1 = bash_sandbox._exec_e2b(
            "session-ephemeral-1", "echo hi", workspace, lib, set(), 30, 64000
        )
        r2 = bash_sandbox._exec_e2b(
            "session-ephemeral-1", "echo hi2", workspace, lib, set(), 30, 64000
        )
        assert r1[0] == 0 and r2[0] == 0
        assert creates["n"] == 2, creates
        assert kills["n"] == 2, kills
        assert "session-ephemeral-1" not in bash_sandbox._pool
        assert not bash_sandbox._session_in_skill_mode("session-ephemeral-1")
        assert all("/skills/" not in p for p in written)
        assert any(p.endswith("/a.txt") for p in written)
    finally:
        bash_sandbox._shutdown_all_sessions()
        for name, fn in originals.items():
            setattr(bash_sandbox, name, fn)


def test_e2b_skill_mode_sticky_upgrades_session():
    """首次命中 skills/ 后强粘性：后续 echo 也走会话池并继续推/扫 skills。"""
    pool_creates = {"n": 0}
    ephemeral_creates = {"n": 0}
    kills = {"n": 0}
    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

        def read(self, path, format=None):
            return b""

    class FakeSandbox:
        def __init__(self):
            self.files = FakeFiles()
            self.commands = self

        def run(self, command, cwd=None, timeout=None):
            class R:
                exit_code = 0
                stdout = "ok\n"
                stderr = ""

            return R()

        def run_code(self, script, timeout=None):
            class Logs:
                stdout = ["__SKILLS_META__[]\n"]
                stderr = []

            class E:
                logs = Logs()
                text = ""
                error = None

            return E()

        def kill(self):
            kills["n"] += 1

        def set_timeout(self, sec):
            return None

    def fake_pool_create(timeout_sec):
        pool_creates["n"] += 1
        return FakeSandbox()

    def fake_ephemeral_create(timeout_sec):
        ephemeral_creates["n"] += 1
        return FakeSandbox()

    prev_ttl = os.environ.get("BASH_SANDBOX_IDLE_TTL_SEC")
    os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = "300"
    bash_sandbox._shutdown_all_sessions()

    originals = {
        "_create_e2b_sandbox": bash_sandbox._create_e2b_sandbox,
        "_create_ephemeral_e2b_sandbox": bash_sandbox._create_ephemeral_e2b_sandbox,
        "_e2b_mkdir": bash_sandbox._e2b_mkdir,
        "_e2b_incremental_sync_skills": bash_sandbox._e2b_incremental_sync_skills,
    }
    bash_sandbox._create_e2b_sandbox = fake_pool_create  # type: ignore
    bash_sandbox._create_ephemeral_e2b_sandbox = fake_ephemeral_create  # type: ignore
    bash_sandbox._e2b_mkdir = lambda *a, **k: None  # type: ignore
    bash_sandbox._e2b_incremental_sync_skills = lambda *a, **k: []  # type: ignore

    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"x")
    workspace = tmp / "ws"
    workspace.mkdir()
    (workspace / "a.txt").write_bytes(b"1")

    try:
        # 1) 无 skills/ → 一次性
        r0 = bash_sandbox._exec_e2b(
            "session-sticky-1", "echo before", workspace, lib, set(), 30, 64000
        )
        assert r0[0] == 0
        assert ephemeral_creates["n"] == 1
        assert pool_creates["n"] == 0
        assert kills["n"] == 1
        assert not bash_sandbox._session_in_skill_mode("session-sticky-1")

        # 2) 命中 skills/ → 升级进池
        r1 = bash_sandbox._exec_e2b(
            "session-sticky-1",
            "python skills/demo/scripts/run.py",
            workspace,
            lib,
            set(),
            30,
            64000,
        )
        assert r1[0] == 0
        assert bash_sandbox._session_in_skill_mode("session-sticky-1")
        assert pool_creates["n"] == 1
        assert ephemeral_creates["n"] == 1
        assert any("/skills/demo/SKILL.md" in p for p in written)
        n_after_upgrade = len(written)

        # 3) 强粘性：即使 echo 也复用池，不再建一次性
        r2 = bash_sandbox._exec_e2b(
            "session-sticky-1", "echo after", workspace, lib, set(), 30, 64000
        )
        assert r2[0] == 0
        assert pool_creates["n"] == 1
        assert ephemeral_creates["n"] == 1
        assert kills["n"] == 1  # 只有第一次 ephemeral kill
        assert "session-sticky-1" in bash_sandbox._pool
        assert len(written) == n_after_upgrade  # 无脏文件不再上传
    finally:
        bash_sandbox._shutdown_all_sessions()
        for name, fn in originals.items():
            setattr(bash_sandbox, name, fn)
        if prev_ttl is None:
            os.environ.pop("BASH_SANDBOX_IDLE_TTL_SEC", None)
        else:
            os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = prev_ttl


def test_incremental_push_helpers_unit():
    uploaded: dict = {}
    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

    class FakeSandbox:
        files = FakeFiles()

    tmp = Path(tempfile.mkdtemp())
    ws = tmp / "ws"
    ws.mkdir()
    (ws / "a.txt").write_bytes(b"1")
    lib = tmp / "lib"
    (lib / "demo").mkdir(parents=True)
    (lib / "demo" / "SKILL.md").write_bytes(b"s")

    up1, sk1 = bash_sandbox._e2b_push_workspace_incremental(
        FakeSandbox(), ws, "/home/user/workspace", uploaded
    )
    up2, sk2 = bash_sandbox._e2b_push_workspace_incremental(
        FakeSandbox(), ws, "/home/user/workspace", uploaded
    )
    assert up1 == 1 and sk1 == 0
    assert up2 == 0 and sk2 == 1

    up3, sk3 = bash_sandbox._e2b_push_skills_incremental(
        FakeSandbox(), lib, "/home/user/workspace", set(), uploaded
    )
    up4, sk4 = bash_sandbox._e2b_push_skills_incremental(
        FakeSandbox(), lib, "/home/user/workspace", set(), uploaded
    )
    assert up3 == 1 and sk3 == 0
    assert up4 == 0 and sk4 == 1


def test_e2b_skips_task_description_as_filename():
    """LLM 任务描述拼进文件名时，按 UTF-8 字节超限跳过，不打爆 write_files。"""
    long_stem = "相关工具检索，重点找：基本信息、教育、实习（群杰物联）各模块与指标、开源AstrBot、个人项目Reactor、技能栈、求职意向。返回一份精炼但完整的中文结构化要点（含可讲的数字指标），不要生成文件。"
    long_name = f"{long_stem}的多模态检索结果.md"
    assert bash_sandbox._utf8_len(long_name) > bash_sandbox._E2B_MAX_NAME_BYTES
    assert not bash_sandbox._e2b_path_component_ok(long_name)
    assert not bash_sandbox._e2b_remote_path_ok(f"/home/user/workspace/{long_name}")

    written: list[str] = []

    class FakeFiles:
        def write_files(self, files):
            for item in files:
                written.append(item["path"])

    class FakeSandbox:
        files = FakeFiles()

    tmp = Path(tempfile.mkdtemp())
    ws = tmp / "ws"
    ws.mkdir()
    (ws / "ok.txt").write_bytes(b"1")
    (ws / long_name).write_bytes(b"bad")

    up, skipped = bash_sandbox._e2b_push_workspace_incremental(
        FakeSandbox(), ws, "/home/user/workspace", {}
    )
    assert up == 1
    assert skipped >= 1
    assert written == ["/home/user/workspace/ok.txt"]
    assert all(long_stem[:20] not in p for p in written)


def test_idle_ttl_reaps_session():
    kills = {"n": 0}

    class FakeSandbox:
        def kill(self):
            kills["n"] += 1

    bash_sandbox._shutdown_all_sessions()
    entry = bash_sandbox._SessionSandbox(
        session_id="sess-ttl",
        sandbox=FakeSandbox(),
        remote_root="/home/user/workspace",
        last_used_at=time.time() - 400,
    )
    with bash_sandbox._pool_guard:
        bash_sandbox._pool["sess-ttl"] = entry

    prev = os.environ.get("BASH_SANDBOX_IDLE_TTL_SEC")
    os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = "300"
    try:
        bash_sandbox._reap_idle_sessions()
        assert "sess-ttl" not in bash_sandbox._pool
        assert kills["n"] == 1
    finally:
        bash_sandbox._shutdown_all_sessions()
        if prev is None:
            os.environ.pop("BASH_SANDBOX_IDLE_TTL_SEC", None)
        else:
            os.environ["BASH_SANDBOX_IDLE_TTL_SEC"] = prev


if __name__ == "__main__":
    test_local_link_skills_and_run()
    test_incremental_write_skill_file_skips_same_content()
    test_upload_tree_skips_skills_dir()
    test_backend_selection_uses_config()
    test_command_needs_skills_heuristic()
    test_session_sandbox_reuses_and_incremental_push()
    test_e2b_ephemeral_skips_skills_and_kills()
    test_e2b_skill_mode_sticky_upgrades_session()
    test_incremental_push_helpers_unit()
    test_e2b_skips_task_description_as_filename()
    test_idle_ttl_reaps_session()
    print("OK")
