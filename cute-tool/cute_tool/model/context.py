# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/7/8
# =====================
"""请求级上下文与 LLM 模型元信息。

- RequestIdCtx: 协程安全的 request_id 透传（日志 / 计时）
- LLMModelInfoFactory: 模型 context 窗口与 max_output 注册表
- AnalysisContext: 自动分析任务的运行态上下文
"""

import asyncio
import contextvars
import json
from typing import Dict, List

from pydantic import BaseModel


class _RequestIdCtx(object):
    """基于 contextvars 的请求 ID 容器，保证异步链路日志可关联。"""

    def __init__(self):
        self._request_id = contextvars.ContextVar(
            "request_id", default="default-request-id"
        )

    @property
    def request_id(self):
        return self._request_id.get()

    @request_id.setter
    def request_id(self, value):
        self._request_id.set(value)


# 全局单例：中间件写入，业务日志读取
RequestIdCtx = _RequestIdCtx()


class LLMModelInfo(BaseModel):
    """单个 LLM 的上下文窗口与输出上限配置。"""

    model: str
    context_length: int
    max_output: int


class _LLMModelInfoFactory:
    """按模型名查询 context_length / max_output，未注册时走默认值。"""

    def __init__(self):
        self._factory = {}

    def register(self, model_info: LLMModelInfo):
        """注册或覆盖某个模型的容量信息。"""
        self._factory[model_info.model] = model_info

    def get_context_length(self, model: str, default: int = 128000) -> int:
        """获取模型上下文长度（token 量级）。"""
        if info := self._factory.get(model):
            return info.context_length
        else:
            return default

    def get_max_output(self, model: str, default: int = 32000) -> int:
        """获取模型单次最大输出 token。"""
        if info := self._factory.get(model):
            return info.max_output
        else:
            return default


LLMModelInfoFactory = _LLMModelInfoFactory()

# 预置常用模型容量（可按部署环境继续 register）
LLMModelInfoFactory.register(
    LLMModelInfo(model="gpt-4.1", context_length=1000000, max_output=32000)
)
LLMModelInfoFactory.register(
    LLMModelInfo(model="DeepSeek-V3", context_length=64000, max_output=8000)
)


class AnalysisContext(object):
    """自动分析（auto_analysis）单次任务上下文。

    持有表 schema、业务知识、洞察结果队列，供分析 Agent 多步读写。
    """

    def __init__(
        self,
        task: str,
        request_id: str,
        modelCodeList: List[str],
        schemas: List[Dict],
        businessKnowledge: str = None,
        queue: asyncio.Queue = None,
        **kwargs,
    ):
        self.request_id = request_id
        self.task = task  # 用户原始分析任务
        self.modelCodeList = modelCodeList  # 数据模型 ID 列表
        self.schemas = schemas  # 表结构元数据

        self.max_data_size = 10000  # 单次取数上限，防止上下文爆炸

        self.businessKnowledge: str = businessKnowledge  # 业务口径 / 指标公式等

        self.current_task = task  # 当前子任务描述（多步分析时会更新）

        self.insights = []  # 已沉淀的洞察列表
        self.data_fetch_error = None  # 取数失败时阻止后续分析继续使用猜测数据

        self.queue = queue  # 可选：流式事件推送队列

    @property
    def schemas_json(self) -> str:
        """将 schema 转为 JSON 字符串，注入 LLM prompt。"""
        schemas = [
            {
                "table": s["modelName"],
                "columns": [
                    {
                        "name": c["columnName"],
                        "type": c["dataType"],
                        "comment": c["columnComment"],
                        "valueExample": c.get("fewShot"),
                    }
                    for c in s["schemaList"]
                ],
                "noAnalysisColumns": [
                    c["columnName"]
                    for c in s["schemaList"]
                    if c.get("analyzeSuggest", 0) == -1
                ],
            }
            for s in self.schemas
        ]
        return json.dumps(schemas, ensure_ascii=False, indent=2)

    @property
    def schemas_markdown(self) -> str:
        """将 schema 转为 Markdown 表格，便于模型阅读。"""
        schemas = ""
        for s in self.schemas:
            columns = (
                "| name | type | comment | valueExample |\n| --- | --- | --- | --- |\n"
            )
            columns += "\n".join(
                [
                    f"| {c['columnName']} | {c['dataType']} | {c['columnComment']} | {c.get('fewShot', '')} |"
                    for c in s["schemaList"]
                ]
            )
            noAnalysisColumns = [
                c["columnName"]
                for c in s["schemaList"]
                if c.get("analyzeSuggest", 0) == -1
            ]
            schemas += f"""\ntable: {s["modelName"]}\n\ncolumns:\n\n{columns}\n\nnoAnalysisColumns: {noAnalysisColumns}\n"""
        return schemas

    def save_insight(self, df: "pd.DataFrame", insight: str, analysis_process: str):  # type: ignore
        """保存一步分析得到的洞察（数据帧 + 结论 + 过程）。"""
        self.insights.append(
            {"data": df, "insight": insight, "analysis_process": analysis_process}
        )
        return f"保存洞察（{insight}）成功"
