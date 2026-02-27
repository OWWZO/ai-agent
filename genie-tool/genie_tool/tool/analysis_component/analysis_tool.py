# -*- coding: utf-8 -*-
# =====================
# 
# 
# Author: liumin.423
# Date:   2025/9/8
# =====================
import json
import re
from typing import  Any, Dict, List

from smolagents import Tool

import pandas as pd

from loguru import logger
import sqlparse

from genie_tool.util.log_util import timer
from genie_tool.tool.analysis_component.schema_data import get_data
from genie_tool.tool.analysis_component.data_model import DataModel, Measure, SiblingGroup
from genie_tool.tool.analysis_component.insights import InsightFactoryDict, InsightType


pd.set_option("display.max_columns", None)


class GetDataTool(Tool):
    name = "get_data"
    description = "这是一个取数智能体。可以使用此工具获取需要的数据，非常聪明，能够准确理解你的取数需求，返回 pd.DataFrame 格式的数据"
    inputs = {
        "query": {
            "type": "string",
            "description": "当前需要获取的数据，是人类的的自然语言描述。\n\n            ## 要求  \n\n            1. **一次性取全维度**：取数描述**必须一次性包含所有需要的相关维度**数据，禁止分多次请求\n                - 无明确要求下，**优先使用离散维度**；连续数据不利于分析\n                - **维度必须是 表Schema 中的维度**  \n            2. **一次取整个时间段**：**仅仅在明确取数时间的情况下**，取数描述必须一次性包含所有需要的时间段数据，禁止分多次请求，效率低下\n                - 时间段要表述清楚，如：近一年、昨天、上个月、2023年5月1日-2023年8月13日等\n                - **时间段跨度超过半年（6个月）的取月度数据**\n                - 没有给定的时间段则不需要带上时间段\n            3. **简洁描述**：描述需精炼，仅包含必要的筛选条件、时间段、维度和指标信息，剔除所有无关说明  \n            4. **单指标原则**：每次请求仅限获取 1 个指标数据  \n                - 只支持获取聚合指标（如：销售额、订单量、用户数、访问次数、点击率、退货量、库存量、离职率等）  \n            5. **筛选条件规则**  \n                - **禁止漏掉任务明确要求的筛选条件**，将其添加到筛选条件中，**使用原始词，禁止解释、转译、引申、改写、扩展等**  \n                - 筛选条件分为两种模式：\n                    - 表达式模式：例如 筛选城市为北京、品牌为Apple，筛选天气为雨天 等  \n                    - 关键词模式：例如 筛选北京，筛选CFO、算法工程师 等\n                - **筛选条件选择与分析任务中的模式一致**（分析任务中只提供关键词的用关键词模式，用表达式模式的用表达式），**禁止将关键词模式转换成表达式模式**  \n            6. 分析任务中有**分析我们部门/我们团队/我们小组/我部门/我团队/我小组等相关表达，则**表示限定取数范围**，**必须作为取数的筛选条件**  \n                - 此条件直接将原文放在筛选中，**禁止翻译成 维度=值 这种条件表达式**\n                - 仅有此条规则不用考虑维度必须是表 Schema 中的维度这条要求  \n            7. **筛选条件无法确定是哪个维度上的条件时，直接使用值来表达筛选条件**  \n\n            ## 返回格式  \n\n            筛选[条件1]、[条件2]、...、、[条件N] ，根据[维度1]、[维度2]、...、[维度N] 进行分组，统计[时间段]的[聚合指标]\n\n            注：筛选条件是可选项，如无必要不添加筛选条件，保证获取全的维度的数据；**[时间段] 是可选项，只在用户表达了时间的情况下存在**\n\n            ## 示例 (严格遵守上述格式与要求)\n\n            ### 无筛选条件例子  \n            例子一（没有给定时间段）：\n            根据商品类目、店铺进行分组，统计销售额\n            例子二：\n            根据大区、省份、城市、商品类别进行分组，统计最近一周的订单量\n            例子三：\n            根据新老客类型、注册渠道、用户性别进行分组，统计上月的购买用户数\n            例子四：\n            根据小时进行分组，统计过去24小时的网站访问次数\n            例子五：\n            根据销售渠道、商品品牌进行分组，统计本季度的退货量\n            例子六（没有给定时间段）：\n            根据仓库、SKU进行分组，统计库存量\n            例子七（时间跨度超过一年）：\n            根据商品类目、店铺、供应商进行分组，统计2024全年各月的退货率\n            例子八：\n            根据商家进行分组，统计最近30天的退款金额\n            例子九：\n            根据促销活动、商品类目、渠道进行分组，统计活动期间的曝光次数\n            例子十（时间跨度超过一年）：\n            根据商品类型、商家、店铺等级、SKU进行分组，统计2024年6月至2025年5月各月的销售量\n\n            ### 有筛选条件例子  \n            例子一：\n            筛选在线支付、未退货，根据商品类目、省份进行分组，统计2023年Q4的订单总额\n            例子二（要求 6）：\n            筛选我团队、日志级别为ERROR，根据应用服务名、服务器节点进行分组，统计今日00:00至今的错误日志数量\n            例子三（没有给定时间段）：\n            筛选iOS、访问频次大于等于3，根据用户年龄段、用户性别、注册渠道、页面类别进行分组，统计平均停留时长\n            例子四（要求 6）：\n            筛选我们部门、审批流程为报销、状态为未完成，根据报销类别、报销项目、报销金额区间进行分组，统计上个月的平均审批耗时\n            例子五（时间跨度超过一年）：\n            筛选原料批次为AX-2024、检测标准为ISO9001，根据生产线、班组长、时间段进行分组，统计本年度各月的次品率\n            例子六（要求 6）：\n            筛选我们小组，根据咨询产品类别进行分组，统计每周的平均会话响应时长\n            例子七（条件 7）：\n            筛选CFO，根据咨询产品类别进行分组，统计每周的平均会话响应时长\n\n            ---  \n            再次强调**禁止取明细数据**，必须明确说明聚合指标名  \n            不要漏掉筛选条件，避免获取错误的数据，并保证获取数据比较全面、完整"
        }
    }
    output_type = "object"

    def __init__(self, context, *args, **kwargs):
        self.context = context
        super().__init__(*args, **kwargs)
    
    def forward(self, query: str) -> pd.DataFrame:
        self.context.queue.put_nowait({"requestId": self.context.request_id, "data": f"\n### 1. 取数 Query\n{query}  \n", "isFinal": False})
        query = f"{query}。取 {self.context.max_data_size} 条"
        base_datas = get_data(query=query, modelCodeList=self.context.modelCodeList, request_id=self.context.request_id)
        if isinstance(base_datas, list):
            df = self.merge_df(base_datas)
            # dfs = [self.to_df(bd) for bd in base_datas]
            if base_datas:
                pretty_sql = sqlparse.format(base_datas[0]["nl2sqlResult"], reindent=True, keyword_case="upper", strip_comments=True)
                self.context.queue.put_nowait({"requestId": self.context.request_id, "data": f"\n### 2. 取数 SQL\n```sql\n{pretty_sql.strip()}\n```\n", "isFinal": False})
            if len(df) > 0:
                top_k = min(5, len(df))
                top_k_data = f"### 3. 取数结果\n共获取到 {len(df)} 条数据，前 {top_k} 条数据如下：\n\n{df[: top_k].to_markdown()}"
            else:
                top_k_data = "### 3. 取数结果\n没有获取到数据"
            self.context.queue.put_nowait({"requestId": self.context.request_id, "data": f"\n{top_k_data}\n", "isFinal": False})
            return df if len(df) > 0 else "没有获取到数据"
        # 异常情况
        else:
            # 查询无权限
            self.context.queue.put_nowait({"requestId": self.context.request_id, "data": f"### 3. 取数结果\n{base_datas}\n", "isFinal": False})
            return base_datas
    
    @classmethod
    @timer()
    def merge_df(cls, datas: List[Dict]) -> pd.DataFrame:
        joined_df = cls.to_df(datas[0])
        if len(datas) > 1 and (prefix := "_".join([f"{f['name']}为{f['val']}" for f in datas[0]["filters"] 
                if f["opt"] in ["EQUALS"] and f["dataType"] in ["VARCHAR"] and not re.match(r"^\d{4}-\d{2}-\d{2}$", f["val"])])):
            joined_df = joined_df.rename({c["name"]: f"{prefix}_{c['name']}" for c in datas[0]["columnList"] if c["guid"] in datas[0]["measureCols"]}, axis=1)
        on_cols = [c["name"] for c in datas[0]["columnList"] if c["col"] in datas[0]["dimCols"]]
        for data in datas[1:]:
            if set(datas[0]["dimCols"]) == set(data["dimCols"]):
                df = cls.to_df(data)
                if prefix := "_".join([f"{f['name']}为{f['val']}" for f in data["filters"] 
                                      if f["opt"] in ["EQUALS"] and f["dataType"] in ["VARCHAR"] and not re.match(r"^\d{4}-\d{2}-\d{2}$", f["val"])]):
                    df = df.rename({c["name"]: f"{prefix}_{c['name']}" for c in data["columnList"] if c["guid"] in data["measureCols"]}, axis=1)
                joined_df = pd.merge(joined_df, df, on=on_cols, how="outer") 
        return joined_df
    
    @staticmethod
    def to_df(data) -> pd.DataFrame:
        cols = [c["name"] for c in data["columnList"]]
        guids = [c["guid"] for c in data["columnList"]]
        values = [[row.get(c) for c in guids] for row in data["dataList"]]
        return pd.DataFrame(values, columns=cols)
    
    @staticmethod
    def to_json(data: str):
        if data.startswith("```json"):
            data = data[len("```json"):]
        if data.startswith("```"):
            data = data[len("```"):]
        if data.startswith("json"):
            data = data[len("json"):]
        if data.endswith("```"):
            data = data[:-len("```")]
        return json.loads(data)


