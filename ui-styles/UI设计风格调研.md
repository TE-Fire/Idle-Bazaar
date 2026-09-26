# UI 设计风格调研笔记

> 调研日期：2026-09-25
> 用途：毕业设计选题前的视觉方向梳理
> 数据来源：trends.daisyui.com、empire-ui.com、techyheaven.com、masrisystems.com、khired digital、兰亭妙微、网易/搜狐设计站点合集

姐妹文档：
- `UI视觉构成要素体系.md` —— 讲"视觉由哪些东西构成"，六层架构 + 字体 / 布局 / 色彩 / 质感 / 动效 / 感知术语全景
- 本文 —— 讲"有哪些风格"，17 个风格的明细与选型判断
- `pages/*.html` —— 讲"风格长什么样"，随机抽中的 4 个风格静态演示

---

## 一、四大谱系总览

当前主流 UI 视觉风格可以按底层动机归为四类（而非按时间线）：

```
┌─ 材质感 · 拟物回归 ─┐  ┌─ 反精致 · 粗野表达 ─┐
│ Glassmorphism       │  │ Brutalism           │
│ Neumorphism         │  │ Neo-Brutalism       │
│ Claymorphism        │  │ Anti-Design         │
│ Skeuomorphism 2.0   │  │ Maximalism          │
│ Liquid Glass        │  │ Hand-Drawn          │
└─────────────────────┘  └─────────────────────┘
┌─ 极简与秩序 ────────┐  ┌─ AI 与空间 ─────────┐
│ Flat 2.0 / Material3│  │ Spatial UI          │
│ Swiss Style         │  │ AI-Native UI        │
│ Minimal Dark        │  │ Aurora UI           │
│ Editorial           │  │ Calm UI             │
│ Bento Grid          │  │ Retro / Y2K         │
└─────────────────────┘  └─────────────────────┘
```

关键认知：**风格不是互斥选项，而是可以叠加的图层。**
2026 年约 70% 的新界面采用 "flat-plus" 路线——以扁平为地基，叠加少量深度/玻璃/材质提示；纯 Neumorphism 实际投产率不足 2%（无障碍硬伤）。

---

## 二、逐风格明细

### 1. Glassmorphism 玻璃拟态 ★★★★★

| 项 | 内容 |
|---|---|
| 视觉特征 | 半透明磨砂层、鲜艳背景、1px 高光描边、多层 Z 轴堆叠 |
| 核心 CSS | `backdrop-filter: blur(12px)` + `background: rgba(255,255,255,.1)` + `border: 1px solid rgba(255,255,255,.2)` |
| Tailwind | `backdrop-blur-md bg-white/10 border-white/20` |
| 适用 | 悬浮导航 dock、弹窗、SaaS 看板、个人主页 |
| 坑点 | 低端安卓 GPU 开销大 → **限制单个滚动容器内的模糊面数量**；必须给文本留不透明阅读区，否则对比度挂掉 |

### 2. Neumorphism 新拟态 ★★☆☆☆（慎选）

| 项 | 内容 |
|---|---|
| 视觉特征 | 元素像从同一底色里"挤出来"，左上白阴影 + 右下暗阴影 |
| 核心 CSS | `box-shadow: 6px 6px 12px #b8b9be, -6px -6px 12px #ffffff`（背景必须同色系） |
| 适用 | 开关、滑块、音量条等**局部组件**；作品集 |
| 坑点 | 未激活态对比度接近 1:1，不满足 WCAG 2.2。**禁止用于主 CTA 和表单标签** |

### 3. Claymorphism 黏土拟态 ★★★★☆

| 项 | 内容 |
|---|---|
| 视觉特征 | 大圆角 24–40px、胖乎乎的充气质感、马卡龙色、顶部内白高光 |
| 核心 CSS | `border-radius:32px; box-shadow: 0 20px 60px -10px rgba(0,0,0,.3), inset 0 -4px 0 rgba(0,0,0,.15), 0 -4px 0 rgba(255,255,255,.5)` |
| 适用 | EdTech、儿童/教育、健康、 onboarding 引导页 |

