export type SopStatus = "online" | "offline" | "draft";

export type SopStep = {
  title: string;
  steps: string[];
};

export type SopItem = {
  sopId: string;
  sopName: string;
  sopDesc: string;
  sopType: string;
  sopSteps: SopStep[];
  status: SopStatus;
  sopString?: string;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type SopRecallHit = {
  sopId: string;
  sopName: string;
  score?: number | null;
  status?: string | null;
};

export type SopRecallTestResult = {
  sopMode: string;
  choosedSopString: string;
  hits: SopRecallHit[];
};

export type SopEditorDraft = {
  sopId: string | null;
  sopName: string;
  sopDesc: string;
  sopType: string;
  sopSteps: SopStep[];
  status: SopStatus;
};
