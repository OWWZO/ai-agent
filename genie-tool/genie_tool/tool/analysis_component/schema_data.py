# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/9/8
# =====================
import json
import os
import requests
from typing import Any, Dict, List

from dotenv import load_dotenv

from genie_tool.util.log_util import timer

load_dotenv()


@timer()
def get_schema(modelCodeList: List[str], timeout: float = 5, request_id: str = None, **kwargs) -> Dict[str, Any]:
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

