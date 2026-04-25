import os
import re
import time

import ast
import json_repair

import requests
from loguru import logger
from jinja2 import Template

from dotenv import load_dotenv
from dataclasses import dataclass, fields

from reactor_tool.tool.table_rag.utils import get_rerank
from reactor_tool.util.log_util import logger, timer
from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.util.qdrant_utils import QdrantRecall, EmbeddingClient

load_dotenv()
# 工具数量大于此参数时触发工具过滤
DEFAULT_FILTER_MINIMUM_COUNT = 8
# 高相关模式阈值（根据query快速选择工具）
DEFAULT_HIGH_QUERY_SIMILARITY_THRESHOLD = 0.9
# 低相关模式阈值（过滤工具数量）
DEFAULT_LOW_SOP_SIMILARITY_THRESHOLD = 0.4
# 快速判断无SOP的阈值
DEFAULT_NO_SOP_SIMILARITY_THRESHOLD = 0.2
# 最大召回的SOP数量
MAX_RECALL_SOP_NUMBER = 5
HIGH_RECALL_SOP_NUMBER = 2


def _env_flag(name: str, default: bool = False) -> bool:
    """统一解析环境变量布尔值，避免 'false' 被当成真值。"""
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}

def safe_literal_eval(input_str):
    try:
        # 尝试解析字符串
        result = ast.literal_eval(input_str)
        return result
    except (SyntaxError, ValueError) as e:
        # 捕获 SyntaxError 和 ValueError，并打印错误信息
        result = json_repair.json_repair.repair_json(input_str)
        return ast.literal_eval(result)

@dataclass
class SOPDict:
    sop_id: int
    sop_name: str
    sop_type: str
    description: str
    sop_string: str
    sop_json_string: str
    vector_type: str
    score: float = None
    parameters: dict = None

    def __init__(self, **kwargs):
        # 获取所有 dataclass 字段名
        field_names = {f.name for f in fields(self.__class__)}

        # 分离合法字段和额外字段
        dataclass_kwargs = {k: v for k, v in kwargs.items() if k in field_names}
        extra_kwargs = {k: v for k, v in kwargs.items() if k not in field_names}

        # 初始化 dataclass 部分
        for key, value in dataclass_kwargs.items():
            setattr(self, key, value)

        # 初始化额外字段
        for key, value in extra_kwargs.items():
            setattr(self, key, value)

@dataclass
class SOP_MODE:
    COMMON_MODE = "COMMON_MODE"
    NO_SOP_MODE = "NO_SOP_MODE"
    HIGH_MODE = "HIGH_MODE"


def get_qd_server_recall(query, filters, collection_name, qdrant_url, limit=30,
                         threshhold=0.5, timeout=5000000):

    body = {
        "scoreThreshold": threshhold,
        "query": query,
        "keywordFilterMap": filters,
        "limit": limit,
        "timeout": timeout, # # 以毫秒为单位
        "collectionName": collection_name
    }
    r = requests.post(qdrant_url, json=body)
    if r.status_code != 200 or "data" not in r.json():
        return []
    elif r.json()["data"] is None:
        return []

    # 使用示例
    data = r.json()["data"]
    return data

