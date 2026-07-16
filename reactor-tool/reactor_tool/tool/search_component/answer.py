# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/9
# =====================
"""DeepSearch 最终回答：基于检索材料流式生成答案。"""
import os
import time

from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.llm_util import resolve_openai_compat_env
from reactor_tool.util.log_util import timer
from reactor_tool.util.prompt_util import get_prompt


@timer()
async def answer_question(query: str, search_content: str):
    """将 query + 检索材料拼入 answer_prompt，流式 yield 答案片段。"""
    prompt_template = get_prompt("deepsearch")["answer_prompt"]

    llm_config = resolve_openai_compat_env("DEEPSEARCH")
    model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")
    answer_length = os.getenv("SEARCH_ANSWER_LENGTH", "3000")

    prompt = prompt_template.format(
        query=query,
        sub_qa=search_content,
        current_time=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        response_length=answer_length
    )
    async for chunk in ask_llm(
            messages=prompt,
            model=model,
            stream=True,
            only_content=True,  # 只返回内容
            api_base=llm_config["api_base"],
            api_key=llm_config["api_key"],
    ):
        if chunk:
            yield chunk


if __name__ == "__main__":
    pass