### 4. Skeuomorphism 2.0 触感写实 ★★★☆☆

不再是 2010 年那种整屏皮革缝线，而是**缩微纹理 + 现代扁平布局**的混血。
- 材质清单：木纹 / 纸张 / 拉丝金属 / 印章 / 档案（Historical Manuscript Skeuomorphism）
- 变体：Real-Wood（音频、家居）、Luminous Eco（气候、健康）、Y2K Techno（透明外壳 + chrome 控件）
- 适用：奢侈品展示、拟真乐器 App、文化传承类选题

### 5. Brutalism / Neo-Brutalism 粗野主义 ★★★★★

| 项 | 内容 |
|---|---|
| 视觉特征 | 2–4px 纯黑描边、4–8px 硬阴影（无模糊）、零圆角、高饱和平涂、巨号字体 |
| 核心 CSS | `border-2 border-black shadow-[4px_4px_0px_#000]` |
| 优点 | **高对比度是结构性的而非偶然的** → 天然满足无障碍；零 GPU 成本；全浏览器渲染一致；写起来最快 |
| 趋势 | 2024 年以来增长最快的流派，绝大多数品牌用的是 Neo-Brutalism（加了色彩和性格），纯 Brutalism 太粗糙 |
| 适用 | 开发者工具、创作者经济、潮牌、校园/年轻向产品 |

### 6. Anti-Design / Maximalism / Hand-Drawn ★★★☆☆

- **Anti-Design**：故意打破常规（错位、叠加、失衡网格），高风险高性格，不适合新手
- **Maximalism**：颜色/纹理/图案全部拉满，靠品牌自信撑
- **Hand-Drawn / Scrapbook**：手绘线稿、噪点、贴纸拼贴 → 给界面注入"人味"，对抗 AI 生成的同质模板明显有效
- 2026 关键词：**反完美主义**（刻意的不完美）+ **温暖极简 2.0**

### 7. Flat 2.0 / Material 3 ★★★☆☆

- Flat 3.0 已不再是 iOS 7 式的纯扁平，允许 1px 边界 + `box-shadow: 0 1px 3px rgba(0,0,0,.08)` 微提示
- Material 3（Material You）：动态取色（由种子色生成色调盘）、2dp 高度用色调叠加而非阴影表达、圆角固定 4/8/28px 三档
- 评价：专业 UI 的地基，但**作为毕设风格几乎没有辨识度**，建议只做基线对照组

### 8. Minimal Dark（Linear / Vercel 风）★★★★★

- 近黑底（`#09090b`）、`border-zinc-800` 发丝级描边、发光渐变徽章、等宽字体微文案
- 适合 B2B 开发者平台、云控制台、API 面板
- 优点：高级感强、实现成本低、评委普遍接受度高

### 9. Bento Grid 便当盒布局 ★★★★★

严格说是**布局范式**而非视觉风格，可与上面任意一种叠加。
源自日式便当盒的模块化 asymmetric grid → 演化为动态响应式单元格（悬停放大 / 点击展开）。
适合：功能总览、数据看板、作品集首页。

### 10. Aurora UI 极光渐变 ★★★★☆

多层 radial-gradient 缓慢流动做背景氛围，前景保持锐利。AI 产品落地页标配。

### 11. Liquid Glass 液态玻璃 ★★☆☆☆

Apple 2025 起推的动态折射材质，会随设备倾斜产生实时折射与高光流动。视觉最抓人，但纯 CSS 复刻纯度有限，**实现成本高**。

### 12. AI-Native UI / GenUI ★★★★☆

三个层次：
1. **生成式界面**：界面随用户意图、行为、场景实时重组
2. **自适应布局**：高频功能自动前置、低频自动折叠 → 每个用户拥有"专属界面"
3. **代理式体验（Agentic UX）**：从"主动操作"转向"委托代理"，指标从停留时长变解决效率

设计师角色变化：从"画界面"转为"设计规则"（定义品牌规范、交互边界、组件逻辑）。
风险：毕设里 AI 部分容易吃掉评审焦点，且需要接 LLM API。

### 13. Spatial UI 空间计算 ★★★☆☆

