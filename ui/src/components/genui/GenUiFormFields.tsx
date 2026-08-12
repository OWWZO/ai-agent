import { FC, memo, type ReactNode } from "react";
import classNames from "classnames";
import {
  GenUiFormProvider,
  formExtrasAtClick,
  useGenUiFormField,
  useGenUiFormScope,
} from "./genUiForms";
import { fireGenUiControl } from "./genUiActionBus";
import { useGenUiRenderContext } from "./GenUiRenderContext";
import type { GenUiNodeData } from "./GenUiNode";

const FIELD_CLASS =
  "w-full rounded-md border border-[var(--chat-border)]/70 bg-white px-3 py-2 text-[13px] text-[var(--chat-text)] focus:outline-none focus:ring-1 focus:ring-[var(--chat-accent)]";

const s = (v: unknown): string =>
  typeof v === "string" ? v : v != null ? String(v) : "";

function FieldShell({
  label,
  required,
  description,
  children,
}: {
  label?: unknown;
  required?: unknown;
  description?: unknown;
  children: ReactNode;
}) {
  return (
    <div className="space-y-1">
      {label ? (
        <label className="text-[12px] font-medium text-[var(--chat-text-soft)]">
          {s(label)}
          {required ? <span className="ml-0.5 text-red-500">*</span> : null}
        </label>
      ) : null}
      {children}
      {description ? (
        <p className="text-[11px] text-[var(--chat-text-soft)]">{s(description)}</p>
      ) : null}
    </div>
  );
}

export const GenUiFormShell: FC<{
  node: GenUiNodeData;
  children: ReactNode;
}> = memo(({ node, children }) => {
  const ctx = useGenUiRenderContext();
  const props = node.props || {};
  const formId = s(props.formId) || node.nodeId || "form";
  const formKey = `${ctx.sessionId ?? "scope"}::${ctx.messageId ?? "root"}::${formId}`;
  const title = props.title || props.name || "";
  const body = props.value || props.description || props.subtitle || "";
  const submitLabel = props.submitLabel;

  return (
    <GenUiFormProvider formId={formId} formKey={formKey}>
      <div className="rounded-xl border border-[var(--chat-border)]/70 bg-[var(--chat-surface-soft)]/40 p-3">
        {props.eyebrow ? (
          <div className="text-[11px] uppercase tracking-wide text-[var(--chat-text-soft)]">
            {s(props.eyebrow)}
          </div>
        ) : null}
        {title ? (
          <div className="mb-1 text-[14px] font-semibold text-[var(--chat-text)]">
            {s(title)}
          </div>
        ) : null}
        {body ? (
          <div className="mb-2 text-[13px] text-[var(--chat-text-soft)]">{s(body)}</div>
        ) : null}
        <div className="mt-2 space-y-2">{children}</div>
        {submitLabel ? (
          <GenUiActionButton
            props={{
              label: submitLabel,
              variant: "primary",
              action: { type: "submit_form", payload: { formId } },
            }}
            className="mt-3"
          />
        ) : null}
      </div>
    </GenUiFormProvider>
  );
});

GenUiFormShell.displayName = "GenUiFormShell";

export const GenUiActionButton: FC<{
  props: Record<string, any>;
  className?: string;
  toggle?: boolean;
}> = memo(({ props, className, toggle }) => {
  const ctx = useGenUiRenderContext();
  const scope = useGenUiFormScope();
  const label = props.label || props.value || props.text || "Button";
  const primary = props.variant === "primary";
  const disabled = Boolean(props.disabled);
  // 仅 object action 可点；裸 actionId 不再当聊天发送
  const hasAction = props.action && typeof props.action === "object";
  const href = props.href || props.url;

  if (!hasAction && href) {
    return (
      <a
        href={String(href)}
        className={classNames(
          "inline-flex items-center rounded-md border px-3 py-1.5 text-[13px] font-medium transition-colors",
          primary
            ? "border-[var(--chat-accent)] bg-[var(--chat-accent)] text-white hover:opacity-90"
            : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]",
          className
        )}
        target="_blank"
        rel="noreferrer"
      >
        {String(label)}
      </a>
    );
  }

  return (
    <button
      type="button"
      disabled={disabled || !hasAction}
      title={
        props.tooltip
          ? String(props.tooltip)
          : !hasAction
            ? "未配置 UI 动作"
            : undefined
      }
      className={classNames(
        "inline-flex items-center rounded-md border px-3 py-1.5 text-[13px] font-medium transition-colors",
        primary
          ? "border-[var(--chat-accent)] bg-[var(--chat-accent)] text-white hover:opacity-90"
          : "border-[var(--chat-border)] bg-[var(--chat-surface)] text-[var(--chat-text)] hover:bg-[var(--chat-surface-muted)]",
        (disabled || !hasAction) && "cursor-not-allowed opacity-50",
        className
      )}
      onClick={() => {
        if (disabled || !hasAction) return;
        fireGenUiControl(props, ctx, {
          ...formExtrasAtClick(scope),
          ...(toggle ? { toggled: !Boolean(props.pressed ?? props.active) } : {}),
        });
      }}
    >
      {String(label)}
    </button>
  );
});

