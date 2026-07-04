"""MRAG recall 指标计算。"""

from collections import defaultdict

from .models import EvalQrel, EvalQuery, RecallReport, RecallStageMetrics, build_empty_metrics_bundle


def _compute_stage_metrics(stage_hits_by_query: dict[str, list[str]], gold_by_query: dict[str, set[str]]) -> RecallStageMetrics:
    """按固定 top-k 计算单阶段 recall。"""

    evaluated_queries = len(gold_by_query)
    matched_queries_by_k = {1: 0, 3: 0, 5: 0, 10: 0}

    for query_id, gold_keys in gold_by_query.items():
        ranked_keys = stage_hits_by_query.get(query_id, [])
        for k in matched_queries_by_k:
            if any(hit_key in gold_keys for hit_key in ranked_keys[:k]):
                matched_queries_by_k[k] += 1

    matched_queries = matched_queries_by_k[10]
    if evaluated_queries == 0:
        return RecallStageMetrics(evaluated_queries=0, matched_queries=0)

    return RecallStageMetrics(
        top1=matched_queries_by_k[1] / evaluated_queries,
        top3=matched_queries_by_k[3] / evaluated_queries,
        top5=matched_queries_by_k[5] / evaluated_queries,
        top10=matched_queries_by_k[10] / evaluated_queries,
        matched_queries=matched_queries,
        evaluated_queries=evaluated_queries,
    )


def compute_recall_report(
    dataset_name: str,
    kb_id: str,
    queries: list[EvalQuery],
    qrels: list[EvalQrel],
    traces_by_query_id: dict[str, object],
    min_recall_grade: int = 2,
) -> RecallReport:
    """根据 qrels 和 retrieval traces 生成固定 schema 报表。"""

    eligible_queries = [query for query in queries if query.requires_retrieval]
    eligible_query_ids = {query.query_id for query in eligible_queries}

    gold_by_query: dict[str, set[str]] = defaultdict(set)
    for qrel in qrels:
        if qrel.query_id in eligible_query_ids and qrel.grade >= min_recall_grade:
            gold_by_query[qrel.query_id].add(qrel.canonical_key)

    metrics_bundle = build_empty_metrics_bundle()
    stage_names = (
        "round1_raw",
        "all_rounds_raw",
        "merged_text",
        "merged_image",
        "merged_page",
        "merged_all",
        "rerank_text",
    )

    for stage_name in stage_names:
        stage_hits_by_query = {}
        for query in eligible_queries:
            trace = traces_by_query_id.get(query.query_id)
            if trace is None:
                stage_hits_by_query[query.query_id] = []
                continue
            stage = getattr(trace, stage_name)
            stage_hits_by_query[query.query_id] = [hit.canonical_key for hit in stage.hits]
        setattr(
            metrics_bundle,
            stage_name,
            _compute_stage_metrics(stage_hits_by_query, gold_by_query),
        )

    return RecallReport(
        dataset_name=dataset_name,
        kb_id=kb_id,
        metrics=metrics_bundle,
    )