class PlanSOP(object):
    def __init__(self, request_id):
        self.request_id = request_id

        self.high_query_similarity_threshold = DEFAULT_HIGH_QUERY_SIMILARITY_THRESHOLD

        self.no_sop_similarity_threshold = DEFAULT_NO_SOP_SIMILARITY_THRESHOLD
        self.max_recall_sop_number = MAX_RECALL_SOP_NUMBER
        self.filter_minimum_count =  DEFAULT_FILTER_MINIMUM_COUNT
        self.use_rerank_length = 5
        self.high_recall_sop_number = HIGH_RECALL_SOP_NUMBER
        self.bge_rerank_url = os.getenv("SOP_BGE_RERANK_URL")

    def sop_dedup(self, sops):
        visited_sop = set()
        dedup_sops = []
        for sop in sops:
            sop_id = sop.sop_id
            key = f"{sop_id}"

            if key not in visited_sop:
                dedup_sops.append(sop)
            else:
                continue
            visited_sop.add(key)

        return dedup_sops

    def sop_choose(self, query, sop_list=[]):
        SOP_QDRANT_ENABLE = _env_flag("SOP_QDRANT_ENABLE", default=False)
        if not SOP_QDRANT_ENABLE and sop_list:
            name_scores = get_rerank(query=query, doc_list=[sop["sop_name"] for sop in sop_list], request_id=self.request_id, url=self.bge_rerank_url)
            step_scores = get_rerank(query=query, doc_list=[sop["sop_string"] for sop in sop_list],
                                     request_id=self.request_id, url=self.bge_rerank_url)
            sop_name_recall = []
            sop_steps_recall = []

            for name_score, steps_score, sop in zip(name_scores, step_scores, sop_list):
                sop["score"] = name_score + steps_score
                sop_name_recall.append(SOPDict(**sop))

        else:
            sop_name_recall = self.sop_recall(query, vector_type="name")
            sop_steps_recall = self.sop_recall(query, vector_type="sop_string")

        # sop id 去重
        all_sop_recall = sop_name_recall + sop_steps_recall
        all_sop_recall = self.sop_dedup(all_sop_recall)

        all_sop_recall = sorted(all_sop_recall, key=lambda x: x.score, reverse=True)
        all_sop_recall = all_sop_recall[:self.max_recall_sop_number * 2]

        sop_mode, choosed_sop = self._get_filter_mode(all_sop_recall)

        choosed_sop_string = ""
        plan_sop_prompts = get_prompt("plan_sop")

        if sop_mode == SOP_MODE.HIGH_MODE:
            prompt = plan_sop_prompts["high_mode_prompt"]

        elif sop_mode == SOP_MODE.COMMON_MODE and len(choosed_sop) > 0:
            prompt = Template(plan_sop_prompts["common_mode_prompt"]).render(sop_length=len(choosed_sop))
        else:
            prompt = plan_sop_prompts["no_sop_mode_prompt"]

        choosed_sop_string += prompt
        for sop_index, sop in enumerate(choosed_sop):
            json_sop = safe_literal_eval(sop.sop_json_string)
            sop_desc = json_sop.get("sop_name", "")
            sop_name = json_sop.get("sop_desc", "")

            if sop_desc.strip() != "":
                choosed_sop_string += f"\n标准执行流程（SOP）编号{sop_index + 1}，名为 {sop_name}，描述为{sop_desc}，步骤如下：\n"
            else:
                choosed_sop_string += f"\n标准执行流程（SOP）编号{sop_index + 1}，名为 {sop_name}，步骤如下：\n"

            sop_steps = json_sop.get("sop_steps", [])
            step_number = 1
            for sop_index, sop_step in enumerate(sop_steps):
                step_title = sop_step["title"]
                step_title = re.sub(r'^\d+[\.、，。]', '', step_title)

                steps = sop_step["steps"]
                for step_index, step in enumerate(steps):
                    step = re.sub(r'[:：]', '-', step)
                    step_format = f"执行顺序{step_number}. {step_title}: {step}".strip()
                    choosed_sop_string += step_format + "\n"
                    step_number += 1

            choosed_sop_string += "\n"
        return sop_mode, choosed_sop_string

    @timer("sop_recall")
    def sop_recall(self, query, vector_type="name") -> str | None:
        filters = {
            "vector_type": vector_type
        }
        qdrant_url = os.getenv('TR_QDRANT_URL', None)
        collection_name = os.getenv('SOP_COLLECTION_NAME', None)
        SOP_QDRANT_ENABLE = _env_flag("SOP_QDRANT_ENABLE", default=False)
        if SOP_QDRANT_ENABLE:
            try:
                if qdrant_url:
                    _sops = get_qd_server_recall(query,
                                                 filters,
                                                 collection_name,
                                                 qdrant_url=qdrant_url,
                                                 limit=self.max_recall_sop_number,
                                                 threshhold=-1,
                                                 timeout=0.5 * 100000000) #毫秒
                else:
                    qdrant_recall_obj = QdrantRecall(
                        host=os.getenv('QDRANT_HOST'),
                        port=os.getenv("QDRANT_PORT", None),
                        api_key=os.getenv("QDRANT_API_KEY", None),
                        collection_name=collection_name,
                        qdrant_limit=self.max_recall_sop_number,
                        threshhold=-1,
                        timeout=0.5 * 100000000
                    )
                    embedding_url = os.getenv("EMBEDDING_URL")
                    emb_client = EmbeddingClient(embedding_url)
                    query_vector = emb_client.get_vector(query)
                    _sops = qdrant_recall_obj.search(query_vector, filters)
            except Exception as error:
                logger.warning(f"SOP qdrant 召回失败，降级到默认 SOP。error={error}")
                _sops = self._build_default_sops()
        else:
            logger.info("SOP_QDRANT_ENABLE 未开启，使用默认 SOP。")
            _sops = self._build_default_sops()
        logger.info(f"sn: {self.request_id} recall res {_sops}")
        recall_sops = [SOPDict(**t) for t in _sops]
        return recall_sops

    @staticmethod
    def _build_default_sops():
        """qdrant 不可用时的兜底 SOP，保证 PlanSolve 至少能继续规划。"""
        return [
            {
                "vector_type": "sop_string",
                "description": "对销售数据进行综合分析",
                "sop_name": "对销售数据进行综合分析",
                "sop_json_string": "{\"sop_desc\": \"对销售数据进行综合分析\", \"sop_name\": \"对销售数据进行综合分析\", \"sop_steps\": [{\"steps\": [\"使用分析工具，按月/季度/年统计销售额、利润等，识别周期性变化。\"], \"title\": \"进行销售趋势分析\"}, {\"steps\": [\"使用分析工具，对公司、消费者、小型企业等不同客户群体进行对比分析。\"], \"title\": \"进行客户细分分析\"}, {\"steps\": [\"使用分析工具，对地区/城市进行分析：挖掘区域市场差异，发现潜力市场。\"], \"title\": \"销售客户细分分析\"}, {\"steps\": [\"使用分析工具，对销售产品类别分析：家具、技术、办公用品等类别的销售表现、利润贡献。\"], \"title\": \"销售产品类别分析\"}, {\"steps\": [\"基于前面步骤的分析和结论，进行汇总展示最终的 HTML 报告\"], \"title\": \"报告呈现\"}]}",
                "sop_string": "对销售数据进行综合分析\n对销售数据进行综合分析进行销售趋势分析使用分析工具，按月/季度/年统计销售额、利润等，识别周期性变化。\n进行客户细分分析使用分析工具，对公司、消费者、小型企业等不同客户群体进行对比分析。\n销售客户细分分析使用分析工具，对地区/城市进行分析：挖掘区域市场差异，发现潜力市场。\n销售产品类别分析使用分析工具，对销售产品类别分析：家具、技术、办公用品等类别的销售表现、利润贡献。\n报告呈现基于前面步骤的分析和结论，进行汇总展示最终的 HTML 报告",
                "sop_id": "1",
                "sop_type": "list",
                "score": 0.636863648891449
            }
        ]


    def _get_filter_mode(self, recall_sops):

        choosed_sop = recall_sops

        if not recall_sops:
            filter_mode = SOP_MODE.NO_SOP_MODE

        # 高相关模式，直接执行sop
        elif recall_sops[0].score > self.high_query_similarity_threshold:
            filter_mode = SOP_MODE.HIGH_MODE
            choosed_sop = recall_sops[:self.high_recall_sop_number]

        # 低相关模式，参考sop 生成sop
        elif recall_sops[0].score < self.no_sop_similarity_threshold:
            filter_mode = SOP_MODE.NO_SOP_MODE
            choosed_sop = recall_sops[:self.max_recall_sop_number]
        else:
            filter_mode = SOP_MODE.COMMON_MODE
            choosed_sop = recall_sops[:self.max_recall_sop_number]

        logger.info(f"sn {self.request_id} 模式 {filter_mode} choosed_sop: {choosed_sop}")
        return filter_mode, choosed_sop


