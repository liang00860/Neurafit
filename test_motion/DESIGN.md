# DESIGN.md

## 项目设计风格总览

这是一个偏高端创意工具和动态产品展示的 React/Vite 前端。整体风格是黑白极简、强动效、摄影素材驱动、局部高饱和强调色。页面不走传统营销站的堆卡片路线，而是用全屏章节、滚动视差、Bento 布局、交互式媒体面板和沉浸式页脚来制造节奏。

给 Stitch 生成新页面或新组件时，应优先保持：留白充足、结构清晰、动效顺滑、视觉重点强、文字克制。

## 核心关键词

- Minimal editorial landing page
- Motion-first interaction
- Black and white neutral system
- Large typography
- Real photography and product mockup imagery
- Scroll-driven storytelling
- Bento feature layout
- High-contrast CTA
- Polished shadcn/Radix component feel
- A little playful, but still premium

## 视觉语言

### 1. 色彩

主色系统以中性色为核心：

- 背景：白色或纯黑大面积铺底。
- 文字：接近纯黑和纯白，形成强对比。
- 容器：使用 `neutral`、`zinc`、`stone` 等低饱和灰阶。
- 边框：细线、低透明度，保持轻盈。
- 深色区块：用黑色背景承载评价区、页脚、深色动态卡片。

强调色只少量出现，用于制造记忆点：

- Hero 圆形背景：明亮黄色，接近 `yellow-400`。
- 登录插画：紫色 `#6C3FF5`、橙色 `#FF9B6B`、黄色 `#E8D754`、深黑 `#2D2D2D`。
- 步骤和状态：少量 lime，例如 `#C6EA7E`。
- 链接 hover：少量 sky blue。

避免把页面做成多彩糖果风。强调色应该像视觉标点，而不是铺满整个页面。

### 2. 字体与排版

项目使用 Geist Variable 作为主要字体，整体偏现代、理性、干净。

排版规则：

- Hero 标题可以非常大，使用 `text-7xl` 到 `text-9xl` 或 `clamp()`。
- 标题字重偏粗：`font-bold`、`font-extrabold`、`font-semibold`。
- 大标题行高偏紧：`leading-none`、`leading-[0.95]`、`leading-[100%]`。
- 导航文字小、全大写、字距较大：`text-sm`、`tracking-widest`。
- 正文保持短句和小段落，颜色用 `text-muted-foreground` 或 `text-foreground/60`。
- 数字分页可使用 mono 风格，增强高级感。

Stitch 生成内容时，标题要有强视觉存在感，正文不要写太多解释性文案。

### 3. 布局

页面结构是连续的沉浸式章节：

- 首屏：全屏 Hero，顶部导航，中间视觉主体，左右文字错位布局。
- 滚动区：长高度容器，例如 `300vh`，使用 sticky 视差。
- 产品展示区：左右分栏，左侧说明和 accordion，右侧大图 media panel。
- Bento 区：三列网格，不同卡片跨行跨列。
- 评价区：黑底、人物图像、缩略图导航、横向/纵向切换动效。
- 页脚：黑底、低重心内容、动态波形条。

布局应多用完整页面宽度的 section，不要把每个区块都包成漂浮卡片。卡片只用于真正的内容单元，例如媒体面板、Bento 单元、表单和弹层。

### 4. 圆角、边框和阴影

整体圆角中等偏大，但不是完全胶囊化：

- Button：`rounded-lg`
- Card：`rounded-xl` 或 `rounded-2xl`
- 大型媒体容器：`adaptive-rounded`
- 头像/图标按钮：可使用圆形

边框细、克制、低对比。阴影主要用于大型滚动展示卡，强调空间层级。普通 UI 不需要厚重阴影。

### 5. 图像与媒体

项目大量使用真实摄影图像，图片应有明确主体：

- 自然风景用于 scroll parallax。
- 人物图像用于 testimonial。
- 产品/屏幕/抽象图用于 feature showcase。
- Hero 使用人物或产品主体，并配合一个强色圆形作为背后图形。

图片处理规则：

- 多用 `object-cover`。
- 允许全屏裁切和局部放大。
- 图片本身要清晰，不要使用过度模糊的 stock 背景。
- 需要让图片承担视觉叙事，而不是只作为装饰。

## 动效语言

动效是这个项目的核心气质，应该自然、流畅、有层次。

主要动效模式：

