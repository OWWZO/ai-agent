interface TypeWriterCoreOptions {
  onConsume: (str: string) => void; // 定义一个回调函数，用于消费（处理）字符
  maxStepSeconds?: number; // 可选属性，定义最大步进间隔（毫秒）
}

export default class TypeWriterCore {
  onConsume: (str: string) => void; // 消费（处理）字符的回调函数
  queueList: string[] = []; // 存储待消费字符的队列
  maxStepSeconds: number = 50; // 默认最大步进间隔为50毫秒
  maxQueueNum: number = 2000; // 队列中最大字符数
  timer: NodeJS.Timeout | number | undefined; // 用于控制下一次消费的定时器，兼容浏览器和Node.js环境

  constructor({onConsume, maxStepSeconds}: TypeWriterCoreOptions) {
    this.onConsume = onConsume; // 初始化消费字符的回调

    if (maxStepSeconds !== undefined) {
      this.maxStepSeconds = maxStepSeconds; // 如果提供了最大步进间隔，则使用提供的值
    }
  }

  // 动态计算消费字符的速度
  dynamicSpeed() {
    const speedQueueNum = this.maxQueueNum / this.queueList.length; // 根据队列长度动态调整速度
    const resNum = +(
      speedQueueNum > this.maxStepSeconds
        ? this.maxStepSeconds : speedQueueNum
    ).toFixed(0); // 确保结果为整数

    return resNum;
  }

  // 将字符串添加到队列中
  onAddQueueList(str: string) {
    this.queueList = [...this.queueList, ...str.split('')]; // 分解字符串为字符数组并追加到队列
  }

  // 添加字符串到队列的公共方法
  add(str: string) {
    if (!str) return; // 如果字符串为空，则不执行任何操作
    this.onAddQueueList(str); // 调用内部方法添加字符串到队列
  }

  // 从队列中消费一个字符
  consume() {
    if (this.queueList.length > 0) {
      const str = this.queueList.shift(); // 从队列头部移除一个字符
      // eslint-disable-next-line @typescript-eslint/no-unused-expressions
      str && this.onConsume(str); // 如果字符存在，则调用消费函数处理该字符
    }
  }

  // 定时消费队列中的字符
  next() {
    this.timer = setTimeout(() => {
      if (this.queueList.length > 0) {
        this.consume(); // 消费一个字符
        this.next(); // 递归调用，继续消费下一个字符
      }
    }, this.dynamicSpeed()); // 根据动态速度设置定时器
  }

  // 开始消费队列中的字符
  start() {
    this.next(); // 调用next方法开始消费字符
  }

  // 渲染完成后的清理工作
  onRendered() {
    if (this.timer) clearTimeout(this.timer); // 清除定时器，防止继续消费字符
  }

  // 清空队列并停止当前的消费过程
  onClearQueueList() {
    this.queueList = []; // 清空字符队列
    if (this.timer) clearTimeout(this.timer); // 清除定时器
  }
}
