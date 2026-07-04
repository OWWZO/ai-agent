# -*- coding: utf-8 -*-
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from reactor_tool.tool.mrag.eval.cli import export_manifest, main, recall_dataset
from reactor_tool.tool.mrag.eval.dataset import dump_jsonl_records
from reactor_tool.tool.mrag.eval.models import EvalQrel, EvalQuery
from reactor_tool.tool.mrag.eval.trace import RetrievalTrace, RetrievalTraceStage


class MragEvalCliTest(unittest.TestCase):

    def test_should_export_manifest_from_vector_store_payloads(self):
        fake_store = MagicMock()
        fake_store.scroll_text_payloads.return_value = ([{"payload": {"chunk_type": "text", "filename": "demo.pdf", "text": "第一段", "file_sorted": "f-1"}}], None)
        fake_store.scroll_image_payloads.return_value = ([{"payload": {"chunk_type": "image", "filename": "demo.pdf", "image_path": "images/img_1.png", "image_id": "img-1"}}], None)
        fake_store.scroll_page_payloads.return_value = ([{"payload": {"chunk_type": "page", "filename": "demo.pdf", "page_path": "pages/page_1.png", "page_id": "page-1"}}], None)

        with tempfile.TemporaryDirectory(prefix="mrag-cli-export-") as temp_dir:
            output_path = Path(temp_dir) / "manifest.jsonl"
            with patch("reactor_tool.tool.mrag.eval.cli.VectorStore", return_value=fake_store):
                manifest_path = export_manifest("kb-demo", output_path)

            lines = output_path.read_text(encoding="utf-8").strip().splitlines()

        self.assertEqual(output_path, manifest_path)
        self.assertEqual(3, len(lines))

    def test_should_run_recall_dataset_with_agent_trace_collector(self):
        with tempfile.TemporaryDirectory(prefix="mrag-cli-recall-") as temp_dir:
            dataset_dir = Path(temp_dir)
            dump_jsonl_records(
                dataset_dir / "queries.jsonl",
                [EvalQuery(query_id="q1", question="问题1", requires_retrieval=True)],
            )
            dump_jsonl_records(
                dataset_dir / "qrels.jsonl",
                [EvalQrel(query_id="q1", canonical_key="text:demo:gold", grade=2)],
            )

            fake_agent = MagicMock()
            fake_agent.collect_retrieval_trace.return_value = RetrievalTrace(
                question="问题1",
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

            with patch("reactor_tool.tool.mrag.eval.cli.build_mrag_agent", return_value=fake_agent):
                report = recall_dataset("kb-demo", dataset_dir)

        self.assertEqual("sample" not in report["dataset_name"], True)
        self.assertIn("round1_raw", report["metrics"])
        fake_agent.collect_retrieval_trace.assert_called_once_with("问题1")

    def test_should_print_json_for_main_subcommands(self):
        with tempfile.TemporaryDirectory(prefix="mrag-cli-main-") as temp_dir:
            dataset_dir = Path(temp_dir)
            output_path = dataset_dir / "manifest.jsonl"
            dump_jsonl_records(
                dataset_dir / "queries.jsonl",
                [EvalQuery(query_id="q1", question="问题1", requires_retrieval=True)],
            )
            dump_jsonl_records(
                dataset_dir / "qrels.jsonl",
                [EvalQrel(query_id="q1", canonical_key="text:demo:gold", grade=2)],
            )

            fake_store = MagicMock()
            fake_store.scroll_text_payloads.return_value = ([{"payload": {"chunk_type": "text", "filename": "demo.pdf", "text": "第一段", "file_sorted": "f-1"}}], None)
            fake_store.scroll_image_payloads.return_value = ([], None)
            fake_store.scroll_page_payloads.return_value = ([], None)

            fake_agent = MagicMock()
            fake_agent.collect_retrieval_trace.return_value = RetrievalTrace(
                question="问题1",
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

            with patch("reactor_tool.tool.mrag.eval.cli.VectorStore", return_value=fake_store), patch(
                "reactor_tool.tool.mrag.eval.cli.build_mrag_agent",
                return_value=fake_agent,
            ), patch("builtins.print") as fake_print:
                export_rc = main(["export-manifest", "--kb-id", "kb-demo", "--output", str(output_path)])
                recall_rc = main(["recall", "--kb-id", "kb-demo", "--dataset-dir", str(dataset_dir)])

        self.assertEqual(0, export_rc)
        self.assertEqual(0, recall_rc)
        printed_payloads = [call.args[0] for call in fake_print.call_args_list]
        self.assertTrue(any("manifest_path" in payload for payload in printed_payloads))
        self.assertTrue(any("\"metrics\"" in payload for payload in printed_payloads))


if __name__ == "__main__":
    unittest.main()
