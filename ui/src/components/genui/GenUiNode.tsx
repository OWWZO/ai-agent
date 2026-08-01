import { FC, memo, useState } from "react";
import classNames from "classnames";
import MarkdownRenderer from "@/components/ActionPanel/MarkdownRenderer";
import GenUiChart from "./GenUiChart";
import GenUiModel3D from "./GenUiModel3D";
import GenUiThreeJsFrame from "./GenUiThreeJsFrame";

export type GenUiNodeData = {
  nodeId?: string;
  kind?: string;
  props?: Record<string, any>;
  children?: GenUiNodeData[];
};

type Props = {
  node?: GenUiNodeData | null;
  depth?: number;
};

const textColor = (color?: string) => {
  switch (color) {
    case "muted":
      return "text-[var(--chat-text-soft)]";
    case "primary":
      return "text-[var(--chat-accent)]";
    case "success":
      return "text-emerald-600";
    case "warning":
      return "text-amber-600";
    case "error":
      return "text-red-600";
    default:
      return "text-[var(--chat-text)]";
  }
};

const GenUiTabs: FC<{ items: GenUiNodeData[]; depth: number }> = ({
  items,
  depth,
}) => {
  const [active, setActive] = useState(0);
  const safeActive = Math.min(Math.max(active, 0), Math.max(items.length - 1, 0));
  const current = items[safeActive];
  return (
    <div className="overflow-hidden rounded-xl border border-[var(--chat-border)]/60">
      <div className="flex flex-wrap gap-1 border-b border-[var(--chat-border)]/50 bg-[var(--chat-surface-soft)]/50 p-1.5">
        {items.map((item, index) => {
          const label =
            item.props?.label || item.props?.title || `标签 ${index + 1}`;
          const selected = index === safeActive;
          return (
            <button
              key={item.nodeId || `tab-${index}`}
              type="button"
              className={classNames(
                "rounded-md px-3 py-1.5 text-[12px] transition-colors",
                selected
                  ? "bg-white font-medium text-[var(--chat-text)] shadow-sm"
                  : "text-[var(--chat-text-soft)] hover:bg-white/70"
              )}
              onClick={() => setActive(index)}
            >
              {String(label)}
            </button>
          );
        })}
      </div>
      <div className="p-3">
        {current ? (
          <GenUiNode node={current} depth={depth + 1} />
        ) : (
          <div className="text-[12px] text-[var(--chat-text-soft)]">暂无内容</div>
        )}
      </div>
    </div>
  );
};

const GenUiAccordion: FC<{ items: GenUiNodeData[]; depth: number }> = ({
  items,
  depth,
}) => {
  const [open, setOpen] = useState(0);
  return (
    <div className="space-y-2">
      {items.map((item, index) => {
        const label =
          item.props?.label || item.props?.title || `分组 ${index + 1}`;
        const expanded = open === index;
        return (
          <div
            key={item.nodeId || `acc-${index}`}
            className="overflow-hidden rounded-lg border border-[var(--chat-border)]/60"
          >
            <button
              type="button"
              className="flex w-full items-center justify-between bg-[var(--chat-surface-soft)]/40 px-3 py-2 text-left text-[13px] font-medium text-[var(--chat-text)]"
              onClick={() => setOpen(expanded ? -1 : index)}
            >
              <span>{String(label)}</span>
              <span className="text-[11px] text-[var(--chat-text-soft)]">
                {expanded ? "收起" : "展开"}
              </span>
            </button>
            {expanded ? (
              <div className="border-t border-[var(--chat-border)]/40 p-3">
                <GenUiNode node={{ ...item, kind: "Stack" }} depth={depth + 1} />
              </div>
            ) : null}
          </div>
        );
      })}
    </div>
  );
};

