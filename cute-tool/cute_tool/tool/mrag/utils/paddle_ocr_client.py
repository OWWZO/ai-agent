# -*- coding: utf-8 -*-
"""
百度飞桨 OCR Job API 客户端

职责：
1. 提交本地文件或 URL 到 PaddleOCR-VL
2. 轮询任务状态
3. 下载并解析 jsonl 结果
4. 提取可直接用于向量化的 markdown 文本
"""
import json
import os
import time
from typing import Any

import requests

from .logger_utils import logger


def _parse_bool_env(name: str, default: bool = False) -> bool:
    """解析布尔环境变量，兼容常见 true/false 写法。"""
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    return str(raw_value).strip().lower() in {"1", "true", "yes", "y", "on"}


class PaddleOCRClient:
    """PaddleOCR-VL 远端任务客户端。"""

    def __init__(self):
        self.job_url = (
            os.getenv("PADDLE_OCR_JOB_URL")
            or "https://paddleocr.aistudio-app.com/api/v2/ocr/jobs"
        ).strip()
        self.token = (os.getenv("PADDLE_OCR_TOKEN") or "").strip()
        self.model_name = (os.getenv("PADDLE_OCR_MODEL_NAME") or "PaddleOCR-VL-1.6").strip()
        self.poll_interval_seconds = float(os.getenv("PADDLE_OCR_POLL_INTERVAL_SECONDS", "5"))
        self.timeout_seconds = float(os.getenv("PADDLE_OCR_TIMEOUT_SECONDS", "300"))
        self.optional_payload = {
            "useDocOrientationClassify": _parse_bool_env(
                "PADDLE_OCR_USE_DOC_ORIENTATION_CLASSIFY", False
            ),
            "useDocUnwarping": _parse_bool_env("PADDLE_OCR_USE_DOC_UNWARPING", False),
            "useChartRecognition": _parse_bool_env("PADDLE_OCR_USE_CHART_RECOGNITION", False),
        }

    def extract_text(self, file_path: str) -> str:
        """执行完整 OCR 流程，并返回拼接后的 markdown 文本。"""
        if not self.token:
            raise ValueError("PADDLE_OCR_TOKEN 未配置")
        if not self.job_url:
            raise ValueError("PADDLE_OCR_JOB_URL 未配置")

        job_id = self._submit_job(file_path)
        jsonl_url = self._wait_for_result_url(job_id)
        if not jsonl_url:
            logger.warning("PaddleOCR 任务已完成，但未返回 jsonl 结果地址")
            return ""
        return self._download_markdown_text(jsonl_url)

    def _submit_job(self, file_path: str) -> str:
        """提交 OCR 任务，返回 jobId。"""
        headers = {"Authorization": f"bearer {self.token}"}
        if file_path.startswith("http://") or file_path.startswith("https://"):
            headers["Content-Type"] = "application/json"
            payload = {
                "fileUrl": file_path,
                "model": self.model_name,
                "optionalPayload": self.optional_payload,
            }
            response = requests.post(
                self.job_url,
                json=payload,
                headers=headers,
                timeout=self.timeout_seconds,
            )
        else:
            if not os.path.exists(file_path):
                raise FileNotFoundError(f"PaddleOCR 输入文件不存在: {file_path}")
            data = {
                "model": self.model_name,
                "optionalPayload": json.dumps(self.optional_payload, ensure_ascii=False),
            }
            with open(file_path, "rb") as file_stream:
                response = requests.post(
                    self.job_url,
                    headers=headers,
                    data=data,
                    files={"file": file_stream},
                    timeout=self.timeout_seconds,
                )

        response.raise_for_status()
        payload = response.json()
        job_id = ((payload.get("data") or {}).get("jobId") or "").strip()
        if not job_id:
            raise ValueError(f"PaddleOCR 提交任务成功，但返回中缺少 jobId: {payload}")
        return job_id

    def _wait_for_result_url(self, job_id: str) -> str:
        """轮询任务状态，直到拿到 jsonl 结果地址或超时。"""
        status_url = f"{self.job_url}/{job_id}"
        headers = {"Authorization": f"bearer {self.token}"}
        start_time = time.time()

        while True:
            response = requests.get(status_url, headers=headers, timeout=self.timeout_seconds)
            response.raise_for_status()
            payload = response.json().get("data") or {}
            state = str(payload.get("state") or "").strip().lower()

            if state == "done":
                return str(((payload.get("resultUrl") or {}).get("jsonUrl") or "")).strip()
            if state == "failed":
                error_msg = str(payload.get("errorMsg") or "unknown error").strip()
                raise RuntimeError(f"PaddleOCR 任务失败: {error_msg}")

            if state == "running":
                progress = payload.get("extractProgress") or {}
                total_pages = progress.get("totalPages")
                extracted_pages = progress.get("extractedPages")
                logger.info(
                    f"PaddleOCR 任务运行中, job_id={job_id}, total_pages={total_pages}, extracted_pages={extracted_pages}"
                )
            else:
                logger.info(f"PaddleOCR 任务等待中, job_id={job_id}, state={state or 'pending'}")

            if time.time() - start_time > self.timeout_seconds:
                raise TimeoutError(f"PaddleOCR 任务超时: job_id={job_id}")

            time.sleep(self.poll_interval_seconds)

    def _download_markdown_text(self, jsonl_url: str) -> str:
        """下载 jsonl 结果并提取 markdown 文本。"""
        response = requests.get(jsonl_url, timeout=self.timeout_seconds)
        response.raise_for_status()
        text_fragments = []
        for line in response.text.strip().splitlines():
            normalized_line = line.strip()
            if not normalized_line:
                continue
            row = json.loads(normalized_line)
            text_fragments.extend(self._extract_markdown_texts(row))
        return "\n\n".join(fragment for fragment in text_fragments if fragment)

    @staticmethod
    def _extract_markdown_texts(row: dict[str, Any]) -> list[str]:
        """从单条 jsonl 记录中提取 markdown 文本。"""
        result = row.get("result") or {}
        layout_results = result.get("layoutParsingResults") or []
        text_fragments = []
        for layout_result in layout_results:
            markdown = layout_result.get("markdown") or {}
            markdown_text = str(markdown.get("text") or "").strip()
            if markdown_text:
                text_fragments.append(markdown_text)
        return text_fragments
