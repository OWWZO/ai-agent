# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/9
# =====================
import os
import time

from genie_tool.util.llm_util import ask_llm
from genie_tool.util.log_util import timer
from genie_tool.util.prompt_util import get_prompt


@timer()
async def answer_question(query: str, search_content: str):
    prompt_template = get_prompt("deepsearch")["answer_prompt"]

    model = os.getenv("SEARCH_ANSWER_MODEL", "gpt-4.1")
    answer_length = os.getenv("SEARCH_ANSWER_LENGTH", "10000")

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
    ):
        if chunk:
            yield chunk


if __name__ == "__main__":
    pass