GenUiActionButton.displayName = "GenUiActionButton";

export const GenUiFormField: FC<{
  kind: string;
  props: Record<string, any>;
}> = memo(({ kind, props }) => {
  const name = s(props.name);
  const initial =
    props.value !== undefined
      ? props.value
      : props.checked !== undefined
        ? props.checked
        : undefined;
  const { interactive, value, onChange } = useGenUiFormField(name, initial);

  if (!interactive) {
    return (
      <div className="space-y-1">
        {props.label ? (
          <div className="text-[12px] text-[var(--chat-text-soft)]">{s(props.label)}</div>
        ) : null}
        <div className="rounded-md border border-[var(--chat-border)]/70 bg-white px-3 py-2 text-[13px] text-[var(--chat-text-soft)]">
          {s(props.placeholder || props.value) || "（只读展示）"}
        </div>
      </div>
    );
  }

  switch (kind) {
    case "Textarea":
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <textarea
            className={classNames(FIELD_CLASS, "resize-y")}
            rows={typeof props.rows === "number" ? props.rows : 3}
            placeholder={s(props.placeholder)}
            value={s(value)}
            onChange={(e) => onChange(e.target.value)}
          />
        </FieldShell>
      );
    case "NumberInput":
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <input
            type="number"
            className={FIELD_CLASS}
            value={
              value === undefined || value === null || value === ""
                ? ""
                : Number(value)
            }
            min={typeof props.min === "number" ? props.min : undefined}
            max={typeof props.max === "number" ? props.max : undefined}
            step={
              typeof props.step === "number"
                ? props.step
                : props.integer
                  ? 1
                  : "any"
            }
            onChange={(e) => {
              const raw = e.target.value;
              if (raw === "") return onChange(undefined);
              onChange(props.integer ? parseInt(raw, 10) : parseFloat(raw));
            }}
          />
        </FieldShell>
      );
    case "Switch":
      return (
        <div className="flex items-center justify-between gap-3">
          <div className="min-w-0">
            {props.label ? (
              <span className="text-[12px] font-medium text-[var(--chat-text)]">
                {s(props.label)}
              </span>
            ) : null}
            {props.description ? (
              <p className="text-[11px] text-[var(--chat-text-soft)]">
                {s(props.description)}
              </p>
            ) : null}
          </div>
          <button
            type="button"
            role="switch"
            aria-checked={Boolean(value)}
            onClick={() => onChange(!value)}
            className={classNames(
              "relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors",
              value ? "bg-[var(--chat-accent)]" : "bg-[var(--chat-border)]"
            )}
          >
            <span
              className={classNames(
                "inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform",
                value ? "translate-x-[18px]" : "translate-x-0.5"
              )}
            />
          </button>
        </div>
      );
    case "Slider": {
      const min = typeof props.min === "number" ? props.min : 0;
      const max = typeof props.max === "number" ? props.max : 100;
      const num = typeof value === "number" ? value : min;
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <div className="flex items-center gap-2">
            <input
              type="range"
              className="h-1.5 flex-1 cursor-pointer accent-[var(--chat-accent)]"
              min={min}
              max={max}
              step={typeof props.step === "number" ? props.step : 1}
              value={num}
              onChange={(e) => onChange(parseFloat(e.target.value))}
            />
            <span className="w-10 shrink-0 text-right text-[12px] tabular-nums text-[var(--chat-text-soft)]">
              {num}
            </span>
          </div>
        </FieldShell>
      );
    }
    case "FileInput":
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <input
            type="text"
            className={FIELD_CLASS}
            placeholder={
              props.accept
                ? `file id or path (${s(props.accept)})`
                : "file id or path"
            }
            value={s(value)}
            onChange={(e) => onChange(e.target.value)}
          />
        </FieldShell>
      );
    case "Select":
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <select
            className={classNames(FIELD_CLASS, "appearance-none")}
            value={s(value)}
            onChange={(e) => onChange(e.target.value)}
          >
            {!s(value) ? <option value="" /> : null}
            {((props.options as unknown[]) || []).map((opt, i) => (
              <option key={i} value={s(opt)}>
                {s(opt)}
              </option>
            ))}
          </select>
        </FieldShell>
      );
    case "Input":
    default:
      return (
        <FieldShell
          label={props.label}
          required={props.required}
          description={props.description}
        >
          <input
            type={(props.type as string) || "text"}
            className={FIELD_CLASS}
            placeholder={s(props.placeholder)}
            value={s(value)}
            onChange={(e) => onChange(e.target.value)}
          />
        </FieldShell>
      );
  }
});

GenUiFormField.displayName = "GenUiFormField";