class InsightTool(Tool):
    name = "insight_analysis"
    description = "这是一个传统数据分析工具。可以使用此工具做一些常见数据分析。\n        \n    ## 支持的分析类型  \n\n    - OutstandingFirst: 分析指标表现最高的情况\n    - OutstandingLast: 分析指标表现最差的情况\n    - Attribution: 分析指标表现最高的是否占据绝对优势，对整体指标影响占据主要地位\n    - Evenness: 分析指标是否平稳\n    - Trend: 分析指标趋势，**指标需要有时序**\n    - Correlation: 分析相关性，看两个维度是否具有相同或者相反表现，维度必须是数字类型\n    - ChangePoint: 分析拐点，看数据在哪个点出现转折，**指标需要有时序**\n\n    ---\n    返回 Dict 类型的分析结果"
    inputs = {
        "df": {
            "type": "object",
            "description": "分析的数据，必须是 pd.DataFrame 类型"
        },
        "breakdown": {
            "type": "string",
            "description": "需要分析的维度，也可以理解需要下钻分析的维度。必须是 df 数据中的列，禁止捏造维度，如果是序列分析（如 Trend、ChangePoint）则必须是时间序列的维度"
        },
        "measure": {
            "type": "string",
            "description": "分析的指标，必须是 df 数据中的列，禁止捏造指标，指标列只能是数值类型，不能有 nan 值，如果有请合理处理"
        },
        "measure_type": {
            "type": "string",
            "description": "指标的类型，只有 quantity、ratio 两种。quantity 表示数量类型指标，ratio 表示比率类型指标。"
        },
        "analysis_method": {
            "type": "string",
            "description": "当前支持的分析方法。只有如下几种类型：\n            - OutstandingFirst: 分析指标表现最高的情况\n            - OutstandingLast: 分析指标表现最差的情况\n            - Attribution: 分析指标表现最高的是否占据绝对优势，对整体指标影响占据主要地位\n            - Evenness: 分析指标是否平稳\n            - Trend: 分析指标趋势，**指标需要有时序**\n            - Correlation: 分析相关性，看两个维度是否具有相同或者相反表现，维度必须是数字类型\n            - ChangePoint: 分析拐点，看数据在哪个点出现转折，**指标需要有时序**\n\n            **必须是上述几种取值**"
        }
    }
    output_type = "object"

    def __init__(self, context, *args, **kwargs):
        self.context = context
        super().__init__(*args, **kwargs)
    
    def forward(
            self, 
            df: pd.DataFrame, 
            breakdown: str, 
            measure: str, 
            measure_type: str, 
            analysis_method: str,
    ) -> List[Dict[str, Any]]:
        if analysis_method in ["Trend", "ChangePoint"]:
            val = df.iloc[0][breakdown]
            if len(val) == 4:
                format = "%Y"
            elif len(val) == 6:
                format = "%Y%m"
            elif len(val) == 7:
                format = "%Y-%m"
            elif len(val) == 8:
                format = "%Y%m%d"
            elif len(val) == 10:
                format = "%Y-%m-%d"
            else:
                format = "%Y-%m-%d"
            df[breakdown] = pd.to_datetime(df[breakdown], format=format)
        data_model = DataModel(
            data=df,
            measure=Measure(name=measure, column=measure, agg="sum" if measure_type == "quantity" else "max", type=measure_type),
        )
        breakdown_col = [c for c in data_model.columns if c.name == breakdown][0]
        sibling_group = SiblingGroup(data=data_model, breakdown=breakdown_col)
        insight: InsightType = InsightFactoryDict[analysis_method].from_data(sibling_group)
        return insight.model_dump(exclude={"data",}) if insight else {"description": "无分析结论"}


