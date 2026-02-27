# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/9/8
# =====================
import asyncio
from datetime import datetime
import os
import re
from typing import Dict, List
from dotenv import load_dotenv
from jinja2 import Template
import pandas as pd

from smolagents import PythonInterpreterTool, Tool, ActionStep, ActionOutput, ToolCall, FinalAnswerStep
from smolagents import CodeAgent, OpenAIServerModel

from genie_tool.util.log_util import timer
from genie_tool.util.file_util import upload_file
from genie_tool.util.prompt_util import get_prompt

from genie_tool.model.context import AnalysisContext

from genie_tool.tool.analysis_component.schema_data import get_schema
from genie_tool.tool.analysis_component.insights import InsightType
from genie_tool.tool.analysis_component.analysis_tool import GetDataTool, DataTransTool, InsightTool, SaveInsightTool, FinalAnswerTool


load_dotenv()


pd.set_option("display.max_columns", None)

authorized_imports=[
    "pandas", "numpy", 
    "statsmodels", "statsmodels.*", 
    "scipy", "scipy.*", 
    "sklearn", "sklearn.*", 
]


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
    
    def __init__(self, max_steps: int = 10, stream: bool = False, queue: asyncio.Queue = None):
        # 用作流式
        self.queue = queue or asyncio.Queue()
        self.max_steps = max_steps or 10
        self.stream = stream

    @timer(key="enter")
    async def run(self, task: str, modelCodeList: List[str], request_id: str, businessKnowledge: str = None, **kwargs) -> List[Dict]:
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
            return_full_result=False,
        )
        result = agent.run(task=context.task, stream=self.stream)
        
        await self.queue.put({"requestId": context.request_id, "data": f"\n# 分析过程  \n", "isFinal": False})
        if self.stream:
            step = 1
            await self.queue.put({"requestId": context.request_id, "data": f"\n## 分析步骤 {step}  \n", "isFinal": False})
            for chunk in result:
                if isinstance(chunk, ActionStep) and chunk.model_output:
                    if step > 1:
                        await self.queue.put({"requestId": context.request_id, "data": f"\n## 分析步骤 {step}  \n", "isFinal": False})
                    await self.queue.put({"requestId": context.request_id, "data": chunk.model_output.replace("Thought:", "\n").split("<code>")[0], "isFinal": False})
                    if code := re.search(r"<code>(.*)</code>", chunk.model_output, re.S):
                        await self.queue.put({"requestId": context.request_id, "data": f"\n```python\n{code.group(1).strip()}\n```\n", "isFinal": False})
                    step += 1
                elif isinstance(chunk, ToolCall):
                    continue
                elif isinstance(chunk, ActionOutput):
                    continue
                elif isinstance(chunk, FinalAnswerStep):
                    return chunk.output
                else:
                    pass
        else:
            return result

    @staticmethod
    def trans_result(task, content):
        return Template(_RESULT_TEMPLATE).render(task=task, insights=content.get("insights", []), summary=content.get("summary", "无"))


def create_agent(
        context: AnalysisContext, 
        tools: List[Tool] = [GetDataTool, DataTransTool, InsightTool, SaveInsightTool, FinalAnswerTool], 
        instructions: str= None,
        max_steps: int = 10,
        return_full_result: bool = False,
) -> CodeAgent:
    model = os.getenv("ANALYSIS_MODEL", "gpt-4.1")
    base_url = os.getenv("OPENAI_BASE_URL")
    api_key = os.getenv("OPENAI_API_KEY")
    _model = OpenAIServerModel(
        model_id=model,
        api_base=base_url,
        api_key=api_key,
    )
    
    return CodeAgent(
        model=_model,
        instructions=instructions,
        tools=[PythonInterpreterTool(authorized_imports=authorized_imports)] \
            + [tool(context=context) for tool in tools],
        additional_authorized_imports=authorized_imports,
        max_steps=max_steps,
        return_full_result=return_full_result,
    )

