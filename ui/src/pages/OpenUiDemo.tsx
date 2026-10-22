import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Button } from "@/components/ui/button";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { Pause, Play, RotateCcw } from 'lucide-react';

import MarkdownRenderer from '@/components/ActionPanel/MarkdownRenderer';
import { OpenUiRender } from '@/components/openui/OpenUiRender';
import { splitOpenUiSegments } from '@/lib/openui/segments';

/**
 * 混排协议的验证页（开发用）：左边是 assistant 原文，右边是按 marker 分段后的真实渲染。
 * 不接对话流，只验证三件事——分段正确、Renderer 能渲染、样式不与 Tailwind/antd 互串。
 */
const SAMPLE = `第三季度整体表现好于预期，营收环比增长 8.2%，主要来自 9 月的渠道放量。

]]>openui:content
root = Col([head, kpis, chart, note], 5)
head = Title("Q3 营收报告", "xl")
kpis = Grid([k1, k2, k3], 3, 180, 4)
k1 = Card([Title("¥12.4M", "2xl"), Caption("总营收")], "soft", 4, 3)
k2 = Card([Title("+8.2%", "2xl", "success"), Caption("同比增长")], "soft", 4, 3)
k3 = Card([Title("3.1%", "2xl", "warning"), Caption("退货率")], "soft", 4, 3)
chart = Chart("bar", [{month: "7月", value: 12}, {month: "8月", value: 15}, {month: "9月", value: 18}], "month", ["value"], "currency", "CNY", null, 200)
note = Text("数据来源：ERP 系统，未经审计。", "xs", "muted")
]]>openui:end

如果需要看 9 月的渠道明细，可以继续问我。`;

export default function OpenUiDemo() {
  const [raw, setRaw] = useState(SAMPLE);
  const [shown, setShown] = useState(SAMPLE);
  const [dslOnly, setDslOnly] = useState(false);
  const [streaming, setStreaming] = useState(false);
  const timerRef = useRef<number | null>(null);

  const stopStreaming = useCallback(() => {
    if (timerRef.current !== null) {
      window.clearInterval(timerRef.current);
      timerRef.current = null;
    }
    setStreaming(false);
  }, []);

  useEffect(() => stopStreaming, [stopStreaming]);

  const startStreaming = useCallback(() => {
    stopStreaming();
    setStreaming(true);
    setShown('');
    let cursor = 0;
    // 每 40ms 吐 12 个字符，模拟 SSE 的增量到达，用来观察 UI 逐步长出来。
    timerRef.current = window.setInterval(() => {
      cursor = Math.min(raw.length, cursor + 12);
      setShown(raw.slice(0, cursor));
      if (cursor >= raw.length) stopStreaming();
    }, 40);
  }, [raw, stopStreaming]);

  const onEdit = (value: string) => {
    stopStreaming();
    setRaw(value);
    setShown(value);
  };

  const segments = useMemo(() => splitOpenUiSegments(shown), [shown]);
  const visible = dslOnly ? segments.filter((seg) => seg.type === 'openui') : segments;

  return (
    <div className="flex h-screen w-full flex-col gap-3 bg-white p-4">
      <header className="flex flex-wrap items-center gap-3">
        <h1 className="text-base font-semibold text-neutral-900">OpenUI 混排渲染验证</h1>
        <Badge>{segments.length} 段</Badge>
        <Badge variant="secondary">{segments.filter((s) => s.type === 'openui').length} 个程序块</Badge>
        <span className="ml-auto flex items-center gap-2 text-xs text-neutral-500">
          仅看 DSL
          <Switch size="sm" checked={dslOnly} onCheckedChange={setDslOnly} />
        </span>
        <Button variant="outline" size="sm" onClick={startStreaming} disabled={streaming}>
          <Play size={14} />
          模拟流式
        </Button>
        <Button variant="outline" size="sm" onClick={stopStreaming} disabled={!streaming}>
          <Pause size={14} />
          停止
        </Button>
        <Button
          variant="outline"
          size="sm"
          onClick={() => {
            stopStreaming();
            setRaw(SAMPLE);
            setShown(SAMPLE);
          }}
        >
          <RotateCcw size={14} />
          复位
        </Button>
      </header>

      <div className="flex min-h-0 flex-1 gap-3">
        <textarea
          className="h-full w-1/2 resize-none rounded-lg border border-neutral-200 bg-neutral-50 p-3 font-mono text-xs leading-relaxed text-neutral-800 outline-none focus:border-neutral-400"
          value={raw}
          onChange={(e) => onEdit(e.target.value)}
          spellCheck={false}
        />
        <div className="h-full w-1/2 overflow-auto rounded-lg border border-neutral-200 bg-white p-4">
          {visible.map((seg, index) =>
            seg.type === 'markdown' ? (
              <MarkdownRenderer
                key={`md-${index}`}
                markDownContent={seg.text}
                isStreaming={streaming && index === visible.length - 1}
              />
            ) : (
              <div key={`ui-${index}`} className="my-3 rounded-lg border border-dashed border-neutral-200 p-3">
                <OpenUiRender
                  text={seg.text}
                  streaming={streaming && !seg.closed}
                  onAction={(event) => {
                    // 验证页只打印，真实接入在 T8 里做。
                    console.info('[openui action]', event.type, event.humanFriendlyMessage, event.formState);
                  }}
                />
              </div>
            )
          )}
        </div>
      </div>
    </div>
  );
}
