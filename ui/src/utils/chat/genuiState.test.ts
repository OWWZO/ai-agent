import { describe, expect, it } from "vitest";
import { mergeUiPatchIntoTaskGroup, getGenUiTreeFromTask } from "./genuiState";

describe("genuiState merge", () => {
  it("merges ui_patch into latest ui_tree", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      messageTime: "1",
      resultMap: {
        messageType: "ui_tree",
        isFinal: true,
        tree: {
          schemaVersion: "1",
          root: {
            nodeId: "r1",
            kind: "Card",
            props: { title: "Old" },
            children: [],
          },
        },
      },
    };
    const patchTask: any = {
      messageType: "ui_patch",
      messageTime: "2",
      resultMap: {
        messageType: "ui_patch",
        isFinal: true,
        patches: [
          { op: "replace", path: "/root/props/title", value: "New" },
        ],
      },
    };
    const group = [treeTask];
    const ok = mergeUiPatchIntoTaskGroup(group, patchTask);
    expect(ok).toBe(true);
    const tree = getGenUiTreeFromTask(treeTask);
    expect(tree.root.props.title).toBe("New");
    expect(patchTask.resultMap.mergedIntoTree).toBe(true);
    expect(Array.isArray(treeTask.resultMap.appliedPatches)).toBe(true);
  });
});