- 页面进入：opacity + translate，轻微延迟。
- Hero 主图：scale + y 位移进入。
- 滚动视差：sticky section、scale 放大、scroll progress 驱动。
- 3D 展示：`rotateX` 从倾斜过渡到平面，配合 scale。
- 卡片入场：stagger children，spring easing。
- Tabs/slider：图片和文字用 AnimatePresence 切换。
- 页脚波形：进入视口后使用 requestAnimationFrame 做轻微循环运动。
- 登录页插画：鼠标跟随、眨眼、输入状态反馈。

动效不应只是炫技，要服务于滚动叙事和状态反馈。速度建议保持 0.3s 到 0.8s，缓动可使用 `[0.22, 1, 0.36, 1]` 或 spring。

## 组件风格

项目基于 shadcn/Radix 体系，组件应保持干净、可访问、状态明确。

按钮：

- 默认按钮黑底白字或反色，高对比。
- 次级按钮透明或浅灰背景。
- Icon button 用 lucide 图标。
- active 状态可以轻微下压。

Tabs：

- 浮在媒体底部。
- 半透明背景加 backdrop blur。
- active tab 使用强反差色。

Badges：

- 小尺寸、低调，用于 eyebrow、状态和统计信息。
- 不要让 badge 抢过标题。

Accordion：

- 左侧说明区使用。
- 标题简短，内容一两句。

Cards：

- Bento 和 media panel 可以使用卡片。
- 卡片内部保持单一主题，不要卡片套卡片。

表单：

- 登录表单偏简洁，输入框带 lucide 图标。
- 错误信息用轻量 destructive 背景。
- 插画区域可以更玩味，但右侧表单要保持专业。

## 响应式规则

项目使用 `useResponsiveScale` 和 CSS 变量统一适配：

- `--font-scale`
- `--space-scale`
- `--component-scale`
- `--section-scale`
- `--page-padding`
- `--section-y`
- `--component-gap`
- `--component-pad`

Stitch 生成时应继续使用 `clamp()` 和响应式 grid：

- 移动端优先单列。
- 平板和桌面逐步切换为双列或三列。
- 大标题要用 `clamp()` 防止溢出。
- 图片、按钮、卡片尺寸要有稳定边界。
- 避免移动端文字和媒体互相覆盖。

## 文案语气

文案应该短、直接、有设计感：

- 少解释，多展示。
- 标题可以有宣言感，例如 "less is more."、"Scroll Animations"。
- 按钮用明确动作，例如 "Get started"、"Browse examples"、"Read More"。
- 导航用简短名词，例如 HOME、PRODUCT、STORE、ABOUT US。

如果改为中文界面，语气也要克制，不要写成长段功能说明。

## 给 Stitch 的生成提示词

Use this style when generating new screens:

Create a premium motion-first React/Tailwind landing page with a minimalist black-and-white editorial style. Use Geist-like typography, oversized bold headings, restrained body copy, real photographic media, and smooth Framer Motion style transitions. Structure the page as immersive full-width sections rather than decorative floating cards. Use neutral surfaces, thin borders, medium rounded corners, and sparse accent colors such as yellow, lime, purple, orange, or sky blue only as visual highlights. Include scroll-driven storytelling, bento feature grids, tabbed media panels, and polished shadcn/Radix-like controls. Keep the interface clean, high-contrast, responsive, and visually calm, with motion used for focus and flow.

## Do

- Use large, confident typography.
- Keep backgrounds mostly white or black.
- Use real imagery as a primary visual element.
- Add smooth scroll and state transitions.
- Use shadcn-like components with lucide icons.
- Keep section layouts full-width and breathable.
- Use `clamp()` for responsive typography and spacing.
- Make mobile layouts stable and readable.

## Don't

- Do not use generic colorful gradient hero sections.
- Do not overuse purple/blue gradients.
- Do not create dense marketing copy blocks.
- Do not nest UI cards inside other cards.
- Do not make every section a floating rounded card.
- Do not use tiny decorative images when a strong real image is needed.
- Do not add excessive shadows or glass effects.
- Do not make the page feel static; motion is part of the brand.

## Existing Project References

- Main composition: `next-app/src/App.tsx`
- Global design tokens: `next-app/src/index.css`
- Hero style: `next-app/src/components/minimalist-hero.tsx`
- Scroll parallax: `next-app/src/components/ui/zoom-parallax.tsx`
- 3D scroll showcase: `next-app/src/components/ui/container-scroll-animation.tsx`
- Feature/media section: `next-app/src/components/feature-showcase.tsx`
- Bento layout: `next-app/src/components/ui/bento-grid-showcase.tsx`
- Testimonial slider: `next-app/src/components/ui/testimonial-slider.tsx`
- Playful login overlay: `next-app/src/components/login-page.tsx`
- Responsive scaling: `next-app/src/hooks/use-responsive-scale.ts`
