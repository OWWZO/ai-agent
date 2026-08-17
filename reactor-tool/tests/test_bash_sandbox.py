# -*- coding: utf-8 -*-
import asyncio
import tempfile
from pathlib import Path

from reactor_tool.model.protocal import BashSandboxRequest
from reactor_tool.tool import bash_sandbox
from reactor_tool.tool.bash_sandbox import run_bash_sandbox


def test_materialize_and_cleanup():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    skill = lib / "demo"
    skill.mkdir(parents=True)
    (skill / "SKILL.md").write_text("hello", encoding="utf-8")
    (skill / "scripts").mkdir()
    (skill / "scripts" / "run.py").write_text("print(1)\n", encoding="utf-8")

    workspace = tmp / "session_ws"
    workspace.mkdir()

    # PATH 已由沙箱注入当前解释器目录，直接 python 即可
    body = BashSandboxRequest(
        requestId="sess-1",
        command="python skills/demo/scripts/run.py",
        workspaceRoot=str(workspace),
        skillLibraryRoot=str(lib),
        timeoutSeconds=30,
    )
    result = asyncio.run(run_bash_sandbox(body))

    assert result.skills_materialized == ["demo"]
    assert not (workspace / "skills").exists()
    assert result.exit_code == 0, (result.exit_code, result.stdout, result.stderr)
    assert "1" in (result.stdout or "")


def test_sync_back_only_skills():
    tmp = Path(tempfile.mkdtemp())
    lib = tmp / "runtime_skills"
    lib.mkdir()
    workspace = tmp / "session_ws"
    sandbox_skill = workspace / "skills" / "demo" / "scripts"
    sandbox_skill.mkdir(parents=True)
    (sandbox_skill / "run.py").write_text("print(2)\n", encoding="utf-8")
    (workspace / "noise.txt").write_text("x", encoding="utf-8")

    synced = bash_sandbox._sync_back_skills(workspace, lib)
    assert synced == ["demo"]
    assert (lib / "demo" / "scripts" / "run.py").read_text(encoding="utf-8") == "print(2)\n"
    assert not (lib / "noise.txt").exists()


def test_backend_selection_uses_config(monkeypatch=None):
    """local backend path still works; e2b selected when env set (smoke without real E2B)."""
    from reactor_tool.tool.sandbox_backend_config import get_sandbox_backend
    import os

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


if __name__ == "__main__":
    test_materialize_and_cleanup()
    test_sync_back_only_skills()
    test_backend_selection_uses_config()
    print("OK")

