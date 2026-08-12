import { describe, expect, it } from "vitest";
import {
  mergeUiPatchIntoTaskGroup,
  mergeUiPatchIntoTasks,
  getGenUiTreeFromTask,
  findFeaturedGenUi,
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
  });

  it("merges ui_patch across task groups", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      resultMap: {
        tree: {
          schemaVersion: "1",
          root: { kind: "Card", props: { title: "A" }, children: [] },
        },
      },
    };
    const patchTask: any = {
      messageType: "ui_patch",
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

  it("finds latest featured GenUI across task groups", () => {
    const older: any = {
      messageType: "ui_tree",
      resultMap: {
        tree: { schemaVersion: "1", root: { kind: "Card", props: { title: "A" } } },
      },
    };
    const newer: any = {
      messageType: "ui_tree",
      resultMap: {
        tree: { schemaVersion: "1", root: { kind: "Card", props: { title: "B" } } },
        appliedPatches: [{ op: "replace", path: "/root/props/title", value: "B" }],
      },
    };
    const featured = findFeaturedGenUi([[older], [newer]]);
    expect(featured?.tree?.root?.props?.title).toBe("B");
  });

  it("finds featured GenUI nested under timeline containers", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      resultMap: {
        tree: {
          schemaVersion: "1",
          root: { kind: "Stack", props: {}, children: [] },
        },
      },
    };
    const featured = findFeaturedGenUi([
      [{ task: "", children: [{ messageType: "tool_result" }, treeTask] } as any],
    ]);
    expect(featured?.tree?.root?.kind).toBe("Stack");
  });

  it("rebuilds featured tree from multiAgent patches relative to originalTree", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      messageId: "tree-1",
      resultMap: {
        tree: {
          schemaVersion: "1",
          root: { kind: "Card", props: { title: "Old", value: "1" }, children: [] },
        },
      },
    };
    const patch1: any = {
      messageType: "ui_patch",
      messageId: "p1",
      resultMap: {
        patches: [{ op: "replace", path: "/root/props/title", value: "Mid" }],
        mergedIntoTree: true,
      },
    };
    const patch2: any = {
      messageType: "ui_patch",
      messageId: "p2",
      resultMap: {
        patches: [{ op: "replace", path: "/root/props/value", value: "99" }],
      },
    };
    const featured = findFeaturedGenUi(
      [[{ task: "", children: [treeTask] } as any]],
      [[treeTask, patch1, patch2]]
    );
    expect(featured?.tree?.root?.props?.title).toBe("Mid");
    expect(featured?.tree?.root?.props?.value).toBe("99");
    expect(featured?.patchCount).toBe(2);
  });

  it("accepts patch path without /root prefix", () => {
    const treeTask: any = {
      messageType: "ui_tree",
      resultMap: {
        tree: {
          schemaVersion: "1",
          root: { kind: "Card", props: { title: "Old" }, children: [] },
        },
      },
    };
    const patchTask: any = {
      messageType: "ui_patch",
      resultMap: {
        patches: [{ op: "replace", path: "/props/title", value: "Loose" }],
      },
    };
    const featured = findFeaturedGenUi(null, [[treeTask, patchTask]]);
    expect(featured?.tree?.root?.props?.title).toBe("Loose");
  });
});
