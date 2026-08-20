export type AskOption = {
  label: string;
  description: string;
};

export type AskQuestion = {
  question: string;
  header: string;
  options: AskOption[];
  multiSelect: boolean;
};

export type AskOutput = {
  recognized: boolean;
  answers: Record<string, string | true>;
  note: string;
};

export type ResolvedAskAnswer = {
  selected: Set<number>;
  otherText: string;
  indeterminate: boolean;
};

export function parseAskInput(arg: string): AskQuestion[] {
  if (!arg) return [];
  try {
    const obj = JSON.parse(arg) as Record<string, unknown>;
    return normalizeAskQuestions(obj.questions);
  } catch {
    return [];
  }
}

export function normalizeAskQuestions(raw: unknown): AskQuestion[] {
  const parsed =
    typeof raw === "string" && raw.trim()
      ? (() => {
          try {
            return JSON.parse(raw);
          } catch {
            return raw;
          }
        })()
      : raw;
  if (!Array.isArray(parsed)) return [];
  const out: AskQuestion[] = [];
  for (const q of parsed) {
    if (!q || typeof q !== "object") continue;
    const qr = q as Record<string, unknown>;
    const opts: AskOption[] = Array.isArray(qr.options)
      ? (qr.options as unknown[]).map((o) => {
          const or = (o && typeof o === "object" ? o : {}) as Record<
            string,
            unknown
          >;
          return {
            label: typeof or.label === "string" ? or.label : String(or.value || ""),
            description:
              typeof or.description === "string" ? or.description : "",
          };
        })
      : [];
    const question =
      typeof qr.question === "string"
        ? qr.question
        : String(qr.text || qr.prompt || "");
    if (!question) continue;
    out.push({
      question,
      header: typeof qr.header === "string" ? qr.header : "",
      options: opts,
      multiSelect: qr.multi_select === true || qr.multiSelect === true,
    });
  }
  return out;
}

const EMPTY: AskOutput = { recognized: false, answers: {}, note: "" };

export function parseAskOutput(output: string[] | undefined): AskOutput {
  const line = output?.[0];
  if (!line) return EMPTY;
  let obj: unknown;
  try {
    obj = JSON.parse(line);
  } catch {
    return EMPTY;
  }
  if (!obj || typeof obj !== "object" || Array.isArray(obj)) return EMPTY;
  return parseAskAnswersRecord(obj as Record<string, unknown>);
}

export function parseAskAnswersRecord(
  obj: Record<string, unknown> | null | undefined
): AskOutput {
  if (!obj) return EMPTY;
  const raw = obj.answers;
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) return EMPTY;
  const answers: Record<string, string | true> = {};
  for (const [k, v] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof v === "string") answers[k] = v;
    else if (v === true) answers[k] = true;
  }
  return {
    recognized: true,
    answers,
    note: typeof obj.note === "string" ? obj.note : "",
  };
}

export function answerFor(
  answers: Record<string, string | true>,
  questionText: string,
  index: number
): string | true | undefined {
  return answers[questionText] ?? answers[`q_${index}`];
}

const OPT_ID = /^opt_\d+_(\d+)$/;

export function resolveAnswer(
  value: string | true | undefined,
  options: readonly AskOption[] = []
): ResolvedAskAnswer {
  if (value === undefined) {
    return { selected: new Set(), otherText: "", indeterminate: false };
  }
  if (value === true) {
    return { selected: new Set(), otherText: "", indeterminate: true };
  }

  const indexByLabel = new Map<string, number>();
  options.forEach((o, i) => {
    if (o.label.length > 0 && !indexByLabel.has(o.label)) {
      indexByLabel.set(o.label, i);
    }
  });

  const whole = indexByLabel.get(value);
  if (whole !== undefined) {
    return { selected: new Set([whole]), otherText: "", indeterminate: false };
  }

  const selected = new Set<number>();
  const others: string[] = [];
  for (const rawSeg of value.split(",")) {
    const seg = rawSeg.trim();
    const byLabel = indexByLabel.get(seg);
    if (byLabel !== undefined) {
      selected.add(byLabel);
      continue;
    }
    const m = OPT_ID.exec(seg);
    if (m) selected.add(Number(m[1]));
    else if (seg.length > 0) others.push(seg);
  }
  return {
    selected,
    otherText: others.join(", "),
    indeterminate: false,
  };
}
