"""MRAG 召回评测数据模型。"""

from typing import Literal

from pydantic import BaseModel, Field


STAGE_NAMES = (
    "round1_raw",
    "all_rounds_raw",
    "merged_text",
    "merged_image",
    "merged_page",
    "merged_all",
    "rerank_text",
)

EVIDENCE_TYPES = ("text", "image", "page")


class EvalQuery(BaseModel):
    """待评测 query 定义。"""

    query_id: str = Field(..., description="查询唯一标识")
    question: str = Field(..., description="自然语言问题")
    requires_retrieval: bool = Field(..., description="是否要求走检索链路")


class EvalQrel(BaseModel):
    """人工标注的 gold evidence 定义。"""

    query_id: str = Field(..., description="所属查询 ID")
    canonical_key: str = Field(..., description="稳定证据键")
    grade: int = Field(..., description="相关性等级")


class ManifestRecord(BaseModel):
    """供人工标注的证据清单记录。"""

    canonical_key: str = Field(..., description="稳定证据键")
    evidence_type: Literal["text", "image", "page"] = Field(..., description="证据类型")
    title: str = Field(..., description="展示标题")
    source_ref: str = Field(..., description="来源引用")
    preview: str = Field(..., description="人工标注预览内容")
    runtime_key: str | None = Field(None, description="当前快照下的运行时键")


class RecallStageMetrics(BaseModel):
    """单个阶段的 recall 统计。"""

    top1: float = Field(0.0, description="top1 recall")
    top3: float = Field(0.0, description="top3 recall")
    top5: float = Field(0.0, description="top5 recall")
    top10: float = Field(0.0, description="top10 recall")
    matched_queries: int = Field(0, description="命中任一 gold 的 query 数")
    evaluated_queries: int = Field(0, description="参与评测的 query 数")


class RecallMetricsBundle(BaseModel):
    """固定 schema 的阶段指标集合。"""

    round1_raw: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    all_rounds_raw: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    merged_text: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    merged_image: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    merged_page: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    merged_all: RecallStageMetrics = Field(default_factory=RecallStageMetrics)
    rerank_text: RecallStageMetrics = Field(default_factory=RecallStageMetrics)


class RecallReport(BaseModel):
    """整份 recall 评测结果。"""

    dataset_name: str = Field(..., description="评测集名称")
    kb_id: str = Field(..., description="知识库 ID")
    metrics: RecallMetricsBundle = Field(..., description="固定 schema 的评测指标")


def build_empty_metrics_bundle() -> RecallMetricsBundle:
    """构建一份全 0 的固定阶段指标。"""

    return RecallMetricsBundle()

