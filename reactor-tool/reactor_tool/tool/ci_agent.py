# -*- coding: utf-8 -*-
"""代码解释器 Agent（CIAgent）：基于 smolagents.CodeAgent 的定制执行循环。

负责：代码解析、权限校验、运行时 I/O 守卫、最终答案判定、流式步骤产出。
"""
import ast
import json
import os
import re
import time
from collections.abc import Callable, Generator
from typing import Any, Optional
import uuid
from smolagents import (
    CodeAgent,
    ChatMessage,
    MessageRole,
    AgentGenerationError,
    BASE_BUILTIN_MODULES,
    LogLevel,
    AgentParsingError,
    fix_final_answer_code,
    parse_code_blobs,
    AgentExecutionError,
    ToolCall,
    truncate_content,
    YELLOW_HEX,
    ActionOutput,
    Model,
    Tool,
    PromptTemplates,
    ActionStep,
    ChatMessageStreamDelta,
    agglomerate_stream_deltas,
    ToolOutput,
)
from loguru import logger as lg
from rich.text import Text
from rich.console import Group
from rich.live import Live
from rich.markdown import Markdown
import json_repair

from reactor_tool.model.code import CodeOuput
from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    CodeInterpreterPermissionPolicy,
)
from reactor_tool.tool.code_interpreter_runtime_guard import (
    extract_runtime_permission_error,
)
from reactor_tool.tool.python_sandbox_executor import PythonSandboxExecutor
from reactor_tool.tool.final_answer_check import FinalAnswerCheck
from reactor_tool.util.file_util import generate_data_id
from reactor_tool.util.log_util import timer


# 默认禁止导入的高风险模块
BLOCKED_IMPORT_MODULES = {
    "ctypes",
    "os",
    "pickle",
    "shutil",
    "subprocess",
    "xlrd",
}
# 默认禁止的动态执行函数
BLOCKED_FUNCTION_CALLS = {"eval", "exec"}


def _force_headless_matplotlib(code: str) -> str:
    """
    在服务端执行绘图代码时，强制使用无界面的 Agg 后端，
    避免 Windows 下 Tk 后端在线程回收时触发 tkinter 异常。
    """
    if "matplotlib" not in code:
        return code

    if 'matplotlib.use("Agg")' in code or "matplotlib.use('Agg')" in code:
        return code

    lines = code.splitlines()
    insert_index = 0
    while insert_index < len(lines):
        stripped = lines[insert_index].strip()
        if not stripped or stripped.startswith("#"):
            insert_index += 1
            continue
        break

    patch_lines = [
        "import matplotlib",
        "matplotlib.use('Agg')",
    ]
    lines[insert_index:insert_index] = patch_lines
    return "\n".join(lines)


def _scan_unsafe_code(code: str) -> list[str]:
    issues: list[str] = []
    try:
        tree = ast.parse(code)
    except SyntaxError:
        return issues

    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                root_module = alias.name.split(".")[0]
                if root_module in BLOCKED_IMPORT_MODULES:
                    issues.append(f"import {alias.name}")
        elif isinstance(node, ast.ImportFrom):
            module = node.module or ""
            root_module = module.split(".")[0]
            if root_module in BLOCKED_IMPORT_MODULES:
                issues.append(f"from {module} import ...")
        elif isinstance(node, ast.Call):
            if isinstance(node.func, ast.Name) and node.func.id in BLOCKED_FUNCTION_CALLS:
                issues.append(f"call {node.func.id}()")

    return sorted(set(issues))


def _extract_thought_text(output_text: str) -> str:
    """从 LLM 输出中剥离代码块，保留思考/任务描述供 SSE 过程展示。"""
    if not output_text:
        return ""
    text = output_text.replace("Thought:", "\n")
    text = re.sub(r"<code>.*?</code>", "", text, flags=re.S | re.I)
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    return text.strip()


