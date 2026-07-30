# -*- coding: utf-8 -*-
# =====================
#
# Author: liumin.423
# Date:   2025/9/8
# =====================
"""自动多步数据分析 Agent。

基于 schema 取数工具 + 洞察工具 + 混合协议 Agent：
- 外层 Function Calling 提交 python_interpreter(code=...)
- 内层 get_data / insight 等由代码编排
最终汇总 insights 与 summary 输出 Markdown 报告。
"""
import asyncio
from datetime import datetime
import os
from typing import Dict, List
from dotenv import load_dotenv
from jinja2 import Template
import pandas as pd

from reactor_tool.util.log_util import timer
from reactor_tool.util.file_util import upload_file
from reactor_tool.util.prompt_util import get_prompt

from reactor_tool.model.context import AnalysisContext

from reactor_tool.tool.analysis_component.schema_data import get_schema
from reactor_tool.tool.analysis_component.insights import InsightType
from reactor_tool.tool.analysis_component.analysis_tool import (
    GetDataTool,
    DataTransTool,
    InsightTool,
    SaveInsightTool,
    FinalAnswerTool,
)
from reactor_tool.tool.analysis_component.analysis_fc_agent import (
    AnalysisFCCodeAgent,
    AnalysisStepEvent,
)


load_dotenv()


pd.set_option("display.max_columns", None)


_RESULT_TEMPLATE = """# {{ task }}  

## 分析过程  
{% for insight in insights %}
### {{ insight.get("analysis_process") }}  

#### 数据  

{{ insight.get("data") }}

#### 分析结果  

{% for i in insight.get("insight", []) %}
- {{ i }}
{% endfor %}

{% endfor %}

## 总结  
{{ summary }}
"""


class AutoAnalysisAgent(object):
    """自动数据分析：拉取 schema → 构建 AnalysisContext → FC 交代码 Agent 多步分析。"""

    def __init__(self, max_steps: int = 10, stream: bool = False, queue: asyncio.Queue = None):
        # 流式事件队列
        self.queue = queue or asyncio.Queue()
        self.max_steps = max_steps or 10
        self.stream = stream

    @timer(key="enter")
    async def run(self, task: str, modelCodeList: List[str], request_id: str, businessKnowledge: str = None, **kwargs) -> List[Dict]:
        """执行完整分析任务；stream 模式下向 queue 推送步骤事件。"""
        try:
            schemas = get_schema(modelCodeList, query=task, request_id=request_id)["schemaInfo"]
            context = AnalysisContext(
                task=task,
                request_id=request_id,
                queue=self.queue,
                modelCodeList=modelCodeList,
                businessKnowledge=businessKnowledge,
                schemas=schemas
            )
            
            insights = await self.analysis(context=context)
            file_info = await upload_file(request_id=request_id, content=self.trans_result(task, insights), file_name=f"{task}", file_type ="txt")
            if not isinstance(file_info, list):
                file_info = [file_info]
            result = insights
            if isinstance(insights, dict) and  "summary" in insights:
                result = insights["summary"]
            await self.queue.put({"requestId": request_id, "data": "\n# 分析结论\n", "isFinal": False})
            await self.queue.put({"requestId": request_id, "data": f"\n{result}\n", "file_info": file_info, "isFinal": True})
            return insights
        except Exception as e:
            await self.queue.put({"requestId": request_id, "data": {"error": f"{e}"}, "isFinal": True})
        finally:
            await self.queue.put("[DONE]")
    
    @timer(key="analysis")
    async def analysis(self, context: AnalysisContext) -> List[InsightType]:
        await self.queue.put({"requestId": context.request_id, "data": f"# 分析任务  \n{context.task}  \n", "isFinal": False})
        
        instructions = Template(get_prompt("analysis")["analysis_auto_prompt"]).render(
            schema=context.schemas_markdown,
            business=context.businessKnowledge,
            current_date=datetime.now().strftime("%Y-%m-%d"),
            max_lenght=context.max_data_size,
        )
        
        agent = create_agent(
            instructions=instructions,
            context=context,
            max_steps=self.max_steps,
        )
        result_stream = agent.run(task=context.task, stream=True)

        await self.queue.put({"requestId": context.request_id, "data": f"\n# 分析过程  \n", "isFinal": False})
        final_output = None
        for event in result_stream:
            if not isinstance(event, AnalysisStepEvent):
                continue
            await self.queue.put({
                "requestId": context.request_id,
                "data": f"\n## 分析步骤 {event.step}  \n",
                "isFinal": False,
            })
            if event.thought:
                await self.queue.put({
                    "requestId": context.request_id,
                    "data": f"\n{event.thought}\n",
                    "isFinal": False,
                })
            if event.code:
                await self.queue.put({
                    "requestId": context.request_id,
                    "data": f"\n```python\n{event.code}\n```\n",
                    "isFinal": False,
                })
            if event.observation:
                preview = event.observation
                if len(preview) > 2500:
                    preview = preview[:2500] + "\n..."
                await self.queue.put({
                    "requestId": context.request_id,
                    "data": f"\n### 执行结果\n```\n{preview}\n```\n",
                    "isFinal": False,
                })
            if event.is_final:
                final_output = event.output
                break

        if final_output is None:
            return {
                "insights": context.insights or [],
                "summary": "分析未返回最终结论",
            }
        if isinstance(final_output, dict):
            return final_output
        return {
            "insights": context.insights or [],
            "summary": str(final_output),
        }

    @staticmethod
    def trans_result(task, content):
        if not isinstance(content, dict):
            content = {"insights": [], "summary": str(content)}
        return Template(_RESULT_TEMPLATE).render(
            task=task,
            insights=content.get("insights", []),
            summary=content.get("summary", "无"),
        )


def create_agent(
        context: AnalysisContext,
        instructions: str = None,
        max_steps: int = 10,
) -> AnalysisFCCodeAgent:
    """构建混合协议分析 Agent：外层仅 FC python_interpreter，业务工具注入代码沙箱。"""
    inner_tools = {
        "get_data": GetDataTool(context=context),
        "data_trans": DataTransTool(context=context),
        "insight_analysis": InsightTool(context=context),
        "save_insight": SaveInsightTool(context=context),
        "final_answer": FinalAnswerTool(context=context),
    }
    # smolagents Tool 的 name 属性即调用名；保持 map key 与 name 一致
    for name, tool in list(inner_tools.items()):
        if getattr(tool, "name", None) and tool.name != name:
            inner_tools[tool.name] = tool

    return AnalysisFCCodeAgent(
        instructions=instructions or "",
        inner_tools=inner_tools,
        max_steps=max_steps,
        model_id=os.getenv("ANALYSIS_MODEL") or os.getenv("DEFAULT_MODEL"),
        api_base=os.getenv("OPENAI_BASE_URL"),
        api_key=os.getenv("OPENAI_API_KEY"),
    )
