# MRAG Recall Evaluation

这套工具用于评估 `reactor-tool` 当前 MRAG 检索链路的 evidence recall，而不是最终答案质量。

## 目录结构

- `sample/queries.jsonl`
- `sample/qrels.jsonl`

`queries.jsonl` 每行一条 query：

```json
{"query_id":"sample-q1","question":"总结知识库里的 MRAG 召回流程","requires_retrieval":true}
```

`qrels.jsonl` 每行一条 gold evidence：

```json
{"query_id":"sample-q1","canonical_key":"text:demo.pdf:goldsample01","grade":2}
```

## 1. 导出 manifest

```bash
uv run python -m reactor_tool.tool.mrag.eval.cli export-manifest --kb-id <kb_id> --output evals/mrag/manifest.jsonl
```

输出是 JSONL，每条记录都包含：

- `canonical_key`
- `evidence_type`
- `title`
- `source_ref`
- `preview`
- `runtime_key`

## 2. 人工标注 qrels

基于 manifest 挑出真正应该命中的证据，把它们写到 `qrels.jsonl`。  
首期只标注 `text`、`image`、`page` 三类主证据。

## 3. 运行 recall

```bash
uv run python -m reactor_tool.tool.mrag.eval.cli recall --kb-id <kb_id> --dataset-dir evals/mrag/sample
```

CLI 会读取：

- `<dataset-dir>/queries.jsonl`
- `<dataset-dir>/qrels.jsonl`

只会评测 `requires_retrieval=true` 的 query。

## 固定 JSON Report Schema

最终输出固定包含以下 stages：

- `round1_raw`
- `all_rounds_raw`
- `merged_text`
- `merged_image`
- `merged_page`
- `merged_all`
- `rerank_text`

每个 stage 固定包含：

- `top1`
- `top3`
- `top5`
- `top10`
- `matched_queries`
- `evaluated_queries`

示例：

```json
{
  "dataset_name": "sample",
  "kb_id": "kb-demo",
  "metrics": {
    "round1_raw": {
      "top1": 0.0,
      "top3": 0.0,
      "top5": 0.0,
      "top10": 0.0,
      "matched_queries": 0,
      "evaluated_queries": 1
    }
  }
}
```

## 建议验证顺序

1. 先跑单测，确认 schema、trace、runner 都是绿的。
2. 再导出真实 manifest。
3. 手工补最小 `queries/qrels`。
4. 跑 `recall`，确认输出 JSON stages 和 top-k 字段齐全。
5. 最后额外验证一个非检索 query 和一个 simple image query，确保 answer path 没回归。
