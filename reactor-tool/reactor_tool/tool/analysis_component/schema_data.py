# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/9/8
# =====================
"""分析数据源 HTTP 适配：拉 schema、按自然语言取数。

对接 Java/外部 ANA_SCHEMA_URL / ANA_DATA_URL。
"""
import json
import os
import requests
from typing import Any, Dict, List

from dotenv import load_dotenv

from reactor_tool.util.log_util import timer

load_dotenv()


@timer()
def get_schema(modelCodeList: List[str], timeout: float = 5, request_id: str = None, **kwargs) -> Dict[str, Any]:
    """按模型 ID 列表拉取表结构元数据。"""
    response = requests.post(
        url=os.getenv("ANA_SCHEMA_URL"),
        data=json.dumps({"modelCodeList": modelCodeList, "traceId": request_id}),
        headers={"Content-Type": "application/json"},
        timeout=timeout,
    )
    if response.status_code != 200:
        response.raise_for_status()
    return json.loads(response.text)


@timer()
def get_data(query: str, modelCodeList: List[str], timeout: float = 90, request_id: str = None, **kwargs) -> List:
    """按自然语言取数描述请求数据接口，返回结果列表。"""
    body = {
        "traceId": request_id,
        "content": query,
        "modelCodeList": modelCodeList,
    }
    response = requests.post(
        url=os.getenv("ANA_DATA_URL"),
        data=json.dumps(body),
        headers={"Content-Type": "application/json"},
        timeout=timeout,
    )
    if response.status_code != 200:
        response.raise_for_status()
    return json.loads(response.text)

