
import re
import json
import numpy as np
import requests

def select_topk_by_scores(doc_list, scores, k):
    """
    根据得分从高到低排序，返回对应的 top-k 文档
    """
    # 将文档和得分配对，按得分降序排序
    ranked_pairs = sorted(zip(doc_list, scores), key=lambda x: x[1], reverse=True)
    # 取出 top-k 的文档
    topk_docs = [doc for doc, score in ranked_pairs[:k]]
    return topk_docs

def parse_code_from_string(input_string):
    """
    Parse executable code from a string, handling various markdown-like code block formats.

    Parameters:
    input_string (str): The input string.

    Returns:
    str: The parsed code.
    """

    # Pattern to match code blocks wrapped in triple backticks, with optional language specification
    triple_backtick_pattern = r"```(\w*\s*)?(.*?)```"
    match = re.search(triple_backtick_pattern, input_string, flags=re.DOTALL | re.IGNORECASE)
    if match:
        return match.group(2).strip()

    # Pattern to match code blocks wrapped in single backticks
    single_backtick_pattern = r"`(.*?)`"
    match = re.search(single_backtick_pattern, input_string, flags=re.DOTALL)
    if match:
        return match.group(1).strip()

    return input_string.strip()

# 定义你想要的字段顺序
desired_field_order = [
    "projectCode",
    "modelCode",
    "columnId",
    "columnName",
    "columnComment",
    "dataType",
    "fewShot",
    "synonyms",
    "score",
    "analyzeSuggest",
    "defaultRecall"
]

def read_json(text):
    res = parse_code_from_string(text)
    return json.loads(res)


def is_numeric(s):
    try:
        float(s)
    except:
        return False
    return True

def sort_dict_list_by_keys(dict_list, desired_order, include_extra_keys=True):
    """
    将字典列表中的每个字典按键的指定顺序重新排序。

    :param dict_list: list[dict] - 要排序的字典列表
    :param desired_order: list[str] - 希望的键顺序
    :param include_extra_keys: bool - 是否包含不在 desired_order 中的键（放在最后）
    :return: list[dict] - 重新排序后的字典列表
    """
    result = []
    for d in dict_list:
        if not isinstance(d, dict):
            raise ValueError(f"dict_list 中的每个元素必须是字典, {dict_list}")
        
        # 按 desired_order 提取存在的键
        sorted_dict = {key: d[key] for key in desired_order if key in d}
        
        # 如果需要，把原字典中其他未在 desired_order 出现的键加在后面
        if include_extra_keys:
            for key in d:
                if key not in desired_order:
                    sorted_dict[key] = d[key]
        
        result.append(sorted_dict)
    
    return result

def softmax(x):
    return np.exp(x) / np.sum(np.exp(x))

def get_rerank(query, doc_list, request_id, url, timeout=0.5):
    payload = json.dumps({
        "query": query,
        "doc_list": doc_list,
        "request_id": request_id,
    })
    headers = {"Content-Type": "application/json"}
    for i in range(2):
        response = requests.request("POST", url, headers=headers, data=payload, timeout=timeout)
        if response.status_code != 200 or "scores" not in response.json():
            continue
        else:
            break

    scores = response.json()["scores"]
    softmax_scores = softmax(scores)
    return {"scores": softmax_scores}