class SaveInsightTool(Tool):
    name = "save_insight"
    description = "这是一个分析结论保存工具。请你将你每一步分析得出的**有用的结论或信息**调用此工具保存到数据库中，“无分析结论” 等无效分析结果禁止保存，**不要保存非分析结论的信息**。多个结论可以多次调用"
    inputs = {
        "df": {
            "type": "object",
            "description": "分析的数据，**必须是 pd.DataFrame 类型数据**，用来**直接**展示证明你的分析结论。请提供**变换后直接得到分析结论的 pd.DataFrame 类型数据**，而不是变换前的太长的 pd.DataFrame 类型数据"
        },
        "insight": {
            "type": "string",
            "description": "分析洞察，**分析获得的数据或结论**，不是无信息量或者无意义的句子。insight 必须和 df 参数提供的数据对应，禁止出现 df 的数据和 insight 不相关的情况，禁止保存基于 df 的部分数据得出结论，禁止分析结论中存在未知信息或者 XX 等占位符信息。如果有多个洞察则多个洞察之间使用 <sep> 分割"
        },
        "analysis_process": {
            "type": "string",
            "description": "分析过程，**简单总结**使用的分析方法和分析过程"
        }
    }
    output_type = "string"

    def __init__(self, context, *args, **kwargs):
        self.context = context
        super().__init__(*args, **kwargs)
    
    def forward(self, df: pd.DataFrame, insight: str, analysis_process: str) -> pd.DataFrame:
        insights = [i.strip() for i in insight.split("<sep>")]
        if isinstance(df, pd.DataFrame):
            data = InsightType.df_to_csv(df)
        # 补充异常情况处理
        elif isinstance(df, (dict, list)):
            try:
                data = json.dumps(df, ensure_ascii=False)
            except Exception as e:
                logger.error(f"{RequestIdCtx.request_id} {self.name} error: {e}")
                data = ""
        else:
            logger.error(f"{RequestIdCtx.request_id} {self.name} df type[{type(df)}] is not pd.DataFrame")
            data = ""
        return self.context.save_insight(df=data, insight=insights, analysis_process=analysis_process)