if __name__ == "__main__":
    SOP1 = {
        "sop_name": "人才流动分析",
        "sop_desc": "分析人力构成，目标是看到人员变化趋势，找到主要影响人员变化的群体，分析原因",
        "steps": [
            ["1.通过{{分析工具}}，对人才的概况进行描述分析", "目标：获取组织人员的构成和变化趋势"],
            ["2.通过{{分析工具}}，对关键群体进行对比分析",
             "目标：获取关键群体（校招生、老员工、职级序列为P的员工）的占比，并进行群体的对比分析"],
            ["3.通过{{分析工具}}，按照群体探索员工留存的规律",
             "目标：对比分析关键群体（校招生、老员工、职级序列为P的员工）的留存情况"],
            ["4.通过{{分析工具}}，获取组织内高绩效员工的画像", "目标：通过归因分析，找到组织内获得高绩效员工的影响因子。"],
            ["5.通过{{html工具}}，形成可视化报告", "生成报告"]
        ],
    }

    _sops = [
        { "description": "SOP描述", "sop_id": "1",
          "sop_name": "sop_name",
          "sop_json_string": "{\"sopDesc\":\"SOP描述\",\"sopName\":\"SOP名称111\",\"sopSteps\":[{\"steps\":[\"步骤内容\",\"步骤内容\",\"步骤内容\",\"步骤内容\"],\"title\":\"步骤标题\"},{\"steps\":[\"步骤内容3\",\"步骤内容1\",\"步骤内容2\"],\"title\":\"步骤标题\"},{\"steps\":[\"步骤内容\",\"步骤内容\"],\"title\":\"步骤标题\"}]}",
          "sop_string": "SOP名称111\nSOP描述\n步骤标题\n步骤内容\n步骤内容\n步骤内容\n步骤内容\n步骤标题\n步骤内容3\n步骤内容1\n步骤内容2\n步骤标题\n步骤内容\n步骤内容\n",
          "sop_type": "list",
          "vector_type": "vector_type"
          },
        {
            "description": "SOP描述",
            "sop_id": "3",
            "sop_json_string": "{\"sopDesc\":\"SOP描述\",\"sopName\":\"SOP名称111\",\"sopSteps\":[{\"steps\":[\"步骤内容\",\"步骤内容\",\"步骤内容\",\"步骤内容\"],\"title\":\"步骤标题\"},{\"steps\":[\"步骤内容3\",\"步骤内容1\",\"步骤内容2\"],\"title\":\"步骤标题\"},{\"steps\":[\"步骤内容\",\"步骤内容\"],\"title\":\"步骤标题\"}]}",
            "sop_name": "SOP名称111",
            "sop_string": "SOP名称111\nSOP描述\n步骤标题\n步骤内容\n步骤内容\n步骤内容\n步骤内容\n步骤标题\n步骤内容3\n步骤内容1\n步骤内容2\n步骤标题\n步骤内容\n步骤内容\n",
            "sop_type": "list",
            "vector_type": "vector_type"
        }

    ]

    _sops = [SOPDict(**t) for t in _sops]

    sop_id_list = [sop.sop_id for sop in _sops]

    pl_sop = PlanSOP(request_id=123)

    r = pl_sop.sop_choose(query="人才流动分析", sop_list=_sops)

    print(">>> r = ", r)
