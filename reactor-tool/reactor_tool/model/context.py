# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/7/8
# =====================
import asyncio
import contextvars
import json
from typing import Dict, List

from pydantic import BaseModel


class _RequestIdCtx(object):
    def __init__(self):
        self._request_id = contextvars.ContextVar("request_id", default="default-request-id")

    @property
    def request_id(self):
        return self._request_id.get()

    @request_id.setter
    def request_id(self, value):
        self._request_id.set(value)


RequestIdCtx = _RequestIdCtx()


class LLMModelInfo(BaseModel):
    model: str
    context_length: int
    max_output: int


class _LLMModelInfoFactory:

    def __init__(self):
        self._factory = {}

    def register(self, model_info: LLMModelInfo):
        self._factory[model_info.model] = model_info

    def get_context_length(self, model: str, default: int = 128000) -> int:
        if info := self._factory.get(model):
            return info.context_length
        else:
            return default

    def get_max_output(self, model: str, default: int = 32000) -> int:
        if info := self._factory.get(model):
            return info.max_output
        else:
            return default


LLMModelInfoFactory = _LLMModelInfoFactory()

LLMModelInfoFactory.register(LLMModelInfo(model="gpt-4.1", context_length=1000000, max_output=32000))
LLMModelInfoFactory.register(LLMModelInfo(model="DeepSeek-V3", context_length=64000, max_output=8000))


class AnalysisContext(object):
    
    def __init__(self, task: str, request_id: str, modelCodeList: List[str], schemas: List[Dict], businessKnowledge: str = None, queue: asyncio.Queue = None, **kwargs):
        self.request_id = request_id
        self.task = task
        self.modelCodeList = modelCodeList
        self.schemas = schemas
        
        self.max_data_size = 10000
        
        self.businessKnowledge: str = businessKnowledge
        
        self.current_task = task
        
        self.insights = []
        
        self.queue = queue
    
    @property
    def schemas_json(self) -> str:
        schemas = [{
            "table": s["modelName"],
            "columns": [{"name": c["columnName"], "type": c["dataType"], "comment": c["columnComment"], "valueExample": c.get("fewShot")} for c in s["schemaList"]],
            "noAnalysisColumns": [c["columnName"] for c in s["schemaList"] if c.get("analyzeSuggest", 0) == -1],
        } for s in self.schemas]
        return json.dumps(schemas, ensure_ascii=False, indent=2)
    
    @property
    def schemas_markdown(self) -> str:
        schemas = ""
        for s in self.schemas:
            columns = "| name | type | comment | valueExample |\n| --- | --- | --- | --- |\n"
            columns += "\n".join([f"| {c['columnName']} | {c['dataType']} | {c['columnComment']} | {c.get('fewShot', '')} |" for c in s["schemaList"]])
            noAnalysisColumns = [c["columnName"] for c in s["schemaList"] if c.get("analyzeSuggest", 0) == -1]
            schemas += f"""\ntable: {s['modelName']}\n\ncolumns:\n\n{columns}\n\nnoAnalysisColumns: {noAnalysisColumns}\n"""
        return schemas
    
    def save_insight(self, df: "pd.DataFrame", insight: str, analysis_process: str): # type: ignore
        self.insights.append({"data": df, "insight": insight, "analysis_process": analysis_process})
        return f"保存洞察（{insight}）成功"
