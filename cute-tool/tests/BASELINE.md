# cute-tool 测试基线

> 记录时间：2026-10-21。用于验证代码重构/清理**不改变原有行为**。
> 所有数字来自同一套命令，改动前后必须逐条一致才算无回归。

## 运行方式

```bash
cd cute-tool
uv sync --group dev

# 冒烟/日常：全量跑（约 2 分 30 秒）
.venv/Scripts/python -m pytest -q -p no:cacheprovider tests/ ^
  --deselect tests/test_grok_channel.py::GrokLiveProbeTest::test_live_grok_channel ^
  --deselect tests/test_grok_channel.py::GrokAskLlmLiveTest::test_live_ask_llm_report_model ^
  --deselect tests/test_mrag_image_retriever.py ^
  --deselect tests/test_mrag_eval_trace.py
```

`--deselect` 的 4 项都需要访问**外部模型/服务**，在离线环境下会挂住或超时：
`test_grok_channel` 的两个 live probe 要真调 grok-4.5；`test_mrag_image_retriever` /
`test_mrag_eval_trace` 要真连 Qdrant + 模型。

## 基线结果（改造前）

```
6 failed, 304 passed, 2 skipped, 17 deselected
```

### 允许存在的失败（6 项）

| 测试 | 类型 | 原因 |
|---|---|---|
| `test_grok_channel.py::ModelNotFoundFallbackGuardTest::test_model_not_found_does_not_switch_to_fallback_model` | 环境 | 测试桩只塞了 2 个 503 响应，但 `ask_llm` 内部按指数退避重试到第 7 次才放弃；桩耗尽后打到了真实网络，触发两次 live 探测。重试次数由 `LLM_MAX_RETRIES` 默认 2 决定，与桩的供给量不匹配。已用 `git stash` 隔离验证过：**改动前同样失败**。 |
| `test_mrag_api.py::MragApiTest::test_should_return_explicit_failure_chunk_when_upstream_error` | 环境 | mock 的 SSE 响应缺 `choices` 字段，被测代码按真实协议取 `choices[0].delta.content`。 |
| `test_code_interpreter_runtime_guard.py::...::test_guard_should_normalize_relative_output_write_for_pathlib` | 环境 | 中文文件名 + guard 路径规范化交互；单独跑也失败，与 pytest 无关。 |
| `test_python_sandbox_executor.py` 3 项（`ptp` / `cwd_for_analysis` / `cwd_for_workspace`） | **Windows 文件锁** | 见下 |

**额外发现**：`test_python_sandbox_executor` 的失败项**随同批测试组合漂移**。它与
`test_e2b_sandbox_backend` 同批时会失败 6 项（`ndarray_ptp` / `roundtrip_non_ascii` /
`sanitize_surrogate_stdout` / `cwd_for_workspace` 也会挂），单独跑必定 9/9 通过。根因是
Windows 下 `tempfile.TemporaryDirectory.cleanup()` 抛
`PermissionError: [WinError 32] 另一个程序正在使用此文件` —— E2B 测试留下的子进程 cwd
还锁着临时目录，另一组测试同时 `rglob` + `stat` 时读到已删/半删的树。属于既有的
**环境性 flaky**，不是被测代码的缺陷；不要用它判定功能回归。

### 测试基建问题（已于本次修复）

| 问题 | 修复 |
|---|---|
| 两个测试在**模块导入期**替换 `sys.modules["cute_tool.util.llm_util"]` 成最小桩且从不还原，污染同进程后续导入。此前 `pytest --collect-only` 直接报 2 个 `ImportError` 无法收集 | `test_deepsearch_query_process.py` 改为对真实 `resolve_openai_compat_env` 打 `patch.dict(os.environ, ...)` 并新增一条"前缀优先于全局"的断言；`test_code_interpreter_outputs.py` 的桩改为只在 `import code_interpreter` 期间生效、`finally` 里还原 |
| `pyproject.toml` 无测试依赖，`import pytest` 的两个模块必然 ImportError | 新增 `[dependency-groups] dev = ["pytest>=8.0", "pytest-asyncio>=0.24"]`，`uv sync --group dev` |

## 全量跑 vs unittest 的差异

`python -m unittest discover` 与 pytest 的测试**发现范围不同**：unittest 需要显式
`_test` 后缀命名匹配，且 `pytest.raises` / 裸函数（无 `TestCase` 包装）不会被收集。
上面 315→329 的增量来自 `test_deepsearch_query_process.py` 的 2 个裸 `test_*` 函数
和 `test_embedding_proxy.py` 的 13 个。**改造后的回归判定请统一用 pytest 命令**，不要
混用两种运行器。
