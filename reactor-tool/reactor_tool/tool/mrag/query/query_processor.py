"""
查询预处理模块

该模块负责对用户查询进行预处理和优化：
- 查询理解和分析
- 查询改写和扩展
- 意图识别
- 多轮对话管理

主要功能：
1. 查询文本清洗和标准化
2. 查询意图识别和分类
3. 查询改写和扩展
4. 实体识别和链接
5. 多轮对话上下文管理
6. 查询质量评估和过滤
"""
import json
import os
import re
import tempfile
from typing import List, Dict

import json_repair

from ..generation import PromptManager
from ..generation.llm import LLMClient
from ..utils import caption_utils, oss_utils
from ..utils.logger_utils import logger
from ..utils.time_utils import time_it

tools = [
    {
        "code": "1",
        "name": "图片问答",
        "description": "直接对图片内容进行理解、总结、问答、翻译等；如果用户输入包含图片附件，且问题和图片相关，请选择此工具。"
    },
    {
        "code": "3",
        "name": "文本检索",
        "description": "根据输入文本，搜索相关文本或图片；如果用户输入问题不为空，且进行额外搜索可能对回答问题有帮助，请选择此工具。"
    }
]


class QueryProcessor:
    """查询处理器类"""

    @staticmethod
    def _parse_json_object(result_str: str) -> Dict:
        """
        从 LLM 输出中稳健提取 JSON 对象。

        兼容：
        1. ```json ... ``` 包裹
        2. 裸 JSON
        3. JSON 前后夹带解释文本
        4. 轻微格式缺陷，通过 json_repair 做兜底修复
        """
        if not result_str or not result_str.strip():
            raise RuntimeError("LLM 返回结果为空，无法解析 JSON。")

        cleaned_str = result_str.strip()
        candidates: list[str] = []

        # 优先提取 ```json``` 代码块，兼容模型按示例输出 fenced JSON 的场景。
        fenced_matches = re.findall(r"```json\s*([\s\S]*?)\s*```", cleaned_str, flags=re.IGNORECASE)
        if fenced_matches:
            candidates.extend(match.strip() for match in fenced_matches if match and match.strip())

        # 再兼容普通 ``` ... ``` 包裹，避免模型省略 json 标识符时直接失败。
        plain_fenced_matches = re.findall(r"```\s*([\s\S]*?)\s*```", cleaned_str)
        if plain_fenced_matches:
            candidates.extend(match.strip() for match in plain_fenced_matches if match and match.strip())

        # 裸 JSON 是 gpt-5.2 在当前供应商下更常见的返回形式，也要直接尝试。
        candidates.append(cleaned_str)

        # 如果前后有解释文本，截取最外层对象再次尝试。
        start_idx = cleaned_str.find("{")
        end_idx = cleaned_str.rfind("}")
        if start_idx != -1 and end_idx != -1 and end_idx > start_idx:
            candidates.append(cleaned_str[start_idx:end_idx + 1].strip())

        tried_candidates = set()
        for candidate in candidates:
            if not candidate or candidate in tried_candidates:
                continue
            tried_candidates.add(candidate)

            try:
                parsed = json.loads(candidate)
                if isinstance(parsed, dict):
                    return parsed
            except json.JSONDecodeError:
                pass

            # 使用 json_repair 兼容轻微缺损 JSON，避免大模型少量格式漂移直接打断主流程。
            try:
                repaired = json_repair.loads(candidate)
                if isinstance(repaired, dict):
                    return repaired
            except Exception:
                continue

        preview = cleaned_str[:300].replace("\n", "\\n")
        logger.error(f"无法从 LLM 输出中提取有效 JSON，原始内容片段: {preview}")
        raise RuntimeError("解析 LLM 返回的 JSON 失败。")

    @staticmethod
    @time_it
    def extend_questions(question: str):
        prompt = PromptManager.QUERY_EXTEND_PROMPT.format(question=question)
        messages = LLMClient.convert_messages(prompt)
        response = LLMClient().completions(messages,
                                           stream=False,
                                           temperature=0.01,
                                           )
        resp = response

        questions = resp.split("\n")
        return [question.strip() for question in questions if question.strip()]

    @staticmethod
    @time_it
    def extract_image_content(filename, image_url: str):
        local_dir = tempfile.gettempdir()
        local_file_path = os.path.join(local_dir, filename)
        oss_utils.download(image_url, local_file_path)
        caption = caption_utils.generate_caption(image_url)
        if local_file_path:
            os.remove(local_file_path)
        return caption

    @staticmethod
    @time_it
    def get_pre_think_results(question: str):
        prompt = PromptManager.PRE_THINK_PROMPT.format(task=question)
        messages = LLMClient.convert_messages(prompt)
        response = LLMClient().completions(messages,
                                           stream=False,
                                           temperature=0.01,
                                           )
        return response

    @staticmethod
    def build_context(docs: List[Dict]):
        context = ""
        for doc in docs:
            if doc['payload'].get("text"):
                context += "[文本片段开始]\n" + doc["payload"]['text'] + "\n[文本片段结束]\n"

        return context

    @staticmethod
    @time_it
    def summarize_subquery(question, chunks: List[Dict]):
        context = QueryProcessor.build_context(chunks)
        prompt = PromptManager.SUMMARIZE_PROMPT.format(context=context, question=question)
        messages = LLMClient.convert_messages(prompt)
        response = LLMClient().completions(messages,
                                           stream=False,
                                           temperature=0.01)
        return response

    @staticmethod
    @time_it
    def generate_next_instruction(question: str, sub_questions: List[str], summarized_contexts: List[str]):
        prompt = PromptManager.POST_CHECK_PROMPT.format(
            question=question,
            sub_questions="\n".join(sub_questions),
            context="\n".join(summarized_contexts)
        )
        messages = LLMClient.convert_messages(prompt)
        resp = LLMClient().completions(messages,
                                       stream=False,
                                       temperature=0.01,
                                       )

        next_instruction = QueryProcessor._parse_json_object(resp)
        logger.info("next_instruction: ", next_instruction)
        return next_instruction

    @staticmethod
    def expand_question_with_images(question: str, image_descs: List[str]):
        logger.info("开始前置的思考")
        pre_think_results = QueryProcessor.get_pre_think_results(question)
        logger.info(f"前置思考结果: {pre_think_results}")

        logger.info("开始基于图片生成子查询")
        if image_descs:
            full_image_desc = "\n".join(image_descs)
            full_input = f"输入:\n{pre_think_results}\n<图片描述>{full_image_desc}</图片描述>"
        else:
            full_input = f"输入:\n{pre_think_results}"

        prompt = PromptManager.QUERY_EXTEND_WITH_PRE_THINK_PROMPT.format(pre_think_result_reminder=full_input)
        messages = LLMClient.convert_messages(prompt)
        resp = LLMClient().completions(messages,
                                       stream=False,
                                       temperature=0.01,
                                       )

        logger.info(f"expand_question_with_images: {resp}")
        questions = resp.split("\n")
        questions = [question.strip() for question in questions if question.strip()]
        return questions

    @staticmethod
    @time_it
    def simple_query_check(question: str):
        prompt = PromptManager.SIMPLE_QUERY_CHECK_PROMPT.format(question=question)
        client = LLMClient()
        messages = client.convert_messages(prompt)
        resp = LLMClient().completions(messages, stream=False, max_tokens=10)

        logger.info(f"simple_query_check: {resp}")
        return "0" in resp

    @staticmethod
    @time_it
    def simple_image_query_check(question: str, image_descs: List[str]):
        if not image_descs:
            return False

        prompt = PromptManager.SIMPLE_IMAGE_QUERY_CHECK_PROMPT.format(
            tools_info=json.dumps(tools, ensure_ascii=False),
            question=question,
            image_desc="\n".join(image_descs)
        )
        messages = LLMClient.convert_messages(prompt)
        resp = LLMClient().completions(messages, stream=False, max_tokens=10)

        logger.info(f"simple_image_query_check: {resp}")
        try:
            tool_ids = resp.split(",")
            if len(tool_ids) == 1 and tool_ids[0] == "1":
                return True
        except Exception as e:
            return False
