# -*- coding: utf-8 -*-
"""代码解释器最终答案判定。

用 LLM 判断当前步骤是否已完成任务（is_final），避免 Agent 过早/过晚收口。
"""
from openai import AsyncOpenAI

from smolagents import ChatMessage
from smolagents import ActionStep
from typing import Callable, List, Dict, Optional
from smolagents import MessageRole
import json_repair
from loguru import logger as lg


class FinalAnswerCheck(object):
    """基于 prompt 模板与执行日志，调用模型判定是否 final answer。"""

    def __init__(
        self,
        input_messages,
        execution_logs,
        model: Callable[[List[Dict[str, str]]], ChatMessage],
        task: str,
        request_id,
        prompt_temps,
        memory_step: ActionStep,
        grammar: Optional[Dict[str, str]] = None,
    ):
        self.model = model
        additional_args = {"grammar": grammar} if grammar is not None else {}
        self.additional_args = additional_args
        self.memory_step = memory_step
        # 拷贝历史消息（后续会拼接 final_answer 模板）
        self.input_messages = input_messages
        self.prompt_templates = prompt_temps
        self.task = task
        self.execution_logs = execution_logs
        self.request_id = request_id

    def check_is_final_answer(self):
        """返回 (is_final, logs_or_None)。"""
        # 去掉 system 消息，只保留对话轨迹
        memory_lines = self.input_messages[2:]
        memory_lines.extend(self.memory_step.to_messages())
        final_answer = self.prompt_templates["final_answer"]
        system_line = ChatMessage.from_dict({
            "role": MessageRole.SYSTEM,
            "content": final_answer["pre_messages"].replace("{{task}}", self.task),
        })
        user_line = ChatMessage.from_dict({
            "role": MessageRole.USER,
            "content": [
                {
                    "type": "text",
                    "text": final_answer["post_messages"].replace(
                        "{{task}}", self.task
                    ),
                }
            ],
        })
        inputs = [system_line]
        for input_ in memory_lines:
            inputs.append(input_)
        inputs.append(user_line)
        chat_message: ChatMessage = self.model.generate(
            inputs, extra_headers={"x-ms-client-request-id": self.request_id},
        )
        obj = json_repair.loads(chat_message.content)
        if obj == None or obj == "":
            return False, None
        if isinstance(obj, dict) and obj["is_final"] == True:
            return True, self.execution_logs
        if isinstance(obj, list):
            for o in obj:
                if hasattr(o, "is_final") and o["is_final"] == True:
                    return True, self.execution_logs
        return False, None

    def __name__(self):
        return "FinalAnswerCheck"
