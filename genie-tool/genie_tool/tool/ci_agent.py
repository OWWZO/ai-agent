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

from genie_tool.model.code import CodeOuput
from genie_tool.tool.final_answer_check import FinalAnswerCheck
from genie_tool.util.file_util import generate_data_id
from genie_tool.util.log_util import timer


class CIAgent(CodeAgent):
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
        *args,
        **kwargs,
    ):
        self.output_dir = output_dir
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
        try:
            input_messages = memory_messages.copy()

            model_request_id = str(uuid.uuid4())

            output_stream = self.model.generate_stream(
                    input_messages,
                    extra_headers={"x-ms-client-request-id": model_request_id},
                )
            chat_message_stream_deltas: list[ChatMessageStreamDelta] = []
            with Live("", console=self.logger.console, vertical_overflow="visible") as live:
                for event in output_stream:
                    chat_message_stream_deltas.append(event)
                    live.update(
                        Markdown(agglomerate_stream_deltas(chat_message_stream_deltas).render_as_markdown())
                    )
                    yield event
            chat_message = agglomerate_stream_deltas(chat_message_stream_deltas)
            memory_step.model_output_message = chat_message
            output_text = chat_message.content
            self.logger.log_markdown(
                content=output_text,
                title="Output message of the LLM:",
                level=LogLevel.DEBUG,
            )
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
            yield CodeOuput(code=code_action,file_name=file_name)
        except Exception as e:
            if (
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

