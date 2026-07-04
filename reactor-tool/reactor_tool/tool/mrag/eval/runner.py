"""MRAG recall 评测执行器。"""

from pathlib import Path
from typing import Callable

from .dataset import load_qrels, load_queries
from .metrics import compute_recall_report
from .models import EvalQuery, RecallReport


def run_recall_evaluation(
    kb_id: str,
    dataset_dir: str | Path,
    trace_collector: Callable[[EvalQuery], object],
    min_recall_grade: int = 2,
) -> RecallReport:
    """读取数据集目录并运行 recall 评测。"""

    target_dir = Path(dataset_dir)
    queries = load_queries(target_dir / "queries.jsonl")
    qrels = load_qrels(target_dir / "qrels.jsonl")

    traces_by_query_id = {}
    for query in queries:
        if not query.requires_retrieval:
            continue
        traces_by_query_id[query.query_id] = trace_collector(query)

    return compute_recall_report(
        dataset_name=target_dir.name,
        kb_id=kb_id,
        queries=queries,
        qrels=qrels,
        traces_by_query_id=traces_by_query_id,
        min_recall_grade=min_recall_grade,
    )
