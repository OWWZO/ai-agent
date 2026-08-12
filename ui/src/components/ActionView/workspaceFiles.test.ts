import { describe, expect, it } from "vitest";

import {
  collectSessionFileTasks,
  collectWorkspaceFiles,
  flattenTasksWithFiles,
} from "./workspaceFiles";

describe("workspaceFiles session aggregation", () => {
  it("flattens nested children file tasks", () => {
    const tasks = [
      {
        messageType: "task",
        children: [
          {
            messageType: "file",
            messageTime: "1",
            fileInfo: [
              {
                fileName: "report.md",
                domainUrl: "http://x/preview/s/report.md",
                ossUrl: "http://x/download/s/report.md",
              },
            ],
          },
        ],
      },
    ] as any;

    const flat = flattenTasksWithFiles(tasks);
    expect(flat).toHaveLength(1);
    expect(flat[0].messageType).toBe("file");
  });

  it("aggregates files across chat turns and live taskList", () => {
    const chatList = [
      {
        tasks: [
          [
            {
              messageType: "file",
              messageTime: "1",
              fileInfo: [
                {
                  fileName: "old.md",
                  domainUrl: "http://x/preview/s/old.md",
                  ossUrl: "http://x/download/s/old.md",
                },
              ],
            },
          ],
        ],
        multiAgent: { tasks: [] },
      },
    ] as unknown as CHAT.ChatItem[];

    const live = [
      {
        messageType: "file",
        messageTime: "2",
        fileInfo: [
          {
            fileName: "new.md",
            domainUrl: "http://x/preview/s/new.md",
            ossUrl: "http://x/download/s/new.md",
          },
        ],
      },
    ] as any;

    const sessionTasks = collectSessionFileTasks(chatList, live);
    const files = collectWorkspaceFiles(sessionTasks);
    expect(files.map((file) => file.name).sort()).toEqual(["new.md", "old.md"]);
  });

  it("includes user-uploaded chat.files in session file list", () => {
    const chatList = [
      {
        files: [
          {
            name: "photo.png",
            url: "http://x/preview/s/photo.png",
            downloadUrl: "http://x/download/s/photo.png",
            type: "png",
            size: 12,
          },
        ],
        tasks: [],
        multiAgent: { tasks: [] },
      },
    ] as unknown as CHAT.ChatItem[];

    const sessionTasks = collectSessionFileTasks(chatList, []);
    const files = collectWorkspaceFiles(sessionTasks);
    expect(files.map((file) => file.name)).toEqual(["photo.png"]);
    expect(files[0]?.url).toBe("http://x/preview/s/photo.png");
  });
});
