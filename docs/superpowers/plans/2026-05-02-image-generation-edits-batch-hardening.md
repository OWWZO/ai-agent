# Image Generation Edits And Batch Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 提升图生图局部编辑质量，并为工作台补齐多图批处理执行能力与基础容错。

**Architecture:** 先在 `reactor-tool` 的图片生成策略层引入“单图图生图优先 `/images/edits`，失败再降级”的路由能力，让 Java 工作台与 `ImageGenerationTool` 共用收益。再在 `ui/` 工作台侧引入轻量批处理编排，把多图图生图拆成多个独立请求，保持现有历史、调试面板与后端持久化链路不变。

**Tech Stack:** Python 3.11 + FastAPI + httpx + unittest；TypeScript 5 + React 19 + Vitest；Java 后端链路保持不改协议。

---

### Task 1: 加固 Python 生图策略层

**Files:**
- Modify: `reactor-tool/tests/test_image_generation.py`
- Modify: `reactor-tool/reactor_tool/tool/image_generation.py`

- [ ] **Step 1: 写单图图生图优先走 `/images/edits` 的失败测试**

```python
def test_should_build_native_edits_request_for_single_edit_without_mask(self):
    request = ImageGenerationRequest.model_validate(
        {
            "requestId": "req-edits-001",
            "prompt": "把图片改成黄昏氛围",
            "mode": "edits",
            "fileNames": ["https://example.com/source.png"],
            "size": "1024x1024",
        }
    )

    async def _run():
        async with httpx.AsyncClient() as client:
            primary, fallback = await _build_generation_requests(
                request=request,
                mode="edits",
                base_url="https://example.com/v1",
                model_name="gpt-image-2",
                client=client,
            )
            self.assertEqual("https://example.com/v1/images/edits", primary["url"])
            self.assertTrue(primary.get("multipart"))
            self.assertEqual("https://example.com/v1/chat/completions", fallback["url"])

    asyncio.run(_run())
```

- [ ] **Step 2: 运行 Python 单测，确认新断言先失败**

Run: `cd reactor-tool && python -m unittest tests.test_image_generation.ImageGenerationToolTest.test_should_build_native_edits_request_for_single_edit_without_mask`

Expected: FAIL，当前实现仍然返回 `/responses`

- [ ] **Step 3: 写带蒙版时也走 `/images/edits` 的失败测试**

```python
def test_should_attach_alpha_mask_when_single_edit_contains_mask(self):
    request = ImageGenerationRequest.model_validate(
        {
            "requestId": "req-edits-002",
            "prompt": "只修改红色标记区域",
            "mode": "edits",
            "fileNames": ["data:image/png;base64,..."],
            "maskFileNames": ["data:image/png;base64,..."],
        }
    )

    async def _run():
        async with httpx.AsyncClient() as client:
            primary, _ = await _build_generation_requests(
                request=request,
                mode="edits",
                base_url="https://example.com/v1",
                model_name="gpt-image-2",
                client=client,
            )
            self.assertTrue(primary.get("multipart"))
            self.assertEqual("https://example.com/v1/images/edits", primary["url"])

    asyncio.run(_run())
```

- [ ] **Step 4: 实现最小策略改动**

```python
if mode == "edits" and len(request.file_names) == 1:
    edits_form = await _build_native_edits_form(...)
    primary_request = {
        "url": f"{base_url}/images/edits",
        "body": edits_form,
        "multipart": True,
    }
    fallback_request = {
        "url": f"{base_url}/chat/completions",
        "body": {...},
    }
    return primary_request, fallback_request
```

- [ ] **Step 5: 让执行器支持 multipart 请求**

```python
if primary_request.get("multipart"):
    response = await client.post(
        primary_request["url"],
        headers=_build_openai_compat_headers({"Authorization": f"Bearer {api_key}"}),
        files=primary_request["body"],
    )
else:
    response = await client.post(..., json=primary_request["body"])
```

- [ ] **Step 6: 运行 Python 生图单测**

Run: `cd reactor-tool && python -m unittest tests.test_image_generation -v`

Expected: PASS

- [ ] **Step 7: 提交本任务改动**

