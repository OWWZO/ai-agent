# -*- coding: utf-8 -*-
import unittest


class MragEvalModelsTest(unittest.TestCase):

    def test_should_validate_query_qrel_manifest_and_fixed_report_schema(self):
        from reactor_tool.tool.mrag.eval.models import (
            EvalQuery,
            EvalQrel,
            ManifestRecord,
            RecallReport,
            RecallStageMetrics,
            build_empty_metrics_bundle,
        )

        query = EvalQuery(
            query_id="q-1",
            question="总结 Reactor MRAG 的召回阶段",
            requires_retrieval=True,
        )
        qrel = EvalQrel(
            query_id="q-1",
            canonical_key="text:demo.pdf:abc123",
            grade=2,
        )
        manifest = ManifestRecord(
            canonical_key="text:demo.pdf:abc123",
            evidence_type="text",
            title="demo.pdf",
            source_ref="demo.pdf#chunk-1",
            preview="MRAG 会先做多路召回。",
            runtime_key="file-1-0",
        )

        empty_bundle = build_empty_metrics_bundle()
        report = RecallReport(
            dataset_name="sample",
            kb_id="kb-demo",
            metrics=empty_bundle,
        )

        self.assertEqual("q-1", query.query_id)
        self.assertEqual(2, qrel.grade)
        self.assertEqual("text", manifest.evidence_type)

        dumped_report = report.model_dump(mode="json")
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
            set(dumped_report["metrics"].keys()),
        )
        self.assertEqual(
            {
                "top1",
                "top3",
                "top5",
                "top10",
                "matched_queries",
                "evaluated_queries",
            },
            set(dumped_report["metrics"]["round1_raw"].keys()),
        )

        stage_metrics = RecallStageMetrics(
            top1=0.5,
            top3=1.0,
            top5=1.0,
            top10=1.0,
            matched_queries=1,
            evaluated_queries=2,
        )
        self.assertEqual(0.5, stage_metrics.top1)

    def test_should_build_stable_canonical_keys_for_text_image_and_page(self):
        from reactor_tool.tool.mrag.eval.canonical_keys import (
            build_canonical_key,
            build_runtime_key,
        )

        text_payload = {
            "chunk_type": "text",
            "filename": "demo.pdf",
            "text": " 第一段内容\\n\\n第二段内容 ",
            "file_sorted": "file-1-0",
        }
        same_text_payload = {
            "chunk_type": "text",
            "filename": "demo.pdf",
            "text": "第一段内容 第二段内容",
            "file_sorted": "file-1-9",
        }
        image_payload = {
            "chunk_type": "image",
            "filename": "demo.pdf",
            "image_path": "C:/tmp/images/page_2.png",
            "image_id": "image-2",
        }
        page_payload = {
            "chunk_type": "page",
            "filename": "demo.pdf",
            "page_path": "D:/kb/pages/page_5.png",
            "page_id": "page-5",
        }

        text_key = build_canonical_key(text_payload)
        same_text_key = build_canonical_key(same_text_payload)
        image_key = build_canonical_key(image_payload)
        page_key = build_canonical_key(page_payload)

        self.assertEqual(text_key, same_text_key)
        self.assertTrue(text_key.startswith("text:demo.pdf:"))
        self.assertEqual("image:demo.pdf:page_2.png", image_key)
        self.assertEqual("page:demo.pdf:page_5.png", page_key)
        self.assertEqual("file-1-0", build_runtime_key(text_payload))
        self.assertEqual("image-2", build_runtime_key(image_payload))
        self.assertEqual("page-5", build_runtime_key(page_payload))

    def test_should_treat_ocr_and_caption_as_text_like_keys_without_crashing(self):
        from reactor_tool.tool.mrag.eval.canonical_keys import build_canonical_key

        ocr_payload = {
            "chunk_type": "ocr_text",
            "filename": "demo.pdf",
            "text": "图中的文字内容",
            "image_id": "img-1",
        }
        caption_payload = {
            "chunk_type": "caption",
            "filename": "demo.pdf",
            "text": "这是一张流程图",
            "page_id": "page-1",
        }

        self.assertTrue(build_canonical_key(ocr_payload).startswith("text:demo.pdf:"))
        self.assertTrue(build_canonical_key(caption_payload).startswith("text:demo.pdf:"))


if __name__ == "__main__":
    unittest.main()
