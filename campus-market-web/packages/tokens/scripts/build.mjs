#!/usr/bin/env node
/**
 * 设计令牌构建脚本（零依赖，只用 Node 内置模块）
 *
 * 职责：
 *   src/primitive.json        ── 原子层（灰阶 / 品牌色池 / 字阶 / 间距 / 圆角）
 *   src/web.semantic.json     ── 用户 Web 端语义层（黄 #FFD100，design/01）
 *   src/admin.semantic.json   ── 管理后台语义层（蓝 #1677FF，design/03）
 *   src/mini.semantic.json    ── 小程序语义层（橙 #FF6A00，design/02）
 *                    │
 *                    ├─ deepMerge（语义层覆盖原子层，逐叶子节点合并）
 *                    └─ flatten（嵌套对象 → 扁平键值）
 *                            │
 *   dist/web.css    ── :root { --cm-* }     供 Web 端直接 import
 *   dist/admin.css  ── :root { --cm-* }     供管理后台直接 import
 *   dist/mini.css   ── :root { --cm-* }     小程序 px 版（H5 预览用）
 *   dist/web.scss   ── $cm-*                供 sass 里做运算 / Element Plus 覆写
 *   dist/admin.scss ── $cm-*
 *   dist/mini.scss  ── $cm-*
 *   dist/uni.scss   ── $cm-*（尺寸 ×2 转 rpx）uni-app 专用
 *   dist/*.json     ── 合并后的结构化令牌，给 JS/TS 用（ECharts 主题、内联样式）
 *
 * 命名规则：
 *   color.text.1  → --cm-color-text-1 / $cm-color-text-1
 *   fontSize.xl   → --cm-font-size-xl  （camelCase 自动转 kebab-case）
 *   layout.nav-h  → --cm-layout-nav-h
 *
 * 单位规则：
 *   JSON 源里尺寸一律写 px（与原型 CSS 一致，方便对照 diff）。
 *   只有 uni.scss 会把 fontSize/space/radius/layout 四组下的 px 乘以 RPX_SCALE 转成 rpx
 *   ——750rpx = 屏宽，原型按 375px 设计稿出图，故 1px = 2rpx。
 *   颜色、阴影不受影响。
 *
 * 运行：pnpm tokens（根目录）或 pnpm --filter @campus/tokens build
 */

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SRC = resolve(__dirname, '../src')
const DIST = resolve(__dirname, '../dist')

/** 设计稿 375px → rpx 的换算系数（750rpx = 屏宽） */
const RPX_SCALE = 2

/** 会被 rpx 换算影响的分组；其余（color / shadow）原样输出 */
const DIMENSION_GROUPS = new Set(['fontSize', 'space', 'radius', 'layout'])

/**
 * 三端产物配置
 * name 决定输出文件名；semantic 决定叠加哪份语义层；rpx 决定是否生成 rpx 版本
 */
const TARGETS = [
  { name: 'web', semantic: 'web.semantic.json', label: '用户 Web 端' },
  { name: 'admin', semantic: 'admin.semantic.json', label: '管理后台' },
  { name: 'mini', semantic: 'mini.semantic.json', label: '移动端小程序', rpx: true }
]

/* ── 工具函数 ───────────────────────────────────────────── */

const readJson = (file) => JSON.parse(readFileSync(resolve(SRC, file), 'utf-8'))

/** 判断是否为「纯对象」（排除数组 / null） */
const isPlainObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v)

/** 深合并：override 的叶子值覆盖 base，base 独有的键保留 */
function deepMerge(base, override) {
  if (!isPlainObject(base)) return override
  if (!isPlainObject(override)) return override
  const out = { ...base }
  for (const [k, v] of Object.entries(override)) {
    out[k] = isPlainObject(v) && isPlainObject(base[k]) ? deepMerge(base[k], v) : v
  }
  return out
}

/** camelCase → kebab-case：fontSize → font-size，brandDark → brand-dark */
const kebab = (s) => s.replace(/[A-Z]/g, (m) => '-' + m.toLowerCase())

/**
 * 扁平化：{ color: { text: { 1: '#000' } } } → [['color','text','1'], '#000']
 * 只取叶子节点（非对象）作为令牌
 */
function flatten(obj, prefix = []) {
  const rows = []
  for (const [key, value] of Object.entries(obj)) {
    const path = [...prefix, key]
    if (isPlainObject(value)) rows.push(...flatten(value, path))
    else rows.push([path, String(value)])
  }
  return rows
}

/** 路径 → 令牌名：['color','text','1'] → 'color-text-1' */
const tokenName = (path) => path.map(kebab).join('-')

/**
 * 单位换算：uni.scss 下把尺寸组的 px 转 rpx
 * @param {string[]} path 令牌路径
 * @param {string} value 原始值
 * @returns {string} 换算后的值
 */
function toUnit(path, value) {
  const m = /^(-?\d+(?:\.\d+)?)px$/.exec(value)
  if (!m) return value
  if (!DIMENSION_GROUPS.has(path[0])) return value
  const n = Number(m[1]) * RPX_SCALE
  return `${Number.isInteger(n) ? n : Number(n.toFixed(2))}rpx`
}

/* ── 产物生成器 ─────────────────────────────────────────── */

const banner = (label) =>
  [
    '/* 自动生成，请勿手改 */',
    `/* 源：packages/tokens/src/{primitive,${label}}.json */`,
    '/* 重新生成：pnpm tokens */',
    ''
  ].join('\n')

/** CSS 自定义属性：--cm-color-brand: #FFD100; */
function toCss(rows, label, { rpx = false } = {}) {
  const body = rows
    .map(([path, value]) => {
      const v = rpx ? toUnit(path, value) : value
      return `  --cm-${tokenName(path)}: ${v};`
    })
    .join('\n')
  return `${banner(label)}:root {\n${body}\n}\n`
}

/** SCSS 变量：$cm-color-brand: #FFD100; */
function toScss(rows, label, { rpx = false } = {}) {
  const body = rows
    .map(([path, value]) => {
      const v = rpx ? toUnit(path, value) : value
      return `$cm-${tokenName(path)}: ${v};`
    })
    .join('\n')
  return `${banner(label)}${body}\n`
}

/* ── 主流程 ─────────────────────────────────────────────── */

const primitive = readJson('primitive.json')
mkdirSync(DIST, { recursive: true })

const summary = []

for (const { name, semantic, label, rpx } of TARGETS) {
  const merged = deepMerge(primitive, readJson(semantic))
  const rows = flatten(merged)

  writeFileSync(resolve(DIST, `${name}.css`), toCss(rows, semantic))
  writeFileSync(resolve(DIST, `${name}.scss`), toScss(rows, semantic))
  writeFileSync(resolve(DIST, `${name}.json`), JSON.stringify(merged, null, 2) + '\n')

  if (rpx) {
    writeFileSync(resolve(DIST, 'uni.scss'), toScss(rows, semantic, { rpx: true }))
  }

  summary.push(`  ${label.padEnd(6, '　')} dist/${name}.{css,scss,json}${rpx ? ' + dist/uni.scss(rpx)' : ''}  (${rows.length} 个令牌)`)
}

console.log('[tokens] 已生成：')
console.log(summary.join('\n'))
