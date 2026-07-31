import { describe, expect, it } from "vitest";
import {
  mergeUiPatchIntoTaskGroup,
  mergeUiPatchIntoTasks,
  getGenUiTreeFromTask,
} from "./genuiState";
import { processTaskForRender } from "./renderTasks";

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

  it("merges ui_patch across task groups", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      messageTime: "1",
      resultMap: {
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
        patches: [{ op: "replace", path: "/root/props/title", value: "Cross" }],
      },
    };
    const ok = mergeUiPatchIntoTasks([[treeTask], []], patchTask);
    expect(ok).toBe(true);
    expect(getGenUiTreeFromTask(treeTask).root.props.title).toBe("Cross");
  });

  it("invalidates render cache after ui_patch merge", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      messageTime: "1",
      messageId: "tree-1",
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
    const before = processTaskForRender(treeTask, "base");
    expect(getGenUiTreeFromTask(before[0]).root.props.title).toBe("Old");

    mergeUiPatchIntoTaskGroup(
      [treeTask],
      {
        messageType: "ui_patch",
        messageTime: "2",
        resultMap: {
          patches: [{ op: "replace", path: "/root/props/title", value: "Patched" }],
        },
      } as any
    );

    const after = processTaskForRender(treeTask, "base");
    expect(getGenUiTreeFromTask(after[0]).root.props.title).toBe("Patched");
  });
});