class DataTransTool(Tool):
    name = "data_trans"
    description = "这是一个数据变换工具。可以使用此工具对数据的某一列计算占比、计算排名、计算和整体均值差以及计算增长，返回 pd.DataFrame 格式的数据"
    inputs = {
        "df": {
            "type": "object",
            "description": "分析的数据，必须是 pd.DataFrame 类型"
        },
        "column": {
            "type": "string",
            "description": "分析的维度，可以理解为变换中需要 group 的列，或者是时间序列列"
        },
        "measure": {
            "type": "string",
            "description": "需要变换的指标维度，必须是 df 数据中的列，禁止捏造指标，指标列只能是数值类型，不能有 nan 值，如果有请合理处理"
        },
        "measure_type": {
            "type": "string",
            "description": "指标的类型，只有 quantity、ratio 两种。quantity 表示数量类型指标，ratio 表示比率类型指标。"
        },
        "trans_type": {
            "type": "string",
            "description": "数据转换类型，只支持如下类型：\n            - rate: 将某列转换为占比\n            - rank: 按照某列计算排名\n            - increase: 序列分析，计算增长\n            - sub_avg: 算与整体均值的差值"
        }
    }
    output_type = "object"

    def __init__(self, context, *args, **kwargs):
        self.context = context
        super().__init__(*args, **kwargs)
    
    def forward(self, df: pd.DataFrame, column:str, measure: str, measure_type: str, trans_type: str) -> pd.DataFrame:
        assert trans_type in ["rate", "rank", "increase", "sub_avg"]
        agg = "sum" if measure_type == "quantity" else "max"
        if trans_type == "rate":
            df = df.groupby(column).agg({measure: agg}).reset_index()
            df[f"Rate({measure})"] = df[measure] / df[measure].sum()
        if trans_type == "increase":
            df = df.groupby(column).agg({measure: agg})\
                .sort_values(by=column, ascending=True).reset_index()
            df[f"Increase({measure})"] = df[measure].diff(1)
            df = df.dropna()
        if trans_type == "sub_avg":
            df = df.groupby(column).agg({measure: agg}).reset_index()
            avg = df[measure].sum() / df[measure].size
            df[f"{measure}-avg"] = df[measure] - avg
        if trans_type == "rank":
            df = df.groupby(column).agg({measure: agg}).reset_index()
            df[f"Rank({measure})"] = df[measure].rank(ascending=False, method="dense").astype(int)
        return df


class FinalAnswerTool(Tool):
    name = "final_answer"
    description = "为用户的任务提供最终答案"
    inputs = {
        "answer": {
            "type": "string", 
            "description": "如果完成分析任务则返回完成分析结论，如果没有完成则给出没有完成的原因\n\n            ## 要求\n            1. 如果完成分析，则返回分析结论\n            2. 如果没有完成分析，则返回没有完成分析的原因\n            3. **必须使用中文回答，禁止给出任何程序代码**\n            4. 使用 **markdown** 格式"
        }
    }
    output_type = "any"
    
    def __init__(self, context, *args, **kwargs):
        self.context = context
        super().__init__(*args, **kwargs)

    def forward(self, answer: str) -> Any:
        return {
            "insights": self.context.insights or [],
            "summary": answer,
        }


if __name__ == "__main__":
    pass
