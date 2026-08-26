import { useCallback, useEffect, useState } from "react";
import {
  Blocks,
  FileArchive,
  Link2,
  Loader2,
  Plus,
  Pencil,
  Puzzle,
  RefreshCcw,
  Trash2,
  Upload,
} from "lucide-react";
import { Modal, Switch } from "antd";

import WorkspaceAdminHeader from "@/components/WorkspaceAdminHeader";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { mcpAdminApi, type McpRecord } from "@/services/mcpAdmin";
import {
  skillAdminApi,
  type SkillPackagePreview,
  type SkillRow,
} from "@/services/skillAdmin";
import { showMessage } from "@/utils";

type Props = { embedded?: boolean };

const CapabilityLibrary: ReactorType.FC<Props> = ({ embedded }) => {
  const [tab, setTab] = useState("skills");
  const [skills, setSkills] = useState<SkillRow[]>([]);
  const [mcps, setMcps] = useState<McpRecord[]>([]);
  const [loading, setLoading] = useState(false);

  // skill dialogs
  const [pasteOpen, setPasteOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [urlOpen, setUrlOpen] = useState(false);
  const [pasteName, setPasteName] = useState("");
  const [pasteDesc, setPasteDesc] = useState("");
  const [pasteContent, setPasteContent] = useState("");
  const [importUrl, setImportUrl] = useState("");
  const [zipFile, setZipFile] = useState<File | null>(null);
  const [zipPreview, setZipPreview] = useState<SkillPackagePreview | null>(
    null,
  );
  const [parsing, setParsing] = useState(false);
  const [saving, setSaving] = useState(false);

  // mcp form
  const [mcpOpen, setMcpOpen] = useState(false);
  const [mcpForm, setMcpForm] = useState<McpRecord>({
    mcpId: "",
    mcpName: "",
    transportType: "streamable_http",
    transportConfig: '{"baseUri":"http://127.0.0.1:8080/mcp"}',
    requestTimeout: 5,
    status: 1,
  });

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [s, m] = await Promise.all([
        skillAdminApi.list().catch(() => [] as SkillRow[]),
        mcpAdminApi.list().catch(() => [] as McpRecord[]),
      ]);
      setSkills(Array.isArray(s) ? s : []);
      setMcps(Array.isArray(m) ? m : []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const onParseZip = async (file: File) => {
    if (!file.name.toLowerCase().endsWith(".zip")) {
      showMessage()?.error("请上传 .zip 技能包");
      return;
    }
    setZipFile(file);
    setZipPreview(null);
    setParsing(true);
    try {
      setZipPreview(await skillAdminApi.parsePackage(file));
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "解析失败");
      setZipFile(null);
    } finally {
      setParsing(false);
    }
  };

  const onUploadZip = async () => {
    if (!zipFile || !zipPreview) return;
    setSaving(true);
    try {
      await skillAdminApi.upload(zipFile, !!zipPreview.nameTaken);
      showMessage()?.success(
        zipPreview.nameTaken ? "已替换同名技能" : "技能包已安装",
      );
      setUploadOpen(false);
      setZipFile(null);
      setZipPreview(null);
      await refresh();
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "上传失败");
    } finally {
      setSaving(false);
    }
  };

  const onPasteCreate = async () => {
    if (!pasteContent.trim()) {
      showMessage()?.error("请粘贴 SKILL.md 正文");
      return;
    }
    setSaving(true);
    try {
      await skillAdminApi.create({
        name: pasteName || undefined,
        description: pasteDesc || undefined,
        content: pasteContent,
        replace: false,
      });
      showMessage()?.success("技能已创建");
      setPasteOpen(false);
      setPasteName("");
      setPasteDesc("");
      setPasteContent("");
      await refresh();
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "创建失败");
    } finally {
      setSaving(false);
    }
  };

  const onImportUrl = async () => {
    if (!importUrl.trim()) {
      showMessage()?.error("请填写 URL");
      return;
    }
    setSaving(true);
    try {
      await skillAdminApi.importUrl(importUrl.trim(), false);
      showMessage()?.success("已从 URL 导入");
      setUrlOpen(false);
      setImportUrl("");
      await refresh();
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "导入失败");
    } finally {
      setSaving(false);
    }
  };

  const onDeleteSkill = (name: string) => {
    Modal.confirm({
      title: "删除技能",
      content: `确认删除「${name}」？将移除 skill 目录下的文件夹。`,
      okButtonProps: { danger: true },
      onOk: async () => {
        await skillAdminApi.remove(name);
        showMessage()?.success("已删除");
        await refresh();
      },
    });
  };

  const onSaveMcp = async () => {
    if (!mcpForm.mcpId.trim() || !mcpForm.mcpName.trim()) {
      showMessage()?.error("mcpId 与名称必填");
      return;
    }
    setSaving(true);
    try {
      const exists = mcps.some((m) => m.mcpId === mcpForm.mcpId);
      if (exists) {
        await mcpAdminApi.update(mcpForm);
      } else {
        await mcpAdminApi.create(mcpForm);
      }
      showMessage()?.success("MCP 已保存并热加载");
      setMcpOpen(false);
      await refresh();
    } catch (e) {
      showMessage()?.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const onDeleteMcp = (mcpId: string) => {
    Modal.confirm({
      title: "删除 MCP",
      content: `确认删除「${mcpId}」？`,
      okButtonProps: { danger: true },
      onOk: async () => {
        await mcpAdminApi.remove(mcpId);
        showMessage()?.success("已删除");
        await refresh();
      },
    });
  };

  return (
    <div className="workspace-admin-shell">
      <WorkspaceAdminHeader
        title="能力库"
        description="管理可被会话启用的技能包与 MCP 连接器，资源安装后即可加入能力选择。"
        icon={Blocks}
        embedded={embedded}
        actions={
          <Button
            type="button"
            variant="outline"
            className="workspace-admin-secondary"
            onClick={() => void refresh()}
            disabled={loading}
            aria-label="刷新能力库"
          >
            {loading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCcw className="h-4 w-4" />
            )}
            <span className="hidden sm:inline">刷新</span>
          </Button>
        }
      />

      <div className="workspace-admin-body">
        <div className="workspace-admin-body-inner">
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div
              className="workspace-admin-tabs"
              role="tablist"
              aria-label="能力类型"
            >
              <button
                type="button"
                role="tab"
                aria-selected={tab === "skills"}
                data-active={tab === "skills"}
                className="workspace-admin-tab"
                onClick={() => setTab("skills")}
              >
                <Puzzle className="h-3.5 w-3.5" />
                技能{" "}
                <span className="font-mono text-[11px]">{skills.length}</span>
              </button>
              <button
                type="button"
                role="tab"
                aria-selected={tab === "mcp"}
                data-active={tab === "mcp"}
                className="workspace-admin-tab"
                onClick={() => setTab("mcp")}
              >
                <Blocks className="h-3.5 w-3.5" />
                MCP <span className="font-mono text-[11px]">{mcps.length}</span>
              </button>
            </div>
            <span className="workspace-admin-section-meta">
              {tab === "skills" ? "技能注册表" : "MCP 连接注册表"}
            </span>
          </div>

          {tab === "skills" ? (
            <section className="workspace-admin-section">
              <div className="workspace-admin-section-head flex-wrap">
                <div>
                  <div className="workspace-admin-section-title">技能包</div>
                  <div className="workspace-admin-section-meta">
                    支持 zip、SKILL.md 粘贴和远程 URL 三种安装方式。
                  </div>
                </div>
                <div className="workspace-admin-toolbar">
                  <Button
                    type="button"
                    className="workspace-admin-primary"
                    onClick={() => setUploadOpen(true)}
                  >
                    <Upload className="h-4 w-4" />
                    上传 zip
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="workspace-admin-secondary"
                    onClick={() => setPasteOpen(true)}
                  >
                    <Plus className="h-4 w-4" />
                    粘贴新建
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() => setUrlOpen(true)}
                  >
                    <Link2 className="h-4 w-4" />
                    在线导入
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    onClick={() =>
                      void skillAdminApi.reload().then(() => {
                        showMessage()?.success("已重载技能注册表");
                        return refresh();
                      })
                    }
                  >
                    重载注册表
                  </Button>
                </div>
              </div>
              <div className="workspace-admin-resource-grid p-3">
                {skills.map((s) => (
                  <div key={s.name} className="workspace-admin-resource-row">
                    <div className="workspace-admin-resource-main">
                      <span
                        className="workspace-admin-resource-icon"
                        aria-hidden="true"
                      >
                        <Puzzle />
                      </span>
                      <div className="min-w-0">
                        <div className="workspace-admin-resource-title">
                          {s.name}
                        </div>
                        <div className="workspace-admin-resource-description">
                          {s.description || "无说明"}
                        </div>
                      </div>
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon-sm"
                      className="workspace-admin-danger"
                      onClick={() => onDeleteSkill(s.name)}
                      aria-label={`删除技能 ${s.name}`}
                      title="删除技能"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
                {!loading && skills.length === 0 ? (
                  <div className="workspace-admin-dashed-empty">
                    暂无技能。上传 zip（含 SKILL.md）或粘贴正文创建。
                  </div>
                ) : null}
              </div>
            </section>
          ) : (
            <section className="workspace-admin-section">
              <div className="workspace-admin-section-head flex-wrap">
                <div>
                  <div className="workspace-admin-section-title">
                    MCP 连接器
                  </div>
                  <div className="workspace-admin-section-meta">
                    连接外部工具服务，保存后热加载到运行时注册表。
                  </div>
                </div>
                <Button
                  type="button"
                  className="workspace-admin-primary"
                  onClick={() => {
                    setMcpForm({
                      mcpId: "",
                      mcpName: "",
                      transportType: "streamable_http",
                      transportConfig:
                        '{"baseUri":"http://127.0.0.1:8080/mcp"}',
                      requestTimeout: 5,
                      status: 1,
                    });
                    setMcpOpen(true);
                  }}
                >
                  <Plus className="h-4 w-4" />
                  添加 MCP
                </Button>
              </div>
              <div className="workspace-admin-resource-grid p-3">
                {mcps.map((m) => (
                  <div key={m.mcpId} className="workspace-admin-resource-row">
                    <div className="workspace-admin-resource-main">
                      <span
                        className="workspace-admin-resource-icon"
                        aria-hidden="true"
                      >
                        <Blocks />
                      </span>
                      <div className="min-w-0">
                        <div className="workspace-admin-resource-title">
                          {m.mcpName}
                        </div>
                        <div className="workspace-admin-resource-description">
                          <span className="workspace-admin-code">
                            {m.mcpId}
                          </span>
                          <span className="mx-1">·</span>
                          {m.transportType}
                        </div>
                        <div className="workspace-admin-resource-detail">
                          {m.transportConfig}
                        </div>
                        <div
                          className="workspace-admin-status mt-2"
                          data-disabled={(m.status ?? 1) !== 1}
                        >
                          <span
                            className="workspace-admin-status-dot"
                            aria-hidden="true"
                          />
                          {(m.status ?? 1) === 1 ? "已启用" : "已停用"}
                        </div>
                      </div>
                    </div>
                    <div className="workspace-admin-resource-actions">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => {
                          setMcpForm({ ...m });
                          setMcpOpen(true);
                        }}
                        aria-label={`编辑 MCP ${m.mcpId}`}
                        title="编辑 MCP"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon-sm"
                        className="workspace-admin-danger"
                        onClick={() => onDeleteMcp(m.mcpId)}
                        aria-label={`删除 MCP ${m.mcpId}`}
                        title="删除 MCP"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                ))}
                {!loading && mcps.length === 0 ? (
                  <div className="workspace-admin-dashed-empty">
                    暂无 MCP。添加 streamable_http、sse 或 stdio 连接器。
                  </div>
                ) : null}
              </div>
            </section>
          )}
        </div>
      </div>

      {/* zip upload */}
      <Dialog
        open={uploadOpen}
        onOpenChange={(o) => {
          setUploadOpen(o);
          if (!o) {
            setZipFile(null);
            setZipPreview(null);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>上传技能包</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <p className="text-[12.5px] text-slate-500">
              zip 内含 <code>SKILL.md</code>（可包在一层目录里），可带
              references/、scripts/。
            </p>
            <label className="flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 hover:border-sky-400">
              <FileArchive className="h-8 w-8 text-slate-400" />
              <span className="text-sm text-slate-600">
                {zipFile ? zipFile.name : "选择或拖入 .zip"}
              </span>
              <input
                type="file"
                accept=".zip"
                className="hidden"
                onChange={(e) => {
                  const f = e.target.files?.[0];
                  if (f) void onParseZip(f);
                }}
              />
            </label>
            {parsing ? (
              <div className="flex items-center gap-2 text-sm text-slate-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                解析中…
              </div>
            ) : null}
            {zipPreview ? (
              <div className="rounded-lg border border-slate-200 bg-white p-3 text-[12.5px]">
                <div>
                  <b>名称：</b>
                  {zipPreview.name}
                  {zipPreview.nameTaken ? (
                    <span className="ml-2 text-amber-600">（将覆盖同名）</span>
                  ) : null}
                </div>
                <div className="mt-1">
                  <b>说明：</b>
                  {zipPreview.description || "—"}
                </div>
                {zipPreview.extraFiles?.length ? (
                  <div className="mt-1 text-slate-500">
                    附带文件：{zipPreview.extraFiles.slice(0, 8).join(", ")}
                    {zipPreview.extraFiles.length > 8 ? "…" : ""}
                  </div>
                ) : null}
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setUploadOpen(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              className="workspace-admin-primary"
              disabled={!zipPreview || saving}
              onClick={() => void onUploadZip()}
            >
              {saving ? "安装中…" : "确认安装"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* paste */}
      <Dialog open={pasteOpen} onOpenChange={setPasteOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>粘贴新建技能</DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 py-2">
            <label className="text-[13px]">
              名称（可空，从 frontmatter 解析）
              <Input
                className="mt-1"
                value={pasteName}
                onChange={(e) => setPasteName(e.target.value)}
                placeholder="my-skill"
              />
            </label>
            <label className="text-[13px]">
              说明
              <Input
                className="mt-1"
                value={pasteDesc}
                onChange={(e) => setPasteDesc(e.target.value)}
              />
            </label>
            <label className="text-[13px]">
              SKILL.md 正文
              <Textarea
                className="mt-1 min-h-[200px] font-mono text-[12px]"
                value={pasteContent}
                onChange={(e) => setPasteContent(e.target.value)}
                placeholder={
                  "---\nname: demo\ndescription: …\n---\n\n# 手册正文"
                }
              />
            </label>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setPasteOpen(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              className="workspace-admin-primary"
              disabled={saving}
              onClick={() => void onPasteCreate()}
            >
              创建
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* url import */}
      <Dialog open={urlOpen} onOpenChange={setUrlOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>在线导入</DialogTitle>
          </DialogHeader>
          <p className="text-[12.5px] text-slate-500">
            支持 zip 直链，或 raw SKILL.md 文本 URL。
          </p>
          <Input
            value={importUrl}
            onChange={(e) => setImportUrl(e.target.value)}
            placeholder="https://…/skill.zip 或 …/SKILL.md"
          />
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setUrlOpen(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              className="workspace-admin-primary"
              disabled={saving}
              onClick={() => void onImportUrl()}
            >
              导入
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* mcp form */}
      <Dialog open={mcpOpen} onOpenChange={setMcpOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {mcps.some((m) => m.mcpId === mcpForm.mcpId)
                ? "编辑 MCP"
                : "添加 MCP"}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-3 py-2">
            <label className="text-[13px]">
              mcpId
              <Input
                className="mt-1 font-mono"
                value={mcpForm.mcpId}
                disabled={mcps.some((m) => m.mcpId === mcpForm.mcpId && !!m.id)}
                onChange={(e) =>
                  setMcpForm((f) => ({
                    ...f,
                    mcpId: e.target.value
                  }))
                }
              />
            </label>
            <label className="text-[13px]">
              名称
              <Input
                className="mt-1"
                value={mcpForm.mcpName}
                onChange={(e) =>
                  setMcpForm((f) => ({
                    ...f,
                    mcpName: e.target.value
                  }))
                }
              />
            </label>
            <label className="text-[13px]">
              传输类型
              <select
                className="mt-1 flex h-9 w-full rounded-md border border-slate-200 px-3 text-[13px]"
                value={mcpForm.transportType}
                onChange={(e) =>
                  setMcpForm((f) => ({
                    ...f,
                    transportType: e.target.value
                  }))
                }
              >
                <option value="streamable_http">streamable_http</option>
                <option value="sse">sse</option>
                <option value="stdio">stdio</option>
              </select>
            </label>
            <label className="text-[13px]">
              transportConfig（JSON）
              <Textarea
                className="mt-1 min-h-[100px] font-mono text-[12px]"
                value={mcpForm.transportConfig || ""}
                onChange={(e) =>
                  setMcpForm((f) => ({
                    ...f,
                    transportConfig: e.target.value,
                  }))
                }
              />
            </label>
            <div className="flex items-center gap-2">
              <span className="text-[13px]">启用</span>
              <Switch
                checked={(mcpForm.status ?? 1) === 1}
                onChange={(c) =>
                  setMcpForm((f) => ({
                    ...f,
                    status: c ? 1 : 0
                  }))
                }
              />
            </div>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setMcpOpen(false)}
            >
              取消
            </Button>
            <Button
              type="button"
              className="workspace-admin-primary"
              disabled={saving}
              onClick={() => void onSaveMcp()}
            >
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default CapabilityLibrary;