Vision Pro / 平价 XR 普及推动。Z 轴分层、跟随式光照、手势/眼动/语音多模态。
硬件门槛决定 demo 阶段只能在 2D 里做隐喻版。

### 14. Calm UI 平静界面 ★★★★☆

低饱和、柔和过渡、控制动效强度、支持动态字体缩放。面向神经多样性（ADHD / 自闭症 / 感官敏感）用户。常与无障碍章节一起作为**设计亮点**写进论文。

### 15. Retro / Y2K ★★★☆☆

透视感的 glossy 塑料外壳、chrome 渐变字、像素font、繁星闪点、早期系统窗体的斜面边框。
潮流度极高，但**评委口味两极分化**（部分老师认为"不够专业"）。

---

## 三、可检索的参考站点

### 查 App 界面与产品流程
| 站点 | 用途 |
|---|---|
| Mobbin | 真实 iOS/Android App 全流程截图，竞品分析必备 |
| Refero | 3 万+ 网页与 App 截图 |
| UI Notes | 线上优秀 App 完整 UI 截图 |
| SaaS Interface | SaaS 后台 UI/UX 示例库 |
| SCRNSHTS | App Store 预览宣传图合集 |
| Screenlane | App/Web UI + 交互效果收录 |

### 查网页视觉与获奖动效
| 站点 | 用途 |
|---|---|
| Awwwards | 网页设计风向标，学版式层级与交互动效 |
| Land-book / Lapa Ninja | 落地页结构与区块顺序 |
| SiteInspire | 极简品牌站、网格与排版 |
| Godly / Recent Design | 实验性网站、科技视觉与动效 |
| One Page Love | 单页网站灵感 |

### 查灵感与趋势
| 站点 | 用途 |
|---|---|
| Dribbble | 图标、微动效、UI 细节碎片灵感 |
| Behance | 完整商业全案，学提案逻辑 |
| Pinterest / 花瓣网 | 建情绪板、配色与风格对照 |
| UI 中国 / 站酷 ZCOOL | 国内可落地商业案例 |
| 优设 UISDC | 版式理论、大厂设计规范、风格解析 |
| trends.daisyui.com | 按"风格"分类的趋势目录，适合做文献综述素材 |

### 组件与素材
| 站点 | 用途 |
|---|---|
| Uiverse | 社区 CSS/Tailwind 组件，免费可直接用 |
| Figma Community | 组件库与模板 |
| Iconfont（阿里）/ IconPark（字节） | 免费商用矢量图标 |
| unDraw / Storyset | 免费商用插画 |

---

## 四、本次抽中的四个风格（静态演示已生成）

随机抽取结果：**Neo-Brutalism / Glassmorphism / AI-Native UI / Y2K**

演示内容统一为虚构产品 **Nimbus 团队协作看板**（同一套信息结构），以便公平对比视觉差异：

| 页面 | 文件 |
|---|---|
| Neo-Brutalism 新粗野主义 | `pages/neo-brutalism.html` |
| Glassmorphism 玻璃拟态 | `pages/glassmorphism.html` |
| AI-Native UI 生成式界面 | `pages/ai-native.html` |
| Y2K 复古回潮 | `pages/y2k.html` |

打开入口：`index.html`

---

## 五、选题建议

- **偏设计类毕设**：`Bento Grid + 单一特色风格（玻璃拟态 或 新粗野）`
  → 格子布局负责"看起来体系化"，特色风格负责"看起来有想法"
- **偏工程类毕设**：`Minimal Dark + AI-Native UI`
  → 视觉稳、技术点足，自适应布局 + 生成式卡片可作为核心创新点
- **想拿高分且有把握**：在任意选题里挂一个 **无障碍 / Calm UI** 章节，这是 2026 年从"合规选项"变成"核心竞争力"的方向，论文好写、演示好讲

---

## 六、待确认

- [ ] 学院要求的技术栈（纯前端 / 需不需要后端 + 数据库）
- [ ] 更偏设计答辩还是工程答辩
- [ ] 是否有指定命题（如"XX 管理系统"类的老师命题）
