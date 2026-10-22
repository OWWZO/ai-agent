"""MRAG 检索阶段 trace 模型。"""

from typing import Any

from pydantic import BaseModel, Field


class RetrievalTraceHit(BaseModel):
    """单条检索命中。"""

    stage: str = Field(..., description="命中所属阶段")
    query: str = Field(..., description="触发命中的查询")
    score: float = Field(..., description="当前阶段得分")
    runtime_key: str = Field(..., description="当前快照下的运行时键")
    canonical_key: str = Field(..., description="稳定证据键")
    payload: dict[str, Any] = Field(..., description="原始 payload")

    def to_chunk(self) -> dict[str, Any]:
        """还原为现有问答链路可复用的 chunk 结构。"""

        return {
            "score": self.score,
            "payload": self.payload,
        }


class RetrievalTraceRound(BaseModel):
    """单轮检索 trace。"""

    stage: str = Field(..., description="阶段名称")
    queries: list[str] = Field(default_factory=list, description="本轮实际发出的 query 列表")
    hits: list[RetrievalTraceHit] = Field(default_factory=list, description="本轮原始命中")


class RetrievalTraceStage(BaseModel):
    """聚合阶段 trace。"""

    stage: str = Field(..., description="阶段名称")
    hits: list[RetrievalTraceHit] = Field(default_factory=list, description="该阶段命中列表")


class RetrievalTrace(BaseModel):
    """完整检索 trace。"""

    question: str = Field(..., description="用户原始问题")
    rounds: list[RetrievalTraceRound] = Field(default_factory=list, description="逐轮原始命中")
    round1_raw: RetrievalTraceStage = Field(..., description="首轮原始命中")
    all_rounds_raw: RetrievalTraceStage = Field(..., description="全部轮次原始命中")
    merged_text: RetrievalTraceStage = Field(..., description="merge 后文本命中")
    merged_image: RetrievalTraceStage = Field(..., description="merge 后图片命中")
    merged_page: RetrievalTraceStage = Field(..., description="merge 后页面命中")
    merged_all: RetrievalTraceStage = Field(..., description="merge 后全量命中")
    rerank_text: RetrievalTraceStage = Field(..., description="rerank 后文本命中")
    answer_image_urls: list[str] = Field(default_factory=list, description="最终回答候选图片")
