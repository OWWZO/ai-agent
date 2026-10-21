import { useSyncExternalStore } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";

export interface ConfirmOptions {
  title: string;
  content?: string;
  okText?: string;
  cancelText?: string;
  /** 危险操作（删除等）用红色确认按钮。 */
  danger?: boolean;
  /**
   * 纯提示（替代 antd `modal.info`）：只渲染一个确认按钮，不出现「取消」。
   * 信息类提示给出「取消」会让人以为可以拒绝。
   */
  alertOnly?: boolean;
  onOk?: () => void | Promise<void>;
}

type State = (ConfirmOptions & { open: boolean }) | null;

let state: State = null;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

/**
 * 命令式确认框，替代 antd `Modal.confirm`。
 *
 * 调用点保持 `confirm({ title, content, okText, danger, onOk })` 的形状；
 * 真正的渲染交给根节点挂载的 `<ConfirmDialogHost />`。
 */
export function confirm(options: ConfirmOptions) {
  state = {
    ...options,
    open: true,
  };
  emit();
}

function close() {
  state = null;
  emit();
}

/**
 * 全局唯一的确认框宿主，挂在 Layout 里。
 *
 * `onOk` 支持异步：期间禁用确认按钮，避免重复提交。
 */
export function ConfirmDialogHost() {
  const current = useSyncExternalStore(subscribe, () => state, () => null);

  if (!current) return null;

  return (
    <AlertDialog open={current.open} onOpenChange={(open) => !open && close()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{current.title}</AlertDialogTitle>
          {current.content ? (
            <AlertDialogDescription>{current.content}</AlertDialogDescription>
          ) : null}
        </AlertDialogHeader>
        <AlertDialogFooter>
          {current.alertOnly ? null : (
            <AlertDialogCancel onClick={close}>
              {current.cancelText ?? "取消"}
            </AlertDialogCancel>
          )}
          <AlertDialogAction
            variant={current.danger ? "destructive" : "default"}
            onClick={async () => {
              close();
              await current.onOk?.();
            }}
          >
            {current.okText ?? "确定"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
