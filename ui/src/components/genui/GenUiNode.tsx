import { FC, memo, type ReactNode } from "react";
import classNames from "classnames";
import { motion, useReducedMotion } from "motion/react";
import { DURATION, EASE_OUT } from "@/lib/motion";
import GenUiChart from "./GenUiChart";
import GenUiModel3D from "./GenUiModel3D";
import GenUiThreeJsFrame from "./GenUiThreeJsFrame";
import ParametricLab from "./ParametricLab";
import ConceptDemo from "./ConceptDemo";
import { GenUiBindScope, useGenUiBind } from "./GenUiBind";
import { resolveBoundProps } from "./bindProps";
import { GenUiQuiz, GenUiWorkedExample } from "./GenUiQuiz";
import { BeforeAfter } from "./BeforeAfter";
import { NumberLine } from "./NumberLine";
import { CoordinateGrid } from "./CoordinateGrid";
import {
  GenUiActionButton,
  GenUiFormField,
  GenUiFormShell,
} from "./GenUiFormFields";

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
      return "text-[var(--chat-text-soft)]";
    case "error":
      return "text-red-600";
    default:
      return "text-[var(--chat-text)]";
  }
};

const GenUiEnter: FC<{ index: number; children: ReactNode }> = ({
  index,
  children,
}) => {
  const reduce = useReducedMotion();
  if (reduce) return <>{children}</>;
  return (
    <motion.div
      initial={{ opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: DURATION.message,
        ease: EASE_OUT,
        delay: Math.min(index * 0.04, 0.28),
      }}
    >
      {children}
    </motion.div>
  );
};