const GenUiNode: FC<Props> = memo(({ node, depth = 0 }) => {
  if (!node || !node.kind) return null;
  const props = node.props || {};
  const children = Array.isArray(node.children) ? node.children : [];
  const key = node.nodeId || `${node.kind}-${depth}`;

  const renderChildren = () =>
    children.map((child, index) => (
      <GenUiNode key={child.nodeId || `${key}-${index}`} node={child} depth={depth + 1} />
    ));

  switch (node.kind) {
    case "DesignSurface":
      return (
        <div
          key={key}
          className={classNames(
            "rounded-2xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface)] p-4",
            props.padding === "lg" && "p-6",
            props.padding === "sm" && "p-3",
            props.padding === "none" && "p-0"
          )}
        >
          {renderChildren()}
        </div>
      );
    case "Stack":
      return (
        <div key={key} className="flex flex-col gap-3">
          {renderChildren()}
        </div>
      );
    case "Row":
      return (
        <div key={key} className="flex flex-wrap items-center gap-3">
          {renderChildren()}
        </div>
      );
    case "Grid": {
      const columns = Math.min(6, Math.max(1, Number(props.columns) || 2));
      return (
        <div
          key={key}
          className="grid gap-3"
          style={{ gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))` }}
        >
          {renderChildren()}
        </div>
      );
    }
    case "Spacer":
      return <div key={key} style={{ height: Number(props.size) || 12 }} />;
    case "AspectBox":
      return (
        <div key={key} className="w-full overflow-hidden rounded-xl border border-[var(--chat-border)]/50">
          <div className="p-3">{renderChildren()}</div>
        </div>
      );
    case "Card":
      return (
        <div
          key={key}
          className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface-soft)]/50 p-3 shadow-sm"
        >
          {props.title ? (
            <div className="mb-1 text-[14px] font-semibold text-[var(--chat-text)]">{props.title}</div>
          ) : null}
          {props.subtitle ? (
            <div className="mb-2 text-[12px] text-[var(--chat-text-soft)]">{props.subtitle}</div>
          ) : null}
          {renderChildren()}
        </div>
      );
    case "Heading": {
      const level = Math.min(4, Math.max(1, Number(props.level) || 2));
      const size =
        level === 1 ? "text-2xl" : level === 2 ? "text-xl" : level === 3 ? "text-lg" : "text-base";
      const text = props.value || props.text || props.title || "";
      const className = classNames(size, "font-semibold tracking-tight text-[var(--chat-text)]");
      if (level === 1) return <h1 key={key} className={className}>{text}</h1>;
      if (level === 2) return <h2 key={key} className={className}>{text}</h2>;
      if (level === 3) return <h3 key={key} className={className}>{text}</h3>;
      return <h4 key={key} className={className}>{text}</h4>;
    }
    case "Text":
      return (
        <p key={key} className={classNames("text-[14px] leading-6", textColor(props.color), props.bold && "font-medium")}>
          {props.value || props.text || props.content || ""}
        </p>
      );
    case "Markdown":
      return (
        <div key={key} className="chat-markdown text-[14px]">
          <MarkdownRenderer markDownContent={String(props.content || props.value || props.text || "")} />
        </div>
      );
    case "Divider":
      return (
        <div key={key} className="my-2 flex items-center gap-2">
          <div className="h-px flex-1 bg-[var(--chat-border)]/70" />
          {props.label ? <span className="text-[12px] text-[var(--chat-text-soft)]">{props.label}</span> : null}
          <div className="h-px flex-1 bg-[var(--chat-border)]/70" />
        </div>
      );
    case "Badge":
    case "Tag":
      return (
        <span
          key={key}
          className="inline-flex rounded-full border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] px-2 py-0.5 text-[12px] text-[var(--chat-text)]"
        >
          {props.value || props.label || props.text || ""}
        </span>
      );
    case "Stat":
      return (
        <div key={key} className="rounded-lg border border-[var(--chat-border)]/50 bg-[var(--chat-surface)] px-3 py-2">
          <div className="text-[12px] text-[var(--chat-text-soft)]">{props.label || ""}</div>
          <div className="text-[20px] font-semibold text-[var(--chat-text)]">{props.value || ""}</div>
          {props.delta ? (
            <div className="text-[12px] text-[var(--chat-text-soft)]">
              {props.trend === "up" ? "↑ " : props.trend === "down" ? "↓ " : ""}
              {props.delta}
            </div>
          ) : null}
        </div>
      );
    case "Progress": {
      const value = Math.min(100, Math.max(0, Number(props.value) || 0));
      return (
        <div key={key} className="space-y-1">
          {props.label ? <div className="text-[12px] text-[var(--chat-text-soft)]">{props.label}</div> : null}
          <div className="h-2 overflow-hidden rounded-full bg-[var(--chat-surface-muted)]">
            <div className="h-full rounded-full bg-[var(--chat-accent)]" style={{ width: `${value}%` }} />
          </div>
        </div>
      );
    }
    case "List":
      return props.ordered ? (
        <ol key={key} className="list-decimal space-y-1 pl-5 text-[14px] text-[var(--chat-text)]">
          {renderChildren()}
        </ol>
      ) : (
        <ul key={key} className="list-disc space-y-1 pl-5 text-[14px] text-[var(--chat-text)]">
          {renderChildren()}
        </ul>
      );
    case "ListItem":
      return <li key={key}>{props.value || props.text || renderChildren()}</li>;
    case "Table": {
      const headers: string[] = Array.isArray(props.headers) ? props.headers : [];
      return (
        <div key={key} className="max-h-[420px] overflow-auto rounded-lg border border-[var(--chat-border)]/60">
          <table className="min-w-full text-left text-[13px]">
            {headers.length ? (
              <thead className="sticky top-0 z-10 bg-[var(--chat-surface-muted)]/90 text-[var(--chat-text-soft)] backdrop-blur">
                <tr>
                  {headers.map((h) => (
                    <th key={h} className="px-3 py-2 font-medium">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
            ) : null}
            <tbody>
              {children.length ? (
                renderChildren()
              ) : (
                <tr>
                  <td
                    colSpan={Math.max(headers.length, 1)}
                    className="px-3 py-6 text-center text-[12px] text-[var(--chat-text-soft)]"
                  >
                    暂无表格数据
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      );
    }
    case "TableRow":
      return <tr key={key} className={classNames(props.highlight && "bg-[var(--chat-surface-soft)]")}>{renderChildren()}</tr>;
    case "TableCell":
      return (
        <td key={key} className={classNames("px-3 py-2 text-[var(--chat-text)]", props.bold && "font-semibold")}>
          {props.value || props.text || renderChildren()}
        </td>
      );
    case "Image":
      return props.src ? (
        <img
          key={key}
          src={String(props.src)}
          alt={String(props.alt || "")}
          className="max-h-64 max-w-full rounded-lg object-contain"
        />
      ) : null;
    case "Icon":
      return (
        <span key={key} className={classNames("inline-flex text-[16px]", textColor(props.color))}>
          {props.name || "•"}
        </span>
      );
    case "CodeBlock":
      return (
        <pre
          key={key}
          className="overflow-x-auto rounded-lg border border-[var(--chat-border)]/60 bg-[var(--chat-surface-muted)]/40 p-3 text-[12px] text-[var(--chat-text)]"
        >
          <code>{props.code || props.value || ""}</code>
        </pre>
      );
    case "Chart": {
      const categories: string[] = Array.isArray(props.categories) ? props.categories : [];
      const series: Array<{ name?: string; values?: number[] }> = Array.isArray(props.series)
        ? props.series
        : [];
      return (
        <GenUiChart
          key={key}
          title={props.title}
          chart={props.chart || "bar"}
          categories={categories}
          series={series}
          height={Number(props.height) || 280}
          stacked={Boolean(props.stacked)}
          showLegend={props.showLegend !== false}
          showGrid={props.showGrid !== false}
        />
      );
    }
    case "Button":
    case "LinkButton":
      return (
        <a
          key={key}
          href={props.href || props.url || undefined}
          className="inline-flex items-center rounded-md border border-[var(--chat-border)] bg-[var(--chat-surface)] px-3 py-1.5 text-[13px] font-medium text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]"
          target={props.href || props.url ? "_blank" : undefined}
          rel="noreferrer"
        >
          {props.label || props.value || props.text || "Button"}
        </a>
      );
    case "MetricCard":
    case "DataCard":
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] p-3 shadow-sm">
          <div className="flex items-start justify-between gap-2">
            <div>
              <div className="text-[12px] text-[var(--chat-text-soft)]">{props.title || props.label || ""}</div>
              <div className="mt-1 text-[22px] font-semibold tracking-tight text-[var(--chat-text)]">
                {props.value || ""}
              </div>
            </div>
            {props.icon ? <div className="text-lg">{props.icon}</div> : null}
          </div>
          {(props.delta || props.period || props.description) ? (
            <div className="mt-2 text-[12px] text-[var(--chat-text-soft)]">
              {props.trend === "up" ? "↑ " : props.trend === "down" ? "↓ " : ""}
              {props.delta || ""}
              {props.period ? ` · ${props.period}` : ""}
              {props.description ? ` ${props.description}` : ""}
            </div>
          ) : null}
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "WeatherCard":
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-gradient-to-br from-sky-50 to-[var(--chat-surface)] p-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-[13px] font-medium text-[var(--chat-text)]">{props.location || "Weather"}</div>
              <div className="mt-1 text-[28px] font-semibold text-[var(--chat-text)]">{props.temperature || ""}</div>
              <div className="text-[12px] text-[var(--chat-text-soft)]">{props.condition || ""}</div>
            </div>
            <div className="text-3xl">{props.icon || "☁"}</div>
          </div>
          {(props.humidity || props.wind || props.feelsLike) ? (
            <div className="mt-3 grid grid-cols-3 gap-2 text-[11px] text-[var(--chat-text-soft)]">
              {props.humidity ? <div>湿度 {props.humidity}</div> : null}
              {props.wind ? <div>风 {props.wind}</div> : null}
              {props.feelsLike ? <div>体感 {props.feelsLike}</div> : null}
            </div>
          ) : null}
          {Array.isArray(props.forecast) && props.forecast.length ? (
            <div className="mt-3 grid grid-cols-4 gap-2">
              {props.forecast.slice(0, 4).map((f: any, i: number) => (
                <div key={i} className="rounded-lg bg-white/70 px-2 py-1 text-center text-[11px]">
                  <div className="text-[var(--chat-text-soft)]">{f.day || ""}</div>
                  <div className="text-base">{f.icon || ""}</div>
                  <div className="text-[var(--chat-text)]">{f.high || ""}/{f.low || ""}</div>
                </div>
              ))}
            </div>
          ) : null}
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "ProfileCard":
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] p-3">
          <div className="flex items-center gap-3">
            {props.avatarUrl ? (
              <img src={String(props.avatarUrl)} alt="" className="size-12 rounded-full object-cover" />
            ) : (
              <div className="flex size-12 items-center justify-center rounded-full bg-[var(--chat-surface-muted)] text-sm font-semibold">
                {props.initials || String(props.name || "?").slice(0, 1)}
              </div>
            )}
            <div>
              <div className="text-[14px] font-semibold text-[var(--chat-text)]">{props.name || ""}</div>
              <div className="text-[12px] text-[var(--chat-text-soft)]">{props.role || ""}</div>
            </div>
          </div>
          {props.bio ? <div className="mt-2 text-[13px] text-[var(--chat-text-soft)]">{props.bio}</div> : null}
          {Array.isArray(props.stats) && props.stats.length ? (
            <div className="mt-3 grid grid-cols-3 gap-2">
              {props.stats.slice(0, 3).map((s: any, i: number) => (
                <div key={i} className="rounded-lg bg-[var(--chat-surface-soft)] px-2 py-1 text-center">
                  <div className="text-[14px] font-semibold">{s.value || ""}</div>
                  <div className="text-[11px] text-[var(--chat-text-soft)]">{s.label || ""}</div>
                </div>
              ))}
            </div>
          ) : null}
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "MediaCard":
      return (
        <div key={key} className="overflow-hidden rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)]">
          {props.imageUrl ? (
            <img src={String(props.imageUrl)} alt="" className="h-36 w-full object-cover" />
          ) : null}
          <div className="p-3">
            <div className="text-[14px] font-semibold text-[var(--chat-text)]">{props.title || ""}</div>
            {props.description ? (
              <div className="mt-1 text-[13px] text-[var(--chat-text-soft)]">{props.description}</div>
            ) : null}
            <div className="mt-2 space-y-2">{renderChildren()}</div>
          </div>
        </div>
      );
    case "QuoteCard":
      return (
        <div key={key} className="rounded-xl border-l-4 border-[var(--chat-accent)] bg-[var(--chat-surface-soft)]/50 p-3">
          <div className="text-[14px] italic text-[var(--chat-text)]">“{props.quote || props.value || ""}”</div>
          <div className="mt-2 text-[12px] text-[var(--chat-text-soft)]">
            — {props.author || ""}{props.role ? ` · ${props.role}` : ""}
          </div>
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "AlertCard":
    case "Callout":
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] p-3">
          {props.title ? <div className="text-[13px] font-semibold text-[var(--chat-text)]">{props.title}</div> : null}
          <div className="mt-1 text-[13px] text-[var(--chat-text-soft)]">{props.message || props.description || props.value || ""}</div>
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "TimelineCard": {
      const items = Array.isArray(props.items) ? props.items : [];
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] p-3">
          {props.title ? <div className="mb-2 text-[14px] font-semibold">{props.title}</div> : null}
          <div className="space-y-3">
            {items.map((item: any, i: number) => (
              <div key={i} className="flex gap-3">
                <div className="mt-1 size-2 shrink-0 rounded-full bg-[var(--chat-accent)]" />
                <div>
                  <div className="text-[11px] text-[var(--chat-text-soft)]">{item.time || ""}</div>
                  <div className="text-[13px] font-medium text-[var(--chat-text)]">{item.title || ""}</div>
                  {item.description ? <div className="text-[12px] text-[var(--chat-text-soft)]">{item.description}</div> : null}
                </div>
              </div>
            ))}
          </div>
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    }
    case "KpiBoard":
    case "FeatureGrid":
      return (
        <div key={key} className="space-y-2">
          {props.title ? <div className="text-[14px] font-semibold text-[var(--chat-text)]">{props.title}</div> : null}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{renderChildren()}</div>
        </div>
      );
    case "SlideDeck":
    case "Slide":
    case "Form":
    case "SectionHeader": {
      const title = props.title || props.name || props.quote || props.location || props.message || "";
      const body = props.value || props.description || props.bio || props.subtitle || props.condition || props.content || "";
      return (
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface-soft)]/40 p-3">
          {props.eyebrow ? <div className="text-[11px] uppercase tracking-wide text-[var(--chat-text-soft)]">{props.eyebrow}</div> : null}
          {title ? <div className="mb-1 text-[14px] font-semibold text-[var(--chat-text)]">{title}</div> : null}
          {body ? <div className="text-[13px] text-[var(--chat-text-soft)]">{body}</div> : null}
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    }
    case "KeyValueList": {
      const items = Array.isArray(props.items) ? props.items : [];
      return (
        <div key={key} className="space-y-1">
          {items.map((item: any, i: number) => (
            <div key={i} className="flex justify-between gap-3 text-[13px]">
              <span className="text-[var(--chat-text-soft)]">{item?.key || item?.label || ""}</span>
              <span className="text-[var(--chat-text)]">{item?.value || ""}</span>
            </div>
          ))}
          {renderChildren()}
        </div>
      );
    }
    case "Stepper": {
      const steps = Array.isArray(props.steps) ? props.steps : [];
      const active = Number(props.active) || 0;
      return (
        <ol key={key} className="space-y-1 text-[13px] text-[var(--chat-text)]">
          {steps.map((s: any, i: number) => (
            <li key={i} className={i === active ? "font-semibold" : "text-[var(--chat-text-soft)]"}>
              {i + 1}. {String(s)}
            </li>
          ))}
        </ol>
      );
    }
    case "Tabs": {
      const tabItems = children.filter((c) => c.kind === "TabItem");
      const items = tabItems.length ? tabItems : children;
      return <GenUiTabs key={key} items={items} depth={depth} />;
    }
    case "Accordion": {
      const accItems = children.filter((c) => c.kind === "AccordionItem");
      const items = accItems.length ? accItems : children;
      return <GenUiAccordion key={key} items={items} depth={depth} />;
    }
    case "ChipGroup":
      return (
        <div key={key} className="flex flex-wrap gap-1.5">
          {renderChildren()}
        </div>
      );
    case "ScrollArea":
      return (
        <div
          key={key}
          className="max-h-[360px] space-y-2 overflow-auto rounded-lg border border-[var(--chat-border)]/40 p-2"
        >
          {renderChildren()}
        </div>
      );
    case "TabItem":
    case "AccordionItem":
      return (
        <div key={key} className="space-y-2">
          {renderChildren()}
        </div>
      );
    case "HtmlFrame": {
      const rawHtml = typeof props.html === "string" ? props.html : "";
      const frameTitle =
        typeof props.title === "string" && props.title.trim() ? props.title.trim() : "Embedded HTML";
      const h = Number(props.height);
      const frameHeight = Number.isFinite(h) && h > 0 ? Math.min(2000, Math.max(120, Math.round(h))) : 320;
      const trimmed = rawHtml.trim();
      const srcDoc = !trimmed
        ? ""
        : /<html[\s>]/i.test(trimmed)
          ? trimmed
          : `<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/></head><body>${trimmed}</body></html>`;
      if (!srcDoc) {
        return (
          <div
            key={key}
            className="rounded-lg border border-dashed border-[var(--chat-border)] p-3 text-xs text-[var(--chat-text-soft)]"
          >
            HtmlFrame: empty html
          </div>
        );
      }
      return (
        <div
          key={key}
          className="overflow-hidden rounded-lg border border-[var(--chat-border)] bg-[var(--chat-surface)]"
        >
          <iframe
            title={frameTitle}
            srcDoc={srcDoc}
            sandbox="allow-scripts"
            className="w-full border-0"
            style={{ height: frameHeight }}
            referrerPolicy="no-referrer"
          />
        </div>
      );
    }
    case "ThreeJsFrame":
      return (
        <GenUiThreeJsFrame
          key={key}
          title={props.title}
          height={props.height}
          background={props.background}
          geometry={props.geometry}
          shape={props.shape}
          color={props.color}
          accentColor={props.accentColor}
          cameraZ={props.cameraZ}
          autoRotate={props.autoRotate}
          wireframe={props.wireframe}
          particles={props.particles}
          orbiters={props.orbiters}
          detail={props.detail}
          rotateSpeed={props.rotateSpeed}
          quality={props.quality}
          dpr={props.dpr}
          sceneScript={props.sceneScript}
        />
      );
    case "Model3D":
      return (
        <GenUiModel3D
          key={key}
          src={props.src}
          height={props.height}
          background={props.background}
          autoRotate={props.autoRotate}
          rotateSpeed={props.rotateSpeed}
          wireframe={props.wireframe}
          caption={props.caption}
        />
      );
    case "Video":
      return props.src ? (
        <video
          key={key}
          src={String(props.src)}
          controls
          poster={props.poster}
          className="w-full rounded-lg border border-[var(--chat-border)]/50"
        />
      ) : (
        <div
          key={key}
          className="rounded border border-dashed border-[var(--chat-border)]/70 px-2 py-2 text-[12px] text-[var(--chat-text-soft)]"
        >
          视频缺少地址
        </div>
      );
    case "ImageGallery": {
      const images: string[] = Array.isArray(props.images)
        ? props.images.map(String)
        : children
            .map((c) => c.props?.src)
            .filter(Boolean)
            .map(String);
      if (!images.length) {
        return (
          <div
            key={key}
            className="rounded border border-dashed border-[var(--chat-border)]/70 px-2 py-2 text-[12px] text-[var(--chat-text-soft)]"
          >
            图库为空
          </div>
        );
      }
      return (
        <div key={key} className="grid grid-cols-2 gap-2 sm:grid-cols-3">
          {images.map((src, i) => (
            <img
              key={`${src}-${i}`}
              src={src}
              alt={props.caption || `image-${i + 1}`}
              className="h-28 w-full rounded-lg object-cover"
            />
          ))}
        </div>
      );
    }
    case "Chip":
      return (
        <span
          key={key}
          className="inline-flex items-center rounded-full bg-[var(--chat-surface-soft)] px-2.5 py-0.5 text-[12px] text-[var(--chat-text)]"
        >
          {props.label || props.value || props.text || renderChildren()}
        </span>
      );
    case "Skeleton":
      return (
        <div
          key={key}
          className="h-4 animate-pulse rounded bg-[var(--chat-surface-soft)]"
          style={{ width: props.width || "100%" }}
        />
      );
    case "Avatar":
      return props.src ? (
        <img
          key={key}
          src={String(props.src)}
          alt={props.alt || props.name || "avatar"}
          className="h-10 w-10 rounded-full object-cover"
        />
      ) : (
        <div
          key={key}
          className="flex h-10 w-10 items-center justify-center rounded-full bg-[var(--chat-surface-soft)] text-[12px] font-medium text-[var(--chat-text-soft)]"
        >
          {String(props.name || props.label || "?").slice(0, 2)}
        </div>
      );
    case "Input":
    case "Textarea":
    case "Select":
    case "FileInput":
      return (
        <div key={key} className="space-y-1">
          {props.label ? (
            <div className="text-[12px] text-[var(--chat-text-soft)]">{props.label}</div>
          ) : null}
          <div className="rounded-md border border-[var(--chat-border)]/70 bg-white px-3 py-2 text-[13px] text-[var(--chat-text-soft)]">
            {props.placeholder || props.value || "（只读展示）"}
          </div>
        </div>
      );
    case "Switch":
    case "Slider":
    case "InteractiveButton":
    case "ToggleButton":
      return (
        <div
          key={key}
          className="inline-flex items-center rounded-md border border-[var(--chat-border)]/60 bg-[var(--chat-surface-soft)]/40 px-2.5 py-1 text-[12px] text-[var(--chat-text)]"
        >
          {props.label || props.title || props.name || node.kind}
          {props.value != null ? `: ${String(props.value)}` : ""}
        </div>
      );
    case "JsonDebug":
      return (
        <pre
          key={key}
          className="max-h-64 overflow-auto rounded-lg bg-[#0f172a] p-3 text-[11px] text-[#e2e8f0]"
        >
          {JSON.stringify(props.data ?? props.value ?? props, null, 2)}
        </pre>
      );
    case "LiveCamera":
    case "HostedCanvasFrame":
      return (
        <div
          key={key}
          className="rounded border border-dashed border-[var(--chat-border)]/70 px-2 py-2 text-[12px] text-[var(--chat-text-soft)]"
        >
          暂不支持交互组件：{node.kind}
          {props.title || props.label ? `（${props.title || props.label}）` : ""}
          {renderChildren()}
        </div>
      );
    case "Alert":
      return (
        <div
          key={key}
          className={classNames(
            "rounded-lg border px-3 py-2 text-[13px]",
            props.variant === "error"
              ? "border-red-200 bg-red-50 text-red-700"
              : props.variant === "success"
                ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                : props.variant === "warning"
                  ? "border-amber-200 bg-amber-50 text-amber-800"
                  : "border-[var(--chat-border)] bg-[var(--chat-surface-soft)] text-[var(--chat-text)]"
          )}
        >
          {props.message || props.description || props.value || props.text || ""}
          {renderChildren()}
        </div>
      );
    default:
      return (
        <div key={key} className="rounded border border-dashed border-[var(--chat-border)]/70 p-2 text-[12px] text-[var(--chat-text-soft)]">
          暂不支持的组件类型：{node.kind}
          {renderChildren()}
        </div>
      );
  }
});

GenUiNode.displayName = "GenUiNode";

export default GenUiNode;
