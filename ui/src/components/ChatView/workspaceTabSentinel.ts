/**
 * 工作区「最后一个真实 tab 被关掉 → 收起整个工作区」的判定。
 *
 * 抽成纯函数的原因：这段逻辑有**两个必须同时满足的时序约束**，写在组件 effect 里
 * 只能靠肉眼看，测不了 —— 而它一旦写错，表现是「工作区刚打开就自己收起」或
 * 「关光了还留一个空面板」，都属于用户一眼可见的回归。
 *
 * 约束一：**首次上报的 0 不是关闭命令**。ActionView 挂载时会立刻上报一次 tab 数，
 * 而 pending tab 的消费 effect 与上报 effect 在同一次提交里执行，上报读到的仍是
 * 旧 state（0）。只有真的经历过 `>0` 之后再回到 0，才算「关光了」。
 *
 * 约束二：**并发打开意图优先**。「关最后一个 tab」与「点工具卡开新 tab」可能落在
 * 同一次提交里，此时不能收起，否则刚点开的 tab 会被连带关掉。
 */

export type WorkspaceTabCountDecision = {
  /** 更新后的「是否经历过有 tab」哨兵，调用方必须写回 */
  hadTabs: boolean;
  /** 是否应当收起整个工作区 */
  collapse: boolean;
};

export function decideWorkspaceTabCountChange(params: {
  /** 上一次判定留下的哨兵（初始必须是 false） */
  hadTabs: boolean;
  /** ActionView 上报的当前 tab 数 */
  count: number;
  /** 此刻是否还有 pending 打开意图（待开文件 / source / 子智能体 / 目录 / 任务 tab，或覆盖层） */
  hasPendingOpenIntent: boolean;
}): WorkspaceTabCountDecision {
  const { hadTabs, count, hasPendingOpenIntent } = params;

  if (count > 0) {
    return {
      hadTabs: true,
      collapse: false
    };
  }

  // 还没经历过「有 tab」的 0：初次挂载 / 刚打开工作区，pending 稍后才会建 tab。
  if (!hadTabs) {
    return {
      hadTabs: false,
      collapse: false
    };
  }

  if (hasPendingOpenIntent) {
    return {
      hadTabs,
      collapse: false
    };
  }

  return {
    hadTabs: false,
    collapse: true
  };
}

/**
 * 流式「自动跟随」（原「动态」模式）是否应当展开工作区并开一个真实任务 tab。
 *
 * 工作区现在「有 tab 才有内容」，所以旧的 `showAction`（流里出现了值得看的任务）
 * 不能再单独当展开条件 —— 那会得到一张空白面板。自动跟随必须**同时**开一个 tab，
 * 而开 tab 有代价（抢焦点、堆 tab），所以闸门要写清楚：
 *
 * - `workspaceVisible`：工作区已经可见 → 不动（不抢用户正在看的 tab，也不堆 tab）；
 * - `userClosedWorkspace`：本轮用户手动关过 → 不弹回；
 * - `hasTabs`：还有 tab → 工作区本来就该在，不需要自动跟随兜底；
 * - `showAction` / `hasLiveTask`：流式意图 + 真的拿到了可渲染任务。
 */
export function shouldAutoFollowWorkspaceTask(params: {
  workspaceVisible: boolean;
  showAction: boolean;
  hasLiveTask: boolean;
  userClosedWorkspace: boolean;
  hasTabs: boolean;
}): boolean {
  const {
    workspaceVisible,
    showAction,
    hasLiveTask,
    userClosedWorkspace,
    hasTabs,
  } = params;

  if (workspaceVisible || userClosedWorkspace || hasTabs) {
    return false;
  }
  return showAction && hasLiveTask;
}

/**
 * 自动跟随该开哪种 tab。
 *
 * 「跟随」跟的是**这个任务在展示什么**，不是任务的类型名：
 *
 * - 任务有可预览的主产物（工具写出的文件）→ **文件 tab**。它与任务 tab 渲染的是
 *   同一份内容（`openFileInTab` 用的 `task` 就是产出这个文件的那个任务），但 tab
 *   上显示的是文件名 + 文件类型角标 —— 这正是用户认的那个东西，也正是用户点
 *   「打开文件」会得到的那一个（身份键一致，天然不会出现两个一样的预览）。
 *   开成任务 tab 的话，tab 上会是工具名（比如 `bash`），用户会以为名字错了。
 * - 没有产物（纯工具输出）→ **任务 tab**，标题用工具名。
 *
 * 例外：`deep_search` 阶段有自己的过程面板（章节 / 搜索列表），产物文件不是它的
 * 主体展示，所以即便带了文件也保持任务 tab。
 */
export function resolveAutoFollowTabKind(params: {
  messageType?: string;
  hasPrimaryFile: boolean;
}): "file" | "task" {
  if (params.messageType === "deep_search") {
    return "task";
  }
  return params.hasPrimaryFile ? "file" : "task";
}
