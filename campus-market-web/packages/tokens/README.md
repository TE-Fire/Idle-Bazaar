# @campus/tokens · 设计令牌唯一源头

三份原型 CSS（`prototypes/{web,admin,miniprogram}`）已经是完整的 CSS 变量体系，
这里把它抽成 JSON 源，再用脚本生成各端产物。**业务代码禁止写死色值**。

## 源与产物

```
src/primitive.json        原子层：灰阶 / 品牌色池 / 字阶 / 间距 / 圆角（三端一致）
src/web.semantic.json     用户 Web 端语义层（黄 #FFD100，design/01）
src/admin.semantic.json   管理后台语义层（蓝 #1677FF，design/03）
src/mini.semantic.json    小程序语义层（橙 #FF6A00，design/02）
        │  scripts/build.mjs（零依赖，纯 Node）
        ▼
dist/web.css    :root{--cm-*}     用户端直接 import
dist/admin.css  :root{--cm-*}     管理端直接 import
dist/mini.css   :root{--cm-*}     小程序 px 版
dist/{web,admin,mini}.scss  $cm-*  Sass 里做运算、覆写组件库变量
dist/uni.scss   $cm-*（尺寸 ×2 → rpx）uni-app 专用
dist/{web,admin,mini}.json         给 JS/TS 用（ECharts 主题、内联样式）
```

## 命名规则

嵌套路径拍平，`camelCase` 转 `kebab-case`，统一加 `cm-` 前缀：

| JSON 路径 | CSS | SCSS |
|---|---|---|
| `color.brand`（admin） | `var(--cm-color-brand)` | `$cm-color-brand` |
| `color.text.1` | `var(--cm-color-text-1)` | `$cm-color-text-1` |
| `fontSize.xl` | `var(--cm-font-size-xl)` | `$cm-font-size-xl` |
| `layout.nav-h`（mini） | `var(--cm-layout-nav-h)` | `$cm-layout-nav-h` |

## 单位规则

- JSON 源里**尺寸一律写 px**，与原型 CSS 一致，便于对照 diff。
- 只有 `dist/uni.scss` 会把 `fontSize / space / radius / layout` 四组下的 px 乘 2 转 rpx
  （750rpx = 屏宽，原型按 375 设计稿出图，故 1px = 2rpx）。**颜色、阴影不换算**。

## 改令牌的正确姿势

1. 改 `src/*.json`，不要改 `dist/`（`dist/` 已 gitignore，改了也会被覆盖）。
2. 语义层只需写「与原子层不同的那部分」，逐叶子节点覆盖，其余自动继承。
3. 跑 `pnpm tokens` 重新生成。三个端的应用 `dev` 脚本已串好这一步，不必手动。

## 为什么不上 Tailwind / UnoCSS

`design/` 已经给出语义化 Token 层，原子化框架会引入第二套命名体系，
两边打架时改哪边都不对（tech/02 第九章）。原型里的 class 本身就是语义化的
（`.card` / `.tag`），直接迁移成本几乎为零。