```bash
git add reactor-tool/tests/test_image_generation.py reactor-tool/reactor_tool/tool/image_generation.py
git commit -m "feat: prefer native edits for single image editing"
```

### Task 2: 为工作台提取批处理编排逻辑并补测试

**Files:**
- Create: `ui/src/pages/WorkspaceImageGeneration/batch.ts`
- Create: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`
- Modify: `ui/src/pages/WorkspaceImageGeneration/types.ts`
- Modify: `ui/src/pages/WorkspaceImageGeneration/index.tsx`

- [ ] **Step 1: 为批处理判定写 failing test**

```ts
it("returns batch mode when edits has multiple images and switch is enabled", () => {
  expect(
    shouldUseImageBatchMode({
      mode: "edits",
      imageCount: 3,
      batchMode: true,
    })
  ).toBe(true);
});
```

- [ ] **Step 2: 为批处理拆分写 failing test**

```ts
it("splits multi-image edit request into one request per image", () => {
  const plans = buildImageBatchPlans({
    prompt: "统一改成电影海报",
    size: "1024x1024",
    images: [
      { fileName: "1.png", source: "data:image/png;base64,1", mask: "" },
      { fileName: "2.png", source: "data:image/png;base64,2", mask: "data:image/png;base64,mask" },
    ],
  });

  expect(plans).toHaveLength(2);
  expect(plans[0].fileNames).toEqual(["data:image/png;base64,1"]);
  expect(plans[1].maskFileNames).toEqual(["data:image/png;base64,mask"]);
});
```

- [ ] **Step 3: 运行 Vitest，确认先红**

Run: `cd ui && npm test -- src/pages/WorkspaceImageGeneration/batch.test.ts`

Expected: FAIL，工具函数尚不存在

- [ ] **Step 4: 实现最小批处理工具函数**

```ts
export function shouldUseImageBatchMode(input: {
  mode: RequestMode;
  imageCount: number;
  batchMode: boolean;
}) {
  return input.mode === "edits" && input.batchMode && input.imageCount > 1;
}

export function buildImageBatchPlans(...) {
  return input.images.map((image, index) => ({
    key: `${index + 1}`,
    fileNames: [image.source],
    maskFileNames: [image.mask || ""],
  }));
}
```

- [ ] **Step 5: 在工作台页面接入批处理开关和执行逻辑**

```ts
if (shouldUseImageBatchMode(...)) {
  const batchPlans = buildImageBatchPlans(...);
  const responses = await runImageBatchRequests(batchPlans, requestImageGenerationTool);
  // 聚合消息、逐项错误、刷新历史
} else {
  const toolResponse = await requestImageGenerationTool(...);
}
```

- [ ] **Step 6: 运行批处理测试**

Run: `cd ui && npm test -- src/pages/WorkspaceImageGeneration/batch.test.ts`

Expected: PASS

- [ ] **Step 7: 运行前端 lint**

Run: `cd ui && npm run lint`

Expected: PASS

- [ ] **Step 8: 提交本任务改动**

```bash
git add ui/src/pages/WorkspaceImageGeneration/batch.ts ui/src/pages/WorkspaceImageGeneration/batch.test.ts ui/src/pages/WorkspaceImageGeneration/types.ts ui/src/pages/WorkspaceImageGeneration/index.tsx
git commit -m "feat: add workspace image batch execution"
```

### Task 3: 联合验证

**Files:**
- Modify: `reactor-tool/tests/test_image_generation.py`（如需补回归断言）
- Modify: `ui/src/pages/WorkspaceImageGeneration/batch.test.ts`（如需补回归断言）

- [ ] **Step 1: 运行 Python 回归**

Run: `cd reactor-tool && python -m unittest tests.test_image_generation -v`

Expected: PASS

- [ ] **Step 2: 运行前端相关测试**

Run: `cd ui && npm test -- src/pages/WorkspaceImageGeneration/batch.test.ts`

Expected: PASS

- [ ] **Step 3: 运行前端 lint**

Run: `cd ui && npm run lint`

Expected: PASS

- [ ] **Step 4: 检查改动范围**

Run: `git diff --stat -- reactor-tool ui docs/superpowers/plans/2026-05-02-image-generation-edits-batch-hardening.md`

Expected: 仅包含本期计划内文件
