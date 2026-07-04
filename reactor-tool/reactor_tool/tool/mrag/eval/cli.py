"""MRAG recall 评测命令行入口。"""

import argparse
import json
from pathlib import Path

from reactor_tool.api.tool import build_mrag_agent
from reactor_tool.tool.mrag.storage import VectorStore

from .dataset import build_manifest_records, dump_jsonl_records
from .runner import run_recall_evaluation


def _collect_manifest_payloads(kb_id: str) -> list[dict]:
    """读取当前知识库的 text/image/page payload。"""

    vector_store = VectorStore()
    payloads = []
    for scroll_method in (
        vector_store.scroll_text_payloads,
        vector_store.scroll_image_payloads,
        vector_store.scroll_page_payloads,
    ):
        offset = None
        while True:
            points, offset = scroll_method(kb_id=kb_id, limit=200, offset=offset)
            payloads.extend(point["payload"] for point in points if point.get("payload"))
            if not offset:
                break
    return payloads


def export_manifest(kb_id: str, output_path: str | Path) -> Path:
    """导出 manifest JSONL。"""

    payloads = _collect_manifest_payloads(kb_id)
    records = build_manifest_records(payloads)
    target = Path(output_path)
    dump_jsonl_records(target, records)
    return target


def recall_dataset(kb_id: str, dataset_dir: str | Path) -> dict:
    """运行 recall 评测并返回 JSON 可序列化结果。"""

    def _collector(query):
        agent = build_mrag_agent(kb_id)
        return agent.collect_retrieval_trace(query.question)

    report = run_recall_evaluation(
        kb_id=kb_id,
        dataset_dir=dataset_dir,
        trace_collector=_collector,
    )
    return report.model_dump(mode="json")


def build_parser() -> argparse.ArgumentParser:
    """构建命令行参数。"""

    parser = argparse.ArgumentParser(description="MRAG recall evaluation utilities")
    subparsers = parser.add_subparsers(dest="command", required=True)

    export_parser = subparsers.add_parser("export-manifest", help="导出 manifest JSONL")
    export_parser.add_argument("--kb-id", required=True, help="目标知识库 ID")
    export_parser.add_argument("--output", required=True, help="manifest 输出路径")

    recall_parser = subparsers.add_parser("recall", help="运行 recall 评测")
    recall_parser.add_argument("--kb-id", required=True, help="目标知识库 ID")
    recall_parser.add_argument("--dataset-dir", required=True, help="包含 queries.jsonl/qrels.jsonl 的目录")

    return parser


def main(argv: list[str] | None = None) -> int:
    """CLI 主入口。"""

    parser = build_parser()
    args = parser.parse_args(argv)

    if args.command == "export-manifest":
        target = export_manifest(args.kb_id, args.output)
        print(json.dumps({"manifest_path": str(target)}, ensure_ascii=False))
        return 0

    if args.command == "recall":
        report = recall_dataset(args.kb_id, args.dataset_dir)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0

    parser.error(f"Unsupported command: {args.command}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