def _format_permission_error_for_agent(error: CodeExecutionPermissionError) -> str:
    """把权限拒绝错误整理成便于 agent 下一步自修的 observation。"""
    lines = [
        f"代码执行权限校验失败：{error}",
        f"blocked_reason={error.blocked_reason}",
    ]
    if error.detail and error.detail != str(error):
        lines.append(f"detail={error.detail}")
    if error.policy is not None:
        lines.append(f"permission_profile={error.policy.profile}")
        if error.policy.input_file_paths:
            lines.append(f"allowed_input_files={sorted(error.policy.input_file_paths)}")
        lines.append(
            "修正建议：优先使用 resolve_input_path('文件名') 读取输入文件，"
            "优先使用 build_output_path('中文文件名.xlsx') 输出文件；"
            "如果继续使用 pathlib/pandas/savefig 的直接路径写法，最终路径也必须落在授权目录内。"
        )
    else:
        lines.append(
            "修正建议：优先使用 resolve_input_path('文件名') 读取输入文件，"
            "优先使用 build_output_path('中文文件名.xlsx') 输出文件；"
            "如果继续使用 pathlib/pandas/savefig 的直接路径写法，最终路径也必须落在授权目录内。"
        )
    lines.append("请改写相关文件 I/O 代码后再执行，不要继续使用越权路径。")
    return "\n".join(lines)


def _should_use_live_stream_render(console) -> bool:
    """
    仅在真正可交互终端里启用 Rich Live。
    否则 Live.update() 会在日志/非 TTY 环境中把每次刷新都落成整段文本快照，导致 Task/Thought 重复刷屏。
    """
    return bool(getattr(console, "is_terminal", False) and getattr(console, "is_interactive", False))


def _extract_incremental_stream_text(rendered_text: str, chunk_text: str) -> tuple[str, str]:
    """
    兼容两类上游流：
    1. 真正的增量 token，如 "你好" -> "，世界"
    2. 累计快照，如 "Task..." -> "Task...Thought..." -> "Task...Thought...Code..."

    返回值:
    - 第一个元素：本次真正需要打印的新增文本
    - 第二个元素：更新后的已渲染全文
    """
    if not chunk_text:
        return "", rendered_text

    if not rendered_text:
        return chunk_text, chunk_text

    if chunk_text == rendered_text:
        return "", rendered_text

    # 上游直接返回“截至当前的完整内容”，只打印新增后缀。
    if chunk_text.startswith(rendered_text):
        return chunk_text[len(rendered_text):], chunk_text

    # 常规增量流或存在边界重叠时，按“已渲染后缀”和“当前块前缀”做最大重叠匹配。
    max_overlap = min(len(rendered_text), len(chunk_text))
    for overlap in range(max_overlap, 0, -1):
        if rendered_text.endswith(chunk_text[:overlap]):
            incremental = chunk_text[overlap:]
            return incremental, rendered_text + incremental

    return chunk_text, rendered_text + chunk_text


