# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/9
# =====================
import json
import os
import time
from json_repair import repair_json

from genie_tool.util.llm_util import ask_llm
from genie_tool.util.prompt_util import get_prompt
from genie_tool.util.log_util import timer


@timer()
async def search_reasoning(
        request_id: str, query: str, content: str, history_query_list: list = [],
):
    if not request_id or not query or not content:
        return {}

    model = os.getenv("SEARCH_REASONING_MODEL", "gpt-4.1")
    prompt = get_prompt("deepsearch")["reasoning_prompt"]
    prompt_content = prompt.format(
        query=query,
        sub_queries=history_query_list,
        content=content,
        date=time.strftime("%Y年%m月%d日 %H时%M分%S秒", time.localtime()),
    )
    content = ""
    async for chunk in ask_llm(
            messages=prompt_content,
            model=model,
            stream=True,
            only_content=True,  # 只返回内容
    ):
        if chunk:
            content += chunk
    content_clean = json.loads(repair_json(content, ensure_ascii=False))
    return _parser(request_id, content_clean)


def _parser(request_id, reasoning: dict) -> dict:
    reasoning_dict = {
        "request_id": request_id,
        "rewrite_query": reasoning.get("rewrite_query", ""),
        "reason": reasoning.get("reason", ""),
    }
    if reasoning.get("is_answer", "") in [1, "1"]:
        reasoning_dict["is_verify"] = "1"
    else:
        reasoning_dict["is_verify"] = "0"
    return reasoning_dict


if __name__ == "__main__":
    pass
