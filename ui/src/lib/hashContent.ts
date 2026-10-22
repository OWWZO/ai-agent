/**
 * 短字符串哈希（djb2），用于生成缓存 key / 内容签名。
 *
 * 不是密码学哈希，只用来把「内容是否变过」压缩成短串比较。
 * 碰撞概率在几十 KB 量级下可接受；需要更强保证时不要用它。
 *
 * 抽在这里是因为 codeViewer 的 cacheKey 和 renderTasks 的渲染签名
 * 都用同一套算法 —— 两处各写一遍会各自漂移，而它们对碰撞的容忍度
 * 是一样的。
 */
export function hashContent(value: string): string {
  let hash = 5381;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 33) ^ value.charCodeAt(index);
  }
  return (hash >>> 0).toString(36);
}
