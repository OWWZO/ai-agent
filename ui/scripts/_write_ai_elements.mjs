// 把官方 AI Elements 全套写入独立目录，避免影响现有 src/components/ai-elements/
import fs from 'node:fs'
import path from 'node:path'

const SRC = '.shadcn-tmp/aie'
const OUT = 'src/components/ai-elements-official'

const COMPONENTS = [
  'artifact', 'canvas', 'chain-of-thought', 'checkpoint', 'code-block',
  'confirmation', 'connection', 'context', 'controls', 'conversation',
  'edge', 'image', 'inline-citation', 'loader', 'message',
  'model-selector', 'node', 'open-in-chat', 'panel', 'plan',
  'prompt-input', 'queue', 'reasoning', 'shimmer', 'sources',
  'suggestion', 'task', 'tool', 'toolbar', 'web-preview',
]

fs.mkdirSync(OUT, { recursive: true })

let written = 0
const report = []

for (const name of COMPONENTS) {
  const jsonPath = path.join(SRC, `${name}.json`)
  if (!fs.existsSync(jsonPath)) {
    report.push(`缺失 registry 数据: ${name}`)
    continue
  }
  const item = JSON.parse(fs.readFileSync(jsonPath, 'utf8'))
  for (const file of item.files ?? []) {
    const target = path.join(OUT, `${name}.tsx`)
    fs.writeFileSync(target, file.content, 'utf8')
    written++
    report.push(`${name}.tsx  ${String(file.content.split('\n').length).padStart(4)} 行`)
  }
}

console.log(`写入目录: ${OUT}`)
console.log(`文件数: ${written}`)
console.log(report.join('\n'))