const GenUiNode: FC<Props> = memo(({ node, depth = 0 }) => {
  if (!node || !node.kind) return null;
  const bind = useGenUiBind();
  const rawProps = node.props || {};
  const props = bind ? resolveBoundProps(rawProps, bind.values) : rawProps;
  const children = Array.isArray(node.children) ? node.children : [];
  const key = node.nodeId || `${node.kind}-${depth}`;

  // 组件按 kind 做纯渲染分派，所有子节点都通过 renderChildren 递归进入同一安全边界；
  // 未知 kind 的处理位于 switch 默认分支，保证新增节点至少能展示文本和子树。
  // depth < 2 时做入场 stagger，深层树不再包 motion 以免性能抖动。
  const renderChildren = (animate = false) =>
    children.map((child, index) => {
      const childNode = (
        <GenUiNode
          key={child.nodeId || `${key}-${index}`}
          node={child}
          depth={depth + 1}
        />
      );
      if (!animate || depth >= 2) return childNode;
      return (
        <GenUiEnter key={child.nodeId || `${key}-enter-${index}`} index={index}>
          {childNode}
        </GenUiEnter>
      );
    });

  switch (node.kind) {
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
            <div
              className="h-full rounded-full bg-[var(--chat-accent)] transition-[width] duration-500 ease-[var(--ease-out)]"
              style={{ width: `${value}%` }}
            />
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
        <div key={key} className="max-h-[min(60vh,640px)] overflow-auto rounded-lg border border-[var(--chat-border)]/60">
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
          className="max-h-[min(60vh,560px)] w-full rounded-lg object-contain"
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
    case "ParametricLab":
    case "PythagorasLab":
    case "GeometryLab":
    case "InteractiveLab":
      return (
        <ParametricLab
          key={key}
          title={props.title}
          description={props.description || props.subtitle}
          scene={
            node.kind === "PythagorasLab"
              ? props.scene || "right_triangle"
              : props.scene || props.preset
          }
          preset={
            node.kind === "PythagorasLab"
              ? "right_triangle"
              : props.preset || props.scene
          }
          params={props.params}
          outputs={props.outputs || props.formulas}
          svg={props.svg || props.customSvg}
          height={Number(props.height) || undefined}
          showFormulas={props.showFormulas !== false && props.showFormula !== false}
          formulaNote={props.formulaNote || props.formula || props.equation}
          accent={props.accent || props.color}
        />
      );
    case "ConceptDemo":
    case "AnimStepLab":
    case "KnowledgeDemo":
      return (
        <ConceptDemo
          key={key}
          title={props.title}
          description={props.description || props.subtitle}
          scene={props.scene || props.preset || props.mode}
          steps={props.steps}
          nodes={props.nodes}
          edges={props.edges}
          formulas={props.formulas || props.tokens}
          left={props.left}
          right={props.right}
          leftTitle={props.leftTitle}
          rightTitle={props.rightTitle}
          height={Number(props.height) || undefined}
          autoPlay={props.autoPlay !== false && props.autoplay !== false}
          loop={props.loop !== false}
          stepDuration={Number(props.stepDuration) || Number(props.duration) || undefined}
        />
      );
    case "BindScope":
    case "ReactiveScope":
      return (
        <GenUiBindScope
          key={key}
          params={props.params || props.state}
          outputs={props.outputs}
          showControls={props.showControls !== false}
        >
          {renderChildren()}
        </GenUiBindScope>
      );
    case "Quiz":
      return (
        <GenUiQuiz
          key={key}
          title={props.title}
          prompt={props.prompt || props.question}
          options={props.options}
          answer={props.answer || props.correct}
          explanation={props.explanation || props.hint}
          multi={Boolean(props.multi)}
        />
      );
    case "WorkedExample":
      return (
        <GenUiWorkedExample
          key={key}
          title={props.title}
          problem={props.problem || props.prompt}
          steps={props.steps}
          answer={props.answer}
        />
      );
    case "BeforeAfter":
    case "CompareSlider":
      return (
        <BeforeAfter
          key={key}
          before={props.before || props.left}
          after={props.after || props.right}
          beforeLabel={props.beforeLabel || props.leftLabel}
          afterLabel={props.afterLabel || props.rightLabel}
          height={Number(props.height) || undefined}
        />
      );
    case "NumberLine":
      return (
        <NumberLine
          key={key}
          min={props.min}
          max={props.max}
          value={props.value}
          points={props.points}
          title={props.title}
          height={Number(props.height) || undefined}
        />
      );
    case "CoordinateGrid":
      return (
        <CoordinateGrid
          key={key}
          xmin={props.xmin ?? props.minX}
          xmax={props.xmax ?? props.maxX}
          ymin={props.ymin ?? props.minY}
          ymax={props.ymax ?? props.maxY}
          points={props.points}
          vectors={props.vectors}
          fn={props.fn || props.expr}
          title={props.title}
          height={Number(props.height) || undefined}
        />
      );
    case "Button":
    case "LinkButton":
    case "InteractiveButton":
      return <GenUiActionButton key={key} props={props} />;
    case "ToggleButton":
      return <GenUiActionButton key={key} props={props} toggle />;
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
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface)] p-3">
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
                <div key={i} className="rounded-lg bg-[var(--chat-surface-soft)] px-2 py-1 text-center text-[11px]">
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
        <div key={key} className="rounded-xl border border-[var(--chat-border)]/60 bg-[var(--chat-surface-soft)]/50 p-3">
          <div className="text-[14px] italic leading-relaxed text-[var(--chat-text)]">
            “{props.quote || props.value || ""}”
          </div>
          <div className="mt-2 text-[12px] text-[var(--chat-text-soft)]">
            — {props.author || ""}{props.role ? ` · ${props.role}` : ""}
          </div>
          <div className="mt-2 space-y-2">{renderChildren()}</div>
        </div>
      );
    case "AlertCard":
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
    case "Form":
      return (
        <GenUiFormShell key={key} node={node}>
          {renderChildren()}
        </GenUiFormShell>
      );
    case "SlideDeck":
    case "Slide":
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
    case "ChipGroup":
      return (
        <div key={key} className="flex flex-wrap gap-1.5">
          {renderChildren()}
        </div>
      );
    case "HtmlFrame": {
      const rawHtml = typeof props.html === "string" ? props.html : "";
      const frameTitle =
        typeof props.title === "string" && props.title.trim() ? props.title.trim() : "Embedded HTML";
      const h = Number(props.height);
      const frameHeight = Number.isFinite(h) && h > 0 ? Math.min(2000, Math.max(120, Math.round(h))) : undefined;
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
            className="w-full border-0 min-h-[280px] max-h-[min(64vh,720px)] aspect-[16/10]"
            style={frameHeight ? { height: frameHeight, maxHeight: "none", aspectRatio: "auto" } : undefined}
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
              className="aspect-[4/3] w-full rounded-lg object-cover"
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
    case "NumberInput":
    case "Switch":
    case "Slider":
      return <GenUiFormField key={key} kind={node.kind} props={props} />;
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
