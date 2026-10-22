import {
  useDebugValue,
  useEffect,
  useMemo,
  useRef,
  useSyncExternalStore,
} from 'react';

const objectIs = Object.is ?? ((x: unknown, y: unknown) => x === y || (x !== x && y !== y));

type Inst<Selection> = {
  hasValue: boolean;
  value: Selection | null;
};

export function useSyncExternalStoreWithSelector<Snapshot, Selection>(
  subscribe: (onStoreChange: () => void) => () => void,
  getSnapshot: () => Snapshot,
  getServerSnapshot: (() => Snapshot) | undefined,
  selector: (snapshot: Snapshot) => Selection,
  isEqual?: (a: Selection, b: Selection) => boolean
) {
  const instRef = useRef<Inst<Selection> | null>(null);

  if (instRef.current === null) {
    instRef.current = { hasValue: false, value: null };
  }

  const inst = instRef.current;

  const [getSelection, getServerSelection] = useMemo(() => {
    let hasMemo = false;
    let memoizedSnapshot: Snapshot;
    let memoizedSelection: Selection;

    const memoizedSelector = (nextSnapshot: Snapshot) => {
      if (!hasMemo) {
        hasMemo = true;
        memoizedSnapshot = nextSnapshot;
        const nextSelection = selector(nextSnapshot);

        if (isEqual && inst.hasValue) {
          const currentSelection = inst.value as Selection;
          if (isEqual(currentSelection, nextSelection)) {
            memoizedSelection = currentSelection;
            return currentSelection;
          }
        }

        memoizedSelection = nextSelection;
        return nextSelection;
      }

      const currentSelection = memoizedSelection;
      if (objectIs(memoizedSnapshot, nextSnapshot)) {
        return currentSelection;
      }

      const nextSelection = selector(nextSnapshot);
      if (isEqual && isEqual(currentSelection, nextSelection)) {
        memoizedSnapshot = nextSnapshot;
        return currentSelection;
      }

      memoizedSnapshot = nextSnapshot;
      memoizedSelection = nextSelection;
      return nextSelection;
    };

    const maybeGetServerSnapshot =
      getServerSnapshot === undefined ? undefined : () => memoizedSelector(getServerSnapshot());

    return [() => memoizedSelector(getSnapshot()), maybeGetServerSnapshot] as const;
  }, [getSnapshot, getServerSnapshot, inst, isEqual, selector]);

  const value = useSyncExternalStore(subscribe, getSelection, getServerSelection);

  useEffect(() => {
    inst.hasValue = true;
    inst.value = value;
  }, [inst, value]);

  useDebugValue(value);

  return value;
}

// zustand 的 ESM 入口是 `import X from '.../shim/with-selector.js'` 后再
// `const { useSyncExternalStoreWithSelector } = X` 解构的（CJS 互操作写法）。
// 真实包是 CJS，默认导出即 module.exports 对象；这里若直接默认导出函数本身，
// 解构出来是 undefined，调用时报 `useSyncExternalStoreWithSelector is not a function`。
export default { useSyncExternalStoreWithSelector };
