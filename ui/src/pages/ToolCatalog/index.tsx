import { useState } from "react";
import { Wrench } from "lucide-react";

import WorkspaceAdminHeader from "@/components/WorkspaceAdminHeader";

import ToolCatalogList from "./ToolCatalogList";
import ToolDetail from "./ToolDetail";
import ToolTaskDialog from "./ToolTaskDialog";
import { TOOL_CATALOG, type ToolCatalogItem, type ToolFunction, type ToolTaskDraft } from "./toolCatalog";
import { filterToolCatalog, type ToolCatalogFilterCategory } from "./toolCatalogModel";

export type ToolCatalogProps = {
  embedded?: boolean;
  onStartToolTask: (draft: ToolTaskDraft) => void;
};

export default function ToolCatalog({ embedded, onStartToolTask }: ToolCatalogProps) {
  const [category, setCategory] = useState<ToolCatalogFilterCategory>("all");
  const [query, setQuery] = useState("");
  const [selectedTool, setSelectedTool] = useState<ToolCatalogItem | null>(null);
  const [selectedFunction, setSelectedFunction] = useState<ToolFunction | null>(null);
  const filteredItems = filterToolCatalog(TOOL_CATALOG, category, query);

  return (
    <div className="workspace-admin-shell">
      <WorkspaceAdminHeader
        title="工具目录"
        description="浏览可用工具及其任务参数。"
        icon={Wrench}
        embedded={embedded}
      />
      <main className="workspace-admin-body">
        <div className="workspace-admin-body-inner">
          {selectedFunction && selectedTool ? (
            <ToolTaskDialog
              tool={selectedTool}
              toolFunction={selectedFunction}
              onClose={() => setSelectedFunction(null)}
              onStartToolTask={onStartToolTask}
              onSubmitted={() => setSelectedFunction(null)}
            />
          ) : selectedTool ? (
            <ToolDetail
              tool={selectedTool}
              onBack={() => setSelectedTool(null)}
              onSelectFunction={setSelectedFunction}
            />
          ) : (
            <ToolCatalogList
              items={filteredItems}
              category={category}
              query={query}
              onCategoryChange={setCategory}
              onQueryChange={setQuery}
              onSelectTool={setSelectedTool}
            />
          )}
        </div>
      </main>
    </div>
  );
}
