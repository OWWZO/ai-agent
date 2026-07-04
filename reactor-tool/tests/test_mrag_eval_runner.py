# -*- coding: utf-8 -*-
import json
import tempfile
import unittest
from pathlib import Path


class MragEvalRunnerTest(unittest.TestCase):

    def test_should_export_deduplicated_manifest_records_from_payloads(self):
        from reactor_tool.tool.mrag.eval.dataset import build_manifest_records

        payloads = [
            {
                "chunk_type": "text",
                "filename": "demo.pdf",
                "text": "第一段",
                "file_sorted": "f-1",
                "file_path": "docs/demo.pdf",
            },
            {
                "chunk_type": "text",
                "filename": "demo.pdf",
                "text": "第一段",
                "file_sorted": "f-2",
                "file_path": "docs/demo.pdf",
            },
            {
                "chunk_type": "image",
                "filename": "demo.pdf",
                "image_path": "images/img_1.png",
                "image_id": "img-1",
            },
            {
                "chunk_type": "page",
                "filename": "demo.pdf",
                "page_path": "pages/page_1.png",
                "page_id": "page-1",
            },
        ]

        manifest_records = build_manifest_records(payloads)

        self.assertEqual(3, len(manifest_records))
        self.assertEqual(
            ["image", "page", "text"],
            sorted(record.evidence_type for record in manifest_records),
        )

    def test_should_load_and_dump_jsonl_datasets(self):
        from reactor_tool.tool.mrag.eval.dataset import (
            dump_jsonl_records,
            load_qrels,
            load_queries,
        )
        from reactor_tool.tool.mrag.eval.models import EvalQrel, EvalQuery

        with tempfile.TemporaryDirectory(prefix="mrag-eval-dataset-") as temp_dir:
            queries_path = Path(temp_dir) / "queries.jsonl"
            qrels_path = Path(temp_dir) / "qrels.jsonl"

            dump_jsonl_records(
                queries_path,
                [
                    EvalQuery(query_id="q1", question="问题1", requires_retrieval=True),
                    EvalQuery(query_id="q2", question="问题2", requires_retrieval=False),
                ],
            )
            dump_jsonl_records(
                qrels_path,
                [
                    EvalQrel(query_id="q1", canonical_key="text:demo:a", grade=2),
                    EvalQrel(query_id="q2", canonical_key="text:demo:b", grade=1),
                ],
            )

            loaded_queries = load_queries(queries_path)
            loaded_qrels = load_qrels(qrels_path)

        self.assertEqual(["q1", "q2"], [query.query_id for query in loaded_queries])
        self.assertEqual(["text:demo:a", "text:demo:b"], [qrel.canonical_key for qrel in loaded_qrels])

    def test_should_compute_fixed_stage_recall_for_retrieval_required_queries_only(self):
        from reactor_tool.tool.mrag.eval.metrics import compute_recall_report
        from reactor_tool.tool.mrag.eval.models import EvalQrel, EvalQuery
        from reactor_tool.tool.mrag.eval.trace import RetrievalTrace, RetrievalTraceHit, RetrievalTraceRound, RetrievalTraceStage

        trace_q1 = RetrievalTrace(
            question="问题1",
            rounds=[
                RetrievalTraceRound(
                    stage="round1_raw",
                    queries=["问题1"],
                    hits=[
                        RetrievalTraceHit(
                            stage="round1_raw",
                            query="问题1",
                            score=0.9,
                            runtime_key="f-1",
                            canonical_key="text:demo:gold",
                            payload={"chunk_type": "text", "filename": "demo.pdf", "text": "gold", "file_sorted": "f-1"},
                        )
                    ],
                )
            ],
            round1_raw=RetrievalTraceStage(
                stage="round1_raw",
                hits=[
                    RetrievalTraceHit(
                        stage="round1_raw",
                        query="问题1",
                        score=0.9,
                        runtime_key="f-1",
                        canonical_key="text:demo:gold",
                        payload={"chunk_type": "text", "filename": "demo.pdf", "text": "gold", "file_sorted": "f-1"},
                    )
                ],
            ),
            all_rounds_raw=RetrievalTraceStage(stage="all_rounds_raw", hits=[]),
            merged_text=RetrievalTraceStage(stage="merged_text", hits=[]),
            merged_image=RetrievalTraceStage(stage="merged_image", hits=[]),
            merged_page=RetrievalTraceStage(stage="merged_page", hits=[]),
            merged_all=RetrievalTraceStage(stage="merged_all", hits=[]),
            rerank_text=RetrievalTraceStage(stage="rerank_text", hits=[]),
            answer_image_urls=[],
        )
        trace_q2 = RetrievalTrace(
            question="问题2",
            rounds=[],
            round1_raw=RetrievalTraceStage(stage="round1_raw", hits=[]),
            all_rounds_raw=RetrievalTraceStage(stage="all_rounds_raw", hits=[]),
            merged_text=RetrievalTraceStage(stage="merged_text", hits=[]),
            merged_image=RetrievalTraceStage(stage="merged_image", hits=[]),
            merged_page=RetrievalTraceStage(stage="merged_page", hits=[]),
            merged_all=RetrievalTraceStage(stage="merged_all", hits=[]),
            rerank_text=RetrievalTraceStage(stage="rerank_text", hits=[]),
            answer_image_urls=[],
        )

        report = compute_recall_report(
            dataset_name="sample",
            kb_id="kb-demo",
            queries=[
                EvalQuery(query_id="q1", question="问题1", requires_retrieval=True),
                EvalQuery(query_id="q2", question="问题2", requires_retrieval=False),
            ],
            qrels=[
                EvalQrel(query_id="q1", canonical_key="text:demo:gold", grade=2),
                EvalQrel(query_id="q2", canonical_key="text:demo:ignored", grade=2),
            ],
            traces_by_query_id={
                "q1": trace_q1,
                "q2": trace_q2,
            },
            min_recall_grade=2,
        )

        dumped = report.model_dump(mode="json")
        self.assertEqual(1, dumped["metrics"]["round1_raw"]["evaluated_queries"])
        self.assertEqual(1, dumped["metrics"]["round1_raw"]["matched_queries"])
        self.assertEqual(1.0, dumped["metrics"]["round1_raw"]["top1"])
        self.assertEqual(
            {
                "round1_raw",
                "all_rounds_raw",
                "merged_text",
                "merged_image",
                "merged_page",
                "merged_all",
                "rerank_text",
            },
            set(dumped["metrics"].keys()),
        )

    def test_should_run_recall_from_dataset_directory(self):
        from reactor_tool.tool.mrag.eval.dataset import dump_jsonl_records
        from reactor_tool.tool.mrag.eval.models import EvalQrel, EvalQuery
        from reactor_tool.tool.mrag.eval.runner import run_recall_evaluation
        from reactor_tool.tool.mrag.eval.trace import RetrievalTrace, RetrievalTraceStage

        with tempfile.TemporaryDirectory(prefix="mrag-eval-runner-") as temp_dir:
            dataset_dir = Path(temp_dir)
            dump_jsonl_records(
                dataset_dir / "queries.jsonl",
                [EvalQuery(query_id="q1", question="问题1", requires_retrieval=True)],
            )
            dump_jsonl_records(
                dataset_dir / "qrels.jsonl",
                [EvalQrel(query_id="q1", canonical_key="text:demo:gold", grade=2)],
            )

            report = run_recall_evaluation(
                kb_id="kb-demo",
                dataset_dir=dataset_dir,
                trace_collector=lambda query: RetrievalTrace(
                    question=query.question,
                    rounds=[],
                    round1_raw=RetrievalTraceStage(stage="round1_raw", hits=[]),
                    all_rounds_raw=RetrievalTraceStage(stage="all_rounds_raw", hits=[]),
                    merged_text=RetrievalTraceStage(stage="merged_text", hits=[]),
                    merged_image=RetrievalTraceStage(stage="merged_image", hits=[]),
                    merged_page=RetrievalTraceStage(stage="merged_page", hits=[]),
                    merged_all=RetrievalTraceStage(stage="merged_all", hits=[]),
                    rerank_text=RetrievalTraceStage(stage="rerank_text", hits=[]),
                    answer_image_urls=[],
                ),
            )

            output_json = json.dumps(report.model_dump(mode="json"), ensure_ascii=False)

        self.assertIn("\"dataset_name\": \"mrag-eval-runner-", output_json)
        self.assertIn("\"round1_raw\"", output_json)


if __name__ == "__main__":
    unittest.main()