class CIAgent(CodeAgent):
    """定制 CodeAgent：权限校验、运行时 I/O 守卫、最终答案判定、流式步骤。"""

    def __init__(
        self,
        tools: list[Tool],
        model: Model,
        prompt_templates: PromptTemplates | None = None,
        additional_authorized_imports: list[str] | None = None,
        planning_interval: int | None = None,
        executor_type: str | None = "local",
        executor_kwargs: dict[str, Any] | None = None,
        grammar: dict[str, str] | None = None,
        output_dir: Optional[str] = None,
        before_execute: Optional[Callable[[str], None]] = None,
        runtime_variables: Optional[dict[str, Any]] = None,
        runtime_permission_policy: CodeInterpreterPermissionPolicy | None = None,
        *args,
        **kwargs,
    ):
        self.output_dir = output_dir  # 产物输出目录
        self.before_execute = before_execute  # 执行前钩子（静态权限校验等）
        self.runtime_permission_policy = runtime_permission_policy
        self.sandbox_executor = (
            PythonSandboxExecutor(runtime_permission_policy)
            if runtime_permission_policy is not None
            else None
        )
        super().__init__(
            tools=tools,
            model=model,
            prompt_templates=prompt_templates,
            grammar=grammar,
            planning_interval=planning_interval,
            additional_authorized_imports=additional_authorized_imports,
            executor_type=executor_type,
            executor_kwargs=executor_kwargs,
            **kwargs,
        )
    def get_produced_files(self) -> list[dict[str, Any]]:
        if self.sandbox_executor is None:
            return []
        return self.sandbox_executor.produced_files()

    def close_sandbox(self) -> None:
        if self.sandbox_executor is not None:
            self.sandbox_executor.close()

    @timer()
    def _step_stream(
        self, memory_step: ActionStep
    ) -> Generator[
        ChatMessageStreamDelta | ToolCall | ToolOutput | ActionOutput | CodeOuput
    ]:
        """
        Perform one step in the ReAct framework: the agent thinks, acts, and observes the result.
        Returns None if the step is not final.
        """
        memory_messages = self.write_memory_to_messages()

        self.input_messages = memory_messages.copy()

        # Add new step in logs
        memory_step.model_input_messages = memory_messages.copy()
        stream_rendered = False
        try:
            input_messages = memory_messages.copy()

            model_request_id = str(uuid.uuid4())

            output_stream = self.model.generate_stream(
                    input_messages,
                    extra_headers={"x-ms-client-request-id": model_request_id},
                )
            chat_message_stream_deltas: list[ChatMessageStreamDelta] = []
            # 终端内实时渲染 Thought/Code，是否继续向外透传由上层路由决定。
            if _should_use_live_stream_render(self.logger.console):
                with Live("", console=self.logger.console, vertical_overflow="visible") as live:
                    for event in output_stream:
                        chat_message_stream_deltas.append(event)
                        live.update(
                            Markdown(
                                agglomerate_stream_deltas(
                                    chat_message_stream_deltas
                                ).render_as_markdown()
                            )
                        )
                        stream_rendered = True
                        yield event
                if stream_rendered:
                    self.logger.console.print()
            else:
                rendered_stream_text = ""
                for event in output_stream:
                    chat_message_stream_deltas.append(event)
                    delta_content = getattr(event, "content", "")
                    incremental_content, rendered_stream_text = _extract_incremental_stream_text(
                        rendered_stream_text,
                        delta_content,
                    )
                    if incremental_content:
                        self.logger.console.print(
                            incremental_content,
                            end="",
                            markup=False,
                            highlight=False,
                            soft_wrap=True,
                        )
                        stream_rendered = True
                    yield event
                if stream_rendered:
                    self.logger.console.print()
            chat_message = agglomerate_stream_deltas(chat_message_stream_deltas)
            memory_step.model_output_message = chat_message
            output_text = chat_message.content
            memory_step.model_output_message = chat_message
            output_text = chat_message.content

            # This adds <end_code> sequence to the history.
            # This will nudge ulterior LLM calls to finish with <end_code>, thus efficiently stopping generation.
            if output_text and output_text.strip().endswith("```"):
                output_text += "<end_code>"
                memory_step.model_output_message.content = output_text

            memory_step.model_output = output_text
            # This put call was missing await

        except Exception as e:
            raise AgentGenerationError(
                f"Error in generating model output:\n{e}", self.logger
            ) from e

        if not stream_rendered:
            self.logger.log_markdown(
                content=output_text,
                title="Output message of the LLM:",
                level=LogLevel.DEBUG,
            )

        # Parse
        try:
            code_action = fix_final_answer_code(parse_code_blobs(output_text))
        except Exception as e:
            error_msg = (
                f"Error in code parsing:\n{e}\nMake sure to provide correct code blobs."
            )
            raise AgentParsingError(error_msg, self.logger)

        code_action = _force_headless_matplotlib(code_action)

        memory_step.tool_calls = [
            ToolCall(
                name="python_interpreter",
                arguments=code_action,
                id=f"call_{len(self.memory.steps)}",
            )
        ]

        # Execute
        self.logger.log_code(
            title="Executing parsed code:", content=code_action, level=LogLevel.INFO
        )

        try:
            if self.before_execute is not None:
                self.before_execute(code_action)
        except CodeExecutionPermissionError as exc:
            observation = _format_permission_error_for_agent(exc)
            memory_step.observations = observation
            raise AgentExecutionError(observation, self.logger) from exc

        unsafe_issues = _scan_unsafe_code(code_action)
        if unsafe_issues:
            raise AgentExecutionError(
                "Unsafe code blocked: "
                + ", ".join(unsafe_issues)
                + ". Disallowed imports: os, shutil, subprocess, pickle, xlrd, ctypes; disallowed calls: eval, exec.",
                self.logger,
            )

        try:
            if self.sandbox_executor is not None:
                sandbox_result = self.sandbox_executor.execute(code_action)
                execution_logs = sandbox_result.stdout
                if sandbox_result.stderr:
                    execution_logs = f"{execution_logs}\n{sandbox_result.stderr}".strip()
            else:
                _, execution_logs, _ = self.python_executor(code_action)

            # This put call was missing await
            execution_outputs_console = []
            if len(execution_logs) > 0:
                execution_outputs_console += [
                    Text("Execution logs:", style="bold"),
                    Text(execution_logs),
                ]

            observation = "Execution logs:\n" + execution_logs
            if matcher := re.search(r"Task:\s?(.*)", output_text):
                file_name = f"{matcher.group(1).replace(' ', '')}.py"
            else:
                file_name = f'{generate_data_id("index")}.py'
            thought = _extract_thought_text(output_text or "")
            yield CodeOuput(
                code=code_action,
                file_name=file_name,
                thought=thought,
                execution_logs=execution_logs or "",
            )
        except Exception as e:
            permission_error = extract_runtime_permission_error(str(e))
            if permission_error is not None:
                observation = _format_permission_error_for_agent(permission_error)
                memory_step.observations = observation
                raise AgentExecutionError(observation, self.logger) from e
            if self.sandbox_executor is None and (
                hasattr(self.python_executor, "state")
                and "_print_outputs" in self.python_executor.state
            ):
                execution_logs = str(self.python_executor.state["_print_outputs"])
                if len(execution_logs) > 0:
                    execution_outputs_console = [
                        Text("Execution logs:", style="bold"),
                        Text(execution_logs),
                    ]
                    memory_step.observations = "Execution logs:\n" + execution_logs
                    self.logger.log(
                        Group(*execution_outputs_console), level=LogLevel.INFO
                    )
            error_msg = str(e)

            if "Import of " in error_msg and " is not allowed" in error_msg:
                self.logger.log(
                    "[bold red]Warning to user: Code execution failed due to an unauthorized import - Consider passing said import under `additional_authorized_imports` when initializing your CodeAgent.",
                    level=LogLevel.INFO,
                )
            raise AgentExecutionError(error_msg, self.logger)

        memory_step.observations = observation

        finalObj = FinalAnswerCheck(
            input_messages=self.input_messages,
            execution_logs=execution_logs,
            model=self.model,
            task=self.task,
            prompt_temps=self.prompt_templates,
            memory_step=memory_step,
            grammar=self.grammar,
            request_id=f"{model_request_id}-final",
        )
        finalFlag, exeLog = finalObj.check_is_final_answer()
        self.logger.log(Group(*execution_outputs_console), level=LogLevel.INFO)
        # self.logger.log(f"check finalanswer 已完成 {finalFlag}  {str(exeLog)}")
        memory_step.action_output = exeLog

        yield ActionOutput(output=exeLog, is_final_answer=finalFlag)
