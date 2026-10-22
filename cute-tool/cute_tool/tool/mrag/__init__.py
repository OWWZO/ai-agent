# -*- coding: utf-8 -*-
"""多模态 RAG（MRAG）全链路。

子包：
  query/       查询规划与 AgenticRAG
  document/    文档解析与切分
  embedding/   文本/图片/BM25 向量
  retrieval/   文本/图片检索
  rerank/      重排序
  generation/  LLM/VLM 生成
  storage/     知识库与会话持久化
  api/         MRAG 独立管理路由
  eval/        评测
  utils/       OCR、下载、日志等
"""
