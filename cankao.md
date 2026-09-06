一、设计参考来源
参考模板	借鉴元素
Scroll Tied Video Section	深色主色调 #1D3045、极简排版、大字号标题、干净无多余装饰、文字直接叠在背景上
Lumora	玻璃态质感 (Glassmorphism)、徽章设计、信任数据展示、优雅的入场动画
Viktor Oddy	卡片网格布局、按钮阴影系统、评价卡片、底部固定导航

原参考：
```
Build a standalone cinematic one-page site that recreates this EXACTLY. Do not invent extra sections, extra copy, extra logos, extra overlays, GSAP, Lenis, or a different video. Match structure, copy, colors, type, spacing, breakpoints, animations, and scroll-video logic.

STACK
- Vite + React 18 + TypeScript + Tailwind CSS 3
- lucide-react icons: ArrowRight, ArrowDown, ChevronUp, Info, X
- mp4box ^0.5.2 for WebCodecs frame extraction
- Path alias @ -> src
- No routing. Single App. One full-viewport sticky scene, 500vh tall scroll track.

VIDEO (use this URL exactly, nowhere else)
https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260821_114821_a8ca298f-be2c-4613-a4dd-51b69e16bbde.mp4

The clip is a high-key aerial fly-through: pale clouds, mist, mountains, then darker atmospheric landscape. Full-bleed object-cover background. No poster image. No controls. muted, playsInline, preload="auto". Video is NEVER autoplayed as a timeline; playback position is driven by scroll.

FONT
Load exactly:
<link href="https://db.onlinewebfonts.com/c/95cecf452d3208890088a5b4c19c7ecf?family=Helvetica+Neue+ME" rel="stylesheet">
body font-family: 'Helvetica Neue ME', 'Helvetica Neue', Helvetica, Arial, sans-serif;
html { scroll-behavior: smooth; }
body { overflow-x: hidden; }
Title: "Scroll Tied Video Section"

COLOR
const DARK = '#1D3045'
Navy text on light cloud frames. White text on dark later frames.
No color overlays / gradients on the video. Text sits directly on the video.

PAGE ARCHITECTURE
Outer: relative h-[500vh]  (this is the scroll distance)
Inner sticky: sticky top-0 w-full h-screen overflow-hidden
Inside sticky:
  1 <video> full cover
  2 <canvas width=1920 height=1080> absolute inset-0 object-cover, opacity 1 when frame-bank is live else 0, transition-opacity duration-300. Canvas draws decoded frames so scrubbing is smooth (video.currentTime seeking is fallback only).
  3 Overlay absolute inset-0 pointer-events-none containing Navbar + 3 sequential sections.

SCROLL PROGRESS
p = clamp(0, 1, window.scrollY / (container.offsetHeight - window.innerHeight))
Recompute span on resize and orientationchange.

TEXT SECTIONS ARE SEQUENTIAL (previous fully fades out before next appears)

s1Opacity:
  p < 0.20 → 1
  else → max(0, 1 - (p - 0.20) / 0.08)

s2Opacity:
  p < 0.32 → 0
  p < 0.40 → (p - 0.32) / 0.08
  p < 0.55 → 1
  else → max(0, 1 - (p - 0.55) / 0.08)

s3Opacity:
  p < 0.67 → 0
  p < 0.75 → (p - 0.67) / 0.08
  else → 1

Each section: absolute inset-0, style opacity + transition 'opacity 0.1s ease-out'
Children use Stagger: visible when section opacity > 0.3
Stagger: opacity 0→1, translateY(24px)→0, 0.8s cubic-bezier(0.16,1,0.3,1), delay in ms.

NAVBAR (absolute top, z-50, pointer-events-auto)
Padding: px-6 sm:px-8 md:px-12  pt-8 sm:pt-12 pb-6
flex items-center justify-between
Color flips at p > 0.55: DARK → white (duration-500)

Desktop lg+: left cluster of 5 links, gap-8 xl:gap-10
  VECTRUS ENERGY (active, 2px underline -bottom-3 full width)
  VECTRUS UPSTREAM
  VECTRUS MARKETS
  VECTRUS SYSTEMS
  VECTRUS+
Link style: text-xs tracking-[0.15em] uppercase font-medium, hover:opacity-70
Nav entrance: after 200ms, each link fades/slides from translateY(-12px), opacity 0.6s cubic-bezier(0.16,1,0.3,1), delay i*80+100ms

Right cluster (hidden below sm):
  NEWS (text-xs tracking-[0.2em] uppercase font-medium) + 20px circle filled with current nav color, Info icon size 10 inverted
  MENU label same type (lg+: span, below lg: button that opens overlay)
  Right cluster delay 500ms same entrance

Mobile <lg: hamburger left, 3 bars:
  bar 24x2, 24x2, 16x2, gap 5px, color follows isLight
Hamburger opens full-screen overlay.

MOBILE MENU OVERLAY
fixed inset-0 z-[100], background DARK
open: opacity-100 visible; closed: opacity-0 invisible; duration-500 ease cubic-bezier(0.4,0,0.2,1)
inner panel: closed -translate-y-8, open translate-y-0
Close: top-right px-6 sm:px-8 pt-8 sm:pt-12, 40px circle border-white/30, X 18, hover:border-white
Links centered vertically, px-8 sm:px-12, py-3, text-2xl sm:text-3xl font-light tracking-wide uppercase
active white, others white/60 hover:white
stagger in: delay i*60ms, translateY(20px)→0
Footer: NEWS and CONTACT, text-xs tracking-[0.2em] uppercase text-white/60, px-8 sm:px-12 pb-10
When open, body overflow hidden.

SECTION 1 (hero, left aligned, vertically centered)
px-6 sm:px-8 md:px-20 lg:px-32
H1: "Advancing resources for a cleaner future"
  clamp(2rem,5vw,5rem) font-light uppercase leading-[1.2] color DARK
Subtitle below mt-6: "Sustainable power with purpose"
  text-sm tracking-[0.3em] uppercase color DARK at 90% alpha (#1D304590)
Bottom-right absolute bottom-12 right-6 sm:right-8 md:right-12:
  48px circle button, border DARK 50% alpha, ArrowRight 18, hover:opacity-70
Stagger delays: title 0, subtitle 150, button 300

SECTION 2 (center)
px-6 sm:px-8, flex center
max-w-[900px]
H2 clamp(1.5rem,4.5vw,4.5rem) font-extralight tracking-wide leading-[1.3] text-center uppercase
Copy exactly:
  "We build lasting partnerships with vision " + span color DARK 80% "and precision" + " " + span color DARK 50% "across every frontier"
Right column absolute bottom-16 right-6 sm:right-8 md:right-12, flex-col items-center gap-4:
  48px circle, border DARK 40%, ArrowDown 18
  then mt-4 three dots: 8px solid DARK (active), 6px DARK 40%, 6px DARK 40%, gap-2
  then 40px circle, border DARK 30%, ChevronUp 16, color DARK 80%, mt-2
Stagger delays: headline 0, down 200, dots 350, up 500

SECTION 3 (right aligned, white type — video is dark here)
flex items-center justify-end px-6 sm:px-8 md:px-20 lg:px-32
max-w-2xl text-left
Eyebrow: "Halder | Nordvik"  text-white/60 text-lg tracking-wide mb-4
H2: "Fueling ambition," line break "shaping tomorrow."
  clamp(2rem,4vw,4rem) font-light text-white leading-[1.2] uppercase tracking-wide mb-8
CTA row gap-4:
  "Contact Nordvik" text-sm tracking-[0.3em] text-white/80 uppercase
  40px white filled circle, gray-800 ArrowRight 16, hover:scale-110 duration-300
Stagger delays: 0 / 150 / 300

SMOOTH VIDEO SCROLL — SAME LOGIC (do not replace with GSAP or naive currentTime only)

Hook useVideoScrub(videoSrc):

Constants:
  LERP_TAU = 8
  SNAP = 0.002
  LRU_MAX = 24
  LEAD = 24
  WATCHDOG = 60000 ms

State:
  bank: {ts microseconds, blob webp}[]
  lru: Map<index, ImageBitmap | null>
  current / target times in seconds
  ready, reverted, painted, building, dur

rAF loop every frame:
  dt = min(0.1, deltaSeconds)
  p = getProgress(); setScrollProgress(p)
  if dur > 0:
    target = p * dur
    if prefers-reduced-motion: current = target
    else:
      current += (target - current) * (1 - exp(-dt * LERP_TAU))
      if abs(target-current) < SNAP: current = target
    if ready: draw nearest frame from bank
    else: fallback: if not seeking and abs(video.currentTime - current) > 0.01, set video.currentTime = current

Frame bank (build after window load, skip if reduced-motion or no VideoDecoder):
  fetch the CloudFront mp4 as ArrayBuffer
  parse with MP4Box.createFile(), extract video track, configure VideoDecoder (codec + avcC/hvcC/vpcC/av1C description)
  decode samples as EncodedVideoChunk (key vs delta)
  throttle with LEAD so decode doesn't outrun blob encoding
  each VideoFrame → offscreen canvas → toBlob('image/webp', 0.82) stored with timestamp
  sort bank by ts
  nearestIndex: binary search on timestamps (compare t*1e6)
  warmLRU around i-1..i+2, createImageBitmap, evict oldest when size > LRU_MAX
  first successful canvas paint → canvasLive true (canvas fades in over video)
  if hardware decode fails, retry once with hardwareAcceleration: 'prefer-software'
  60s watchdog → revert to video seeking fallback, hide canvas
  CORS: fetch needs CloudFront CORS; video element still works as fallback if decode fails

Video element still renders underneath until canvas is live.

DO NOT
- Add extra brands, logos, cookie widgets, or “scroll down to discover”
- Change copy or nav labels
- Play the video with play()
- Use a different video host or local file
- Skip mobile overlay or hamburger
- Skip lerp (jumping frames = wrong)

DELIVER
Working Vite React TS app: index.html, App.tsx, useVideoScrub.ts, index.css, mp4box.d.ts, vite alias @, lucide-react + mp4box installed. Pixel-faithful to the spec above.
```
二、色彩系统
2.1 核心颜色
角色	色值	用途
Primary Dark	#1D3045	主要标题、重要文字、深色背景
Primary Darker	#0A1A2B	更深色背景、Footer
Blue Accent	#4A90D9	强调色、链接、按钮、选中状态
White	#FFFFFF	白色文字、卡片背景
Text Light	rgba(255,255,255,0.7)	辅助文字（深色背景上）
Text Muted	rgba(29,48,69,0.7)	次要文字（浅色背景上）
Background	#F5F7FA	页面浅色背景
2.2 颜色应用规则
text
浅色区域 (白色背景)：
  - 标题: #1D3045
  - 正文: rgba(29,48,69,0.8)
  - 辅助: rgba(29,48,69,0.5)

深色区域 (深色背景)：
  - 标题: #FFFFFF
  - 正文: rgba(255,255,255,0.85)
  - 辅助: rgba(255,255,255,0.55)
三、字体系统
3.1 字体家族
用途	字体	说明
Logo / 强调词	'Instrument Serif', serif	斜体，仅用于品牌标识和少量强调
正文 / 标题	'Helvetica Neue', -apple-system, sans-serif	干净、现代、专业感
3.2 字号规范
层级	大小	用途
Logo	clamp(1.8rem, 3vw, 2.8rem)	品牌标志
H1 (Hero)	clamp(2.5rem, 6vw, 5rem)	页面主标题，font-weight: 300
H2 (板块)	clamp(1.8rem, 4vw, 3rem)	板块标题，font-weight: 300-400
H3 (卡片)	1.05rem - 1.25rem	卡片标题
Body	0.875rem - 1rem	正文内容
Small	0.7rem - 0.8rem	标签、辅助信息
3.3 文字间距
标题: letter-spacing: 0.02em - 0.05em（略微宽松）

导航链接: letter-spacing: 0.15em - 0.2em（大写时）

正文: letter-spacing: normal

四、核心设计原则
4.1 极简主义
text
- 背景干净，无复杂纹理
- 内容直接放在背景上，不加多余卡片框（除非必要）
- 大留白，呼吸感
- 每屏只聚焦一个核心信息
4.2 滚动驱动叙事
text
- 页面内容随滚动渐进显示
- 文字分阶段淡入淡出（参考三个section的透明度逻辑）
- 背景色随滚动变化（浅→深）
- 文字颜色随背景亮度反转（深色文字→白色文字）
4.3 沉浸式视觉
text
- 如果使用视频背景：全屏、无控制条、滚动驱动播放
- 如果使用静态背景：渐变或抽象几何，不抢文字风头
- 文字直接叠加在背景上，无遮罩层
五、组件样式规范
5.1 按钮
主要按钮 (Primary)

css
.btn-primary {
    background: #1D3045;
    color: white;
    padding: 0.75rem 2rem;
    border-radius: 9999px;
    font-weight: 500;
    font-size: 0.875rem;
    letter-spacing: 0.01em;
    transition: 0.3s ease;
    border: none;
    cursor: pointer;
}
.btn-primary:hover {
    transform: translateY(-2px);
    opacity: 0.85;
}
次要按钮 (Secondary)

css
.btn-secondary {
    background: transparent;
    color: #1D3045;
    padding: 0.75rem 2rem;
    border-radius: 9999px;
    font-weight: 500;
    font-size: 0.875rem;
    border: 1px solid rgba(29,48,69,0.2);
    transition: 0.3s ease;
    cursor: pointer;
}
.btn-secondary:hover {
    border-color: #1D3045;
    background: rgba(29,48,69,0.05);
}
深色背景上的按钮

css
.btn-primary-light {
    background: white;
    color: #1D3045;
    /* 其他同 .btn-primary */
}
5.2 导航栏
css
.navbar {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    z-index: 50;
    padding: 2rem 3rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
    pointer-events: auto;
    transition: color 0.5s ease;
}

.navbar .logo {
    font-family: 'Instrument Serif', serif;
    font-style: italic;
    font-size: 1.8rem;
}

.nav-links {
    display: flex;
    gap: 2rem;
    font-size: 0.7rem;
    letter-spacing: 0.15em;
    text-transform: uppercase;
    font-weight: 500;
}

.nav-links a {
    text-decoration: none;
    color: inherit;
    opacity: 0.8;
    transition: opacity 0.3s;
    position: relative;
}
.nav-links a:hover { opacity: 1; }
.nav-links a.active::after {
    content: '';
    position: absolute;
    bottom: -6px;
    left: 0;
    right: 0;
    height: 2px;
    background: currentColor;
}
5.3 卡片
css
.card {
    background: white;
    border-radius: 16px;
    padding: 1.75rem;
    box-shadow: 0 4px 20px rgba(29,48,69,0.06);
    transition: 0.3s ease;
    border: 1px solid rgba(255,255,255,0.8);
}
.card:hover {
    transform: translateY(-4px);
    box-shadow: 0 8px 40px rgba(29,48,69,0.10);
}
5.4 徽章
css
.badge {
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    padding: 0.3rem 1rem;
    border-radius: 9999px;
    font-size: 0.7rem;
    font-weight: 500;
    letter-spacing: 0.05em;
    background: rgba(74,144,217,0.10);
    color: #4A90D9;
    border: 1px solid rgba(74,144,217,0.15);
}

.badge .dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: #4A90D9;
    animation: pulse 2s infinite;
}
六、页面布局结构
6.1 整体结构
text
┌──────────────────────────────────────────────────────┐
│  NAVBAR (绝对定位)                                 │
│  Logo + 导航链接 (左)  |  工具按钮 (右)            │
├──────────────────────────────────────────────────────┤
│                                                      │
│  SECTION 1 — HERO (全屏)                           │
│  ├── 大标题 (Advancing resources...)               │
│  ├── 副标题 (Sustainable power...)                │
│  └── 右下角箭头按钮                                │
│                                                      │
│  SECTION 2 — 叙事 (全屏，滚动淡入)                │
│  ├── 居中大标题 (We build lasting...)             │
│  └── 右侧控制组 (箭头+点)                         │
│                                                      │
│  SECTION 3 — CTA (全屏，滚动淡入)                 │
│  ├── 右对齐标题 (Fueling ambition...)             │
│  ├── 副标题 (Halder | Nordvik)                    │
│  └── CTA按钮组                                    │
│                                                      │
│  [页面内容继续...]                                  │
│                                                      │
├──────────────────────────────────────────────────────┤
│  FOOTER                                            │
└──────────────────────────────────────────────────────┘
6.2 滚动行为
text
滚动进度 p = scrollY / (总高度 - 视口高度)

Section 1 透明度:
  p < 0.20 → 1
  p < 0.28 → 线性淡出到 0

Section 2 透明度:
  p < 0.32 → 0
  p < 0.40 → 线性淡入到 1
  p < 0.55 → 1
  p < 0.63 → 线性淡出到 0

Section 3 透明度:
  p < 0.67 → 0
  p < 0.75 → 线性淡入到 1
  p > 0.75 → 1

导航文字颜色翻转: p > 0.55 时变为白色
七、动画规范
7.1 入场动画 (Stagger)
css
@keyframes fadeSlideUp {
    0% { opacity: 0; transform: translateY(24px); }
    100% { opacity: 1; transform: translateY(0); }
}

.stagger-item {
    opacity: 0;
    animation: fadeSlideUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
}

/* 延迟阶梯 */
.stagger-1 { animation-delay: 0.05s; }
.stagger-2 { animation-delay: 0.15s; }
.stagger-3 { animation-delay: 0.25s; }
/* ... 以此类推，每次递增 0.1s */
7.2 触发条件
元素进入视口（IntersectionObserver，threshold: 0.1）

或父级 Section 透明度 > 0.3 时触发

7.3 交互动画
交互	动画
按钮悬停	transform: translateY(-2px) + opacity变化
卡片悬停	transform: translateY(-4px) + 阴影加深
链接悬停	opacity: 0.7
图标悬停	scale(1.05)
八、各板块设计规范
8.1 Hero Section
元素	规范
背景	渐变 #F5F7FA → #E8EEF5 或全屏视频
对齐	左对齐 (参考Scroll Tied) 或居中 (参考Lumora)
标题	大号 (5vw)，font-weight: 300，letter-spacing: 0.02em
副标题	小号，大写，letter-spacing: 0.3em
CTA	主要按钮 + 次要按钮组合
8.2 Features Grid (六个功能卡片)
元素	规范
布局	3列网格 → 2列 → 1列 (响应式)
卡片	白色背景，悬停上浮，带右箭头指示点击
图标	48px 圆底，#E8F0FE 背景，蓝色图标
链接	每个卡片是 <a> 链接，跳转到对应页面
8.3 滚动叙事区
元素	规范
布局	左右2列 (或上下)
标题	大号，含斜体强调词
视觉	时间线/进度条/里程碑图示
文字	随滚动淡入淡出
8.4 Testimonials
元素	规范
标题	含斜体强调词
卡片	白色，引号标记
作者	头像 + 姓名 + 角色
九、响应式断点
断点	宽度	调整
Mobile	< 640px	单列，文字缩小，汉堡菜单
Tablet	641px - 1024px	2列网格，适中字号
Desktop	> 1024px	完整布局，大字号，内联导航
十、修改指令（给AI）
10.1 全局修改
text
□ 将主色改为 #3873b6ff 和 #6da1d8ff
□ 引入 Instrument Serif 字体用于 Logo
□ 正文使用 Helvetica Neue / system-ui
□ 所有页面标题 font-weight: 300-400
□ 添加滚动驱动的淡入淡出效果
□ 卡片添加悬停上浮动画
□ 按钮添加多层阴影 (参考Viktor Oddy)
□ 底部添加固定导航栏
10.2 文本替换
原文本	替换为
学习/知识类	健康管理/宠物护理
用户/学习者	宠主/宠物家长
课程/知识点	健康记录/疫苗/驱虫
AI学习建议	AI健康诊断
社区问答	宠物社区
10.3 功能卡片 (六个页面入口)
text
1. 宠物档案 → /pets → 图标 📋
2. 健康追踪 → /health-records → 图标 📊
3. AI健康助手 → /ai-diagnosis → 图标 🤖
4. 智能提醒 → /reminders → 图标 🔔
5. 兽医服务 → /vets → 图标 🏥
6. 宠物社区 → /community → 图标 💬

十一、首页排版参考
```
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>PetHealth · 宠物健康管家</title>

    <!-- ===== Fonts ===== -->
    <link rel="preconnect" href="https://fonts.googleapis.com" />
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
    <link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Inter:opsz,wght@14..32,300;14..32,400;14..32,500;14..32,600;14..32,700&display=swap" rel="stylesheet" />

    <!-- ===== Lucide Icons ===== -->
    <script src="https://unpkg.com/lucide@latest"></script>

    <style>
        /* ============================================================
                   CSS VARIABLES — 蓝白灰专业医疗基调
                   ============================================================ */
        :root {
            --color-primary: #0D212C;
            --color-primary-light: #1D3045;
            --color-primary-dark: #051A24;

            --color-blue: #4A90D9;
            --color-blue-light: #6BA8E8;
            --color-blue-pale: #E8F0FE;

            --color-white: #FFFFFF;
            --color-off-white: #F7FAFC;
            --color-gray-50: #F4F7FA;
            --color-gray-100: #EDF2F7;
            --color-gray-200: #E2E8F0;
            --color-gray-300: #CBD5E0;
            --color-gray-400: #A0AEC0;
            --color-gray-500: #718096;
            --color-gray-600: #4A5568;
            --color-gray-700: #2D3748;

            --color-text: #0D212C;
            --color-text-muted: #4A5568;
            --color-text-light: #718096;

            --color-bg: #F4F7FA;

            --radius-sm: 8px;
            --radius-md: 12px;
            --radius-lg: 20px;
            --radius-xl: 28px;
            --radius-full: 9999px;

            --shadow-sm: 0 1px 3px rgba(13, 33, 44, 0.06);
            --shadow-md: 0 4px 16px rgba(13, 33, 44, 0.08);
            --shadow-lg: 0 8px 32px rgba(13, 33, 44, 0.10);
            --shadow-xl: 0 16px 48px rgba(13, 33, 44, 0.12);

            --transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1);
            --transition-slow: 0.6s cubic-bezier(0.4, 0, 0.2, 1);

            --font-sans: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            --font-serif: 'Instrument Serif', serif;
        }

        /* ============================================================
                   RESET & BASE
                   ============================================================ */
        *,
        *::before,
        *::after {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        html {
            scroll-behavior: smooth;
        }

        body {
            font-family: var(--font-sans);
            background: var(--color-bg);
            color: var(--color-text);
            line-height: 1.6;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }

        /* ============================================================
                   GLASS / LIQUID GLASS 效果
                   ============================================================ */
        .glass {
            background: rgba(255, 255, 255, 0.6);
            backdrop-filter: blur(12px);
            -webkit-backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.4);
            box-shadow: var(--shadow-sm);
        }

        .glass-strong {
            background: rgba(255, 255, 255, 0.75);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid rgba(255, 255, 255, 0.5);
            box-shadow: var(--shadow-md);
        }

        /* ============================================================
                   BUTTONS
                   ============================================================ */
        .btn-primary {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            background: var(--color-primary-dark);
            color: white;
            font-weight: 500;
            font-size: 0.875rem;
            padding: 0.75rem 1.75rem;
            border: none;
            border-radius: var(--radius-full);
            cursor: pointer;
            transition: var(--transition);
            box-shadow:
                0 1px 2px 0 rgba(5, 26, 36, 0.10),
                0 4px 4px 0 rgba(5, 26, 36, 0.09),
                0 9px 6px 0 rgba(5, 26, 36, 0.05),
                inset 0 2px 8px 0 rgba(255, 255, 255, 0.3);
            text-decoration: none;
            letter-spacing: 0.01em;
        }

        .btn-primary:hover {
            transform: translateY(-2px);
            box-shadow:
                0 4px 8px 0 rgba(5, 26, 36, 0.15),
                0 12px 20px 0 rgba(5, 26, 36, 0.10),
                inset 0 2px 8px 0 rgba(255, 255, 255, 0.3);
        }

        .btn-secondary {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            background: var(--color-white);
            color: var(--color-text);
            font-weight: 500;
            font-size: 0.875rem;
            padding: 0.75rem 1.75rem;
            border: none;
            border-radius: var(--radius-full);
            cursor: pointer;
            transition: var(--transition);
            box-shadow: 0 0 0 0.5px rgba(0, 0, 0, 0.05), 0 4px 30px rgba(0, 0, 0, 0.06);
            text-decoration: none;
        }

        .btn-secondary:hover {
            transform: translateY(-2px);
            box-shadow: 0 0 0 0.5px rgba(0, 0, 0, 0.05), 0 8px 40px rgba(0, 0, 0, 0.10);
        }

        .btn-ghost {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            background: transparent;
            color: var(--color-text-muted);
            font-weight: 500;
            font-size: 0.875rem;
            padding: 0.75rem 1.5rem;
            border: 1px solid var(--color-gray-200);
            border-radius: var(--radius-full);
            cursor: pointer;
            transition: var(--transition);
            text-decoration: none;
        }

        .btn-ghost:hover {
            background: var(--color-gray-100);
            border-color: var(--color-gray-300);
        }

        /* ============================================================
                   ANIMATIONS
                   ============================================================ */
        @keyframes fadeInUp {
            0% {
                opacity: 0;
                transform: translateY(30px);
            }
            100% {
                opacity: 1;
                transform: translateY(0);
            }
        }

        @keyframes fadeIn {
            0% {
                opacity: 0;
            }
            100% {
                opacity: 1;
            }
        }

        @keyframes pulse-glow {
            0%,
            100% {
                box-shadow: 0 0 0 0 rgba(74, 144, 217, 0.3);
            }
            50% {
                box-shadow: 0 0 0 16px rgba(74, 144, 217, 0);
            }
        }

        .animate-fade-in-up {
            opacity: 0;
            animation: fadeInUp 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards;
        }

        .animate-fade-in {
            opacity: 0;
            animation: fadeIn 0.8s ease-out forwards;
        }

        .stagger-1 {
            animation-delay: 0.05s;
        }
        .stagger-2 {
            animation-delay: 0.15s;
        }
        .stagger-3 {
            animation-delay: 0.25s;
        }
        .stagger-4 {
            animation-delay: 0.35s;
        }
        .stagger-5 {
            animation-delay: 0.45s;
        }
        .stagger-6 {
            animation-delay: 0.55s;
        }
        .stagger-7 {
            animation-delay: 0.65s;
        }
        .stagger-8 {
            animation-delay: 0.75s;
        }

        /* ============================================================
                   LAYOUT UTILITIES
                   ============================================================ */
        .container {
            max-width: 1280px;
            margin: 0 auto;
            padding: 0 1.5rem;
        }

        .container-narrow {
            max-width: 880px;
            margin: 0 auto;
            padding: 0 1.5rem;
        }

        .section-padding {
            padding: 5rem 0;
        }

        .section-padding-sm {
            padding: 3rem 0;
        }

        /* ============================================================
                   COMPONENT: Hero Section
                   ============================================================ */
        .hero {
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            padding: 2rem 1.5rem 4rem;
            position: relative;
            background: linear-gradient(165deg, #F4F7FA 0%, #E8F0FE 40%, #D6E4F5 100%);
            overflow: hidden;
        }

        /* 装饰性抽象几何背景 */
        .hero::before {
            content: '';
            position: absolute;
            top: -30%;
            right: -10%;
            width: 500px;
            height: 500px;
            background: radial-gradient(circle, rgba(74, 144, 217, 0.08) 0%, transparent 70%);
            border-radius: 50%;
            pointer-events: none;
        }

        .hero::after {
            content: '';
            position: absolute;
            bottom: -20%;
            left: -10%;
            width: 400px;
            height: 400px;
            background: radial-gradient(circle, rgba(74, 144, 217, 0.06) 0%, transparent 70%);
            border-radius: 50%;
            pointer-events: none;
        }

        .hero-content {
            position: relative;
            z-index: 1;
            max-width: 720px;
        }

        .hero-badge {
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            padding: 0.4rem 1.2rem;
            border-radius: var(--radius-full);
            font-size: 0.75rem;
            font-weight: 500;
            color: var(--color-blue);
            background: rgba(74, 144, 217, 0.10);
            border: 1px solid rgba(74, 144, 217, 0.15);
            margin-bottom: 1.5rem;
            letter-spacing: 0.02em;
        }

        .hero-badge .dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: var(--color-blue);
            animation: pulse-glow 2s infinite;
        }

        .hero-logo {
            font-family: var(--font-serif);
            font-size: 3.5rem;
            font-weight: 400;
            font-style: italic;
            color: var(--color-primary-dark);
            letter-spacing: -0.02em;
            line-height: 1.1;
            margin-bottom: 0.5rem;
        }

        .hero-logo span {
            color: var(--color-blue);
        }

        .hero-tagline {
            font-size: 0.875rem;
            color: var(--color-text-muted);
            letter-spacing: 0.15em;
            text-transform: uppercase;
            font-weight: 400;
            margin-bottom: 0.5rem;
        }

        .hero-title {
            font-size: clamp(2.2rem, 6vw, 4rem);
            font-weight: 600;
            line-height: 1.1;
            color: var(--color-primary);
            letter-spacing: -0.02em;
            margin-bottom: 1.25rem;
        }

        .hero-title .highlight {
            font-family: var(--font-serif);
            font-style: italic;
            color: var(--color-blue);
            font-weight: 400;
        }

        .hero-desc {
            font-size: 1.05rem;
            color: var(--color-text-muted);
            max-width: 540px;
            margin: 0 auto 2rem;
            line-height: 1.7;
        }

        .hero-actions {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: 0.75rem;
        }

        .hero-stats {
            display: flex;
            gap: 2.5rem;
            justify-content: center;
            margin-top: 3rem;
            padding-top: 2rem;
            border-top: 1px solid rgba(13, 33, 44, 0.06);
        }

        .hero-stats-item {
            text-align: center;
        }

        .hero-stats-item .number {
            font-size: 1.25rem;
            font-weight: 700;
            color: var(--color-primary);
        }

        .hero-stats-item .label {
            font-size: 0.75rem;
            color: var(--color-text-light);
            display: block;
            margin-top: 0.1rem;
        }

        /* ============================================================
                   COMPONENT: Feature Cards (Grid) — 六个页面入口
                   ============================================================ */
        .features-grid {
            display: grid;
            grid-template-columns: repeat(3, 1fr);
            gap: 1.5rem;
            margin-top: 1rem;
        }

        @media (max-width: 1024px) {
            .features-grid {
                grid-template-columns: repeat(2, 1fr);
            }
        }

        @media (max-width: 640px) {
            .features-grid {
                grid-template-columns: 1fr;
            }
        }

        .feature-card {
            background: var(--color-white);
            border-radius: var(--radius-lg);
            padding: 1.75rem 1.5rem;
            box-shadow: var(--shadow-sm);
            transition: var(--transition);
            border: 1px solid rgba(255, 255, 255, 0.8);
            text-align: left;
            text-decoration: none;
            color: inherit;
            display: block;
            cursor: pointer;
            position: relative;
            overflow: hidden;
        }

        .feature-card::after {
            content: '→';
            position: absolute;
            right: 1.5rem;
            bottom: 1.5rem;
            font-size: 1.25rem;
            color: var(--color-gray-300);
            transition: var(--transition);
        }

        .feature-card:hover {
            transform: translateY(-6px);
            box-shadow: var(--shadow-lg);
            border-color: var(--color-blue);
        }

        .feature-card:hover::after {
            color: var(--color-blue);
            transform: translateX(4px);
        }

        .feature-card .icon-wrap {
            width: 48px;
            height: 48px;
            border-radius: var(--radius-sm);
            background: var(--color-blue-pale);
            color: var(--color-blue);
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 0.75rem;
            font-size: 1.25rem;
        }

        .feature-card h3 {
            font-size: 1.05rem;
            font-weight: 600;
            color: var(--color-primary);
            margin-bottom: 0.3rem;
        }

        .feature-card p {
            font-size: 0.875rem;
            color: var(--color-text-muted);
            line-height: 1.5;
        }

        /* ============================================================
                   COMPONENT: Scroll Story Section
                   ============================================================ */
        .scroll-story {
            background: var(--color-white);
            padding: 5rem 0;
            position: relative;
            overflow: hidden;
        }

        .scroll-story-inner {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 4rem;
            align-items: center;
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 1.5rem;
        }

        @media (max-width: 768px) {
            .scroll-story-inner {
                grid-template-columns: 1fr;
                gap: 2.5rem;
            }
        }

        .scroll-story-text h2 {
            font-size: clamp(1.8rem, 4vw, 2.8rem);
            font-weight: 600;
            color: var(--color-primary);
            line-height: 1.2;
            margin-bottom: 1rem;
        }

        .scroll-story-text h2 .serif {
            font-family: var(--font-serif);
            font-style: italic;
            color: var(--color-blue);
            font-weight: 400;
        }

        .scroll-story-text p {
            color: var(--color-text-muted);
            line-height: 1.7;
            margin-bottom: 1rem;
        }

        .scroll-story-visual {
            position: relative;
            background: var(--color-gray-100);
            border-radius: var(--radius-lg);
            aspect-ratio: 4/3;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, #E8F0FE 0%, #D6E4F5 100%);
        }

        .scroll-story-visual .timeline-mock {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            width: 80%;
            max-width: 320px;
        }

        .timeline-mock .step {
            display: flex;
            align-items: center;
            gap: 0.75rem;
            padding: 0.6rem 1rem;
            background: rgba(255, 255, 255, 0.7);
            backdrop-filter: blur(4px);
            border-radius: var(--radius-sm);
            border-left: 3px solid var(--color-blue);
            transition: var(--transition);
        }

        .timeline-mock .step .num {
            font-size: 0.7rem;
            font-weight: 700;
            color: var(--color-blue);
            background: var(--color-white);
            width: 22px;
            height: 22px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .timeline-mock .step .label {
            font-size: 0.8rem;
            color: var(--color-text);
            font-weight: 500;
        }

        .timeline-mock .step .sub {
            font-size: 0.7rem;
            color: var(--color-text-light);
            margin-left: auto;
        }

        /* ============================================================
                   COMPONENT: Testimonials
                   ============================================================ */
        .testimonials {
            background: var(--color-bg);
            padding: 4rem 0;
        }

        .testimonials-header {
            display: flex;
            align-items: flex-end;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 1rem;
            margin-bottom: 2.5rem;
        }

        .testimonials-header h2 {
            font-size: clamp(1.8rem, 3.5vw, 2.8rem);
            font-weight: 600;
            color: var(--color-primary);
            line-height: 1.2;
        }

        .testimonials-header h2 .serif {
            font-family: var(--font-serif);
            font-style: italic;
            color: var(--color-blue);
            font-weight: 400;
        }

        .testimonials-header .rating {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            color: var(--color-text-muted);
            font-size: 0.875rem;
        }

        .testimonials-header .rating .stars {
            color: #f59e0b;
            letter-spacing: 1px;
        }

        .testimonial-cards {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
            gap: 1.5rem;
        }

        .testimonial-card {
            background: var(--color-white);
            border-radius: var(--radius-lg);
            padding: 1.75rem;
            box-shadow: var(--shadow-sm);
            transition: var(--transition);
            border: 1px solid rgba(255, 255, 255, 0.8);
        }

        .testimonial-card:hover {
            transform: translateY(-4px);
            box-shadow: var(--shadow-md);
        }

        .testimonial-card .quote-mark {
            color: var(--color-blue);
            font-size: 1.5rem;
            font-family: var(--font-serif);
            line-height: 1;
            margin-bottom: 0.5rem;
        }

        .testimonial-card blockquote {
            font-size: 0.925rem;
            color: var(--color-text);
            line-height: 1.6;
            margin-bottom: 1rem;
            font-style: normal;
        }

        .testimonial-card .author {
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .testimonial-card .author .avatar {
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: var(--color-gray-200);
            flex-shrink: 0;
            overflow: hidden;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.2rem;
        }

        .testimonial-card .author .info .name {
            font-weight: 600;
            font-size: 0.875rem;
            color: var(--color-primary);
        }

        .testimonial-card .author .info .role {
            font-size: 0.75rem;
            color: var(--color-text-light);
        }

        /* ============================================================
                   COMPONENT: Projects / 健康场景案例
                   ============================================================ */
        .projects {
            background: var(--color-white);
            padding: 5rem 0;
        }

        .project-item {
            margin-bottom: 4rem;
        }

        .project-item:last-child {
            margin-bottom: 0;
        }

        .project-item .meta {
            max-width: 600px;
            margin-bottom: 0.75rem;
        }

        .project-item .meta h3 {
            font-family: var(--font-serif);
            font-size: clamp(1.5rem, 3vw, 2.2rem);
            font-weight: 400;
            color: var(--color-primary);
            margin-bottom: 0.2rem;
        }

        .project-item .meta p {
            font-size: 0.9rem;
            color: var(--color-text-muted);
        }

        .project-item .image-wrap {
            border-radius: var(--radius-lg);
            overflow: hidden;
            box-shadow: var(--shadow-md);
            background: var(--color-gray-200);
            aspect-ratio: 16/9;
            display: flex;
            align-items: center;
            justify-content: center;
            background: linear-gradient(135deg, #E8F0FE, #D6E4F5);
            font-size: 0.875rem;
            color: var(--color-text-light);
        }

        /* ============================================================
                   COMPONENT: Partner / CTA
                   ============================================================ */
        .partner-cta {
            background: var(--color-white);
            padding: 4rem 1.5rem;
            text-align: center;
            border-radius: var(--radius-xl);
            max-width: 1200px;
            margin: 0 auto 3rem;
            box-shadow: var(--shadow-sm);
            border: 1px solid rgba(255, 255, 255, 0.8);
        }

        .partner-cta h2 {
            font-family: var(--font-serif);
            font-size: clamp(2.5rem, 6vw, 4.5rem);
            font-weight: 400;
            color: var(--color-primary);
            margin-bottom: 1.5rem;
            line-height: 1.1;
        }

        .partner-cta .actions {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: 1rem;
        }

        /* ============================================================
                   COMPONENT: Footer
                   ============================================================ */
        .footer {
            background: var(--color-white);
            padding: 3rem 0 1.5rem;
            border-top: 1px solid var(--color-gray-200);
        }

        .footer-inner {
            display: flex;
            flex-wrap: wrap;
            justify-content: space-between;
            align-items: center;
            gap: 1.5rem;
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 1.5rem;
        }

        .footer-links {
            display: flex;
            flex-wrap: wrap;
            gap: 2rem;
        }

        .footer-links a {
            color: var(--color-text-muted);
            text-decoration: none;
            font-size: 0.875rem;
            transition: var(--transition);
        }

        .footer-links a:hover {
            color: var(--color-primary);
        }

        .footer-bottom {
            max-width: 1200px;
            margin: 2rem auto 0;
            padding: 1rem 1.5rem 0;
            border-top: 1px solid var(--color-gray-100);
            display: flex;
            justify-content: space-between;
            flex-wrap: wrap;
            gap: 0.5rem;
            font-size: 0.75rem;
            color: var(--color-text-light);
        }

        /* ============================================================
                   COMPONENT: Floating Bottom Nav
                   ============================================================ */
        .bottom-nav {
            position: fixed;
            bottom: 1.5rem;
            left: 50%;
            transform: translateX(-50%);
            z-index: 100;
            background: var(--color-white);
            backdrop-filter: blur(16px);
            -webkit-backdrop-filter: blur(16px);
            border: 1px solid rgba(255, 255, 255, 0.6);
            border-radius: var(--radius-full);
            padding: 0.4rem 0.6rem 0.4rem 1.2rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
            box-shadow:
                0 2px 12px rgba(13, 33, 44, 0.08),
                0 8px 32px rgba(13, 33, 44, 0.06);
            transition: var(--transition);
        }

        .bottom-nav .brand-mark {
            font-family: var(--font-serif);
            font-size: 1.3rem;
            font-weight: 600;
            color: var(--color-primary-dark);
            margin-right: 0.25rem;
        }

        .bottom-nav .nav-links {
            display: flex;
            align-items: center;
            gap: 0.25rem;
        }

        .bottom-nav .nav-links a {
            padding: 0.4rem 0.8rem;
            font-size: 0.75rem;
            color: var(--color-text-muted);
            text-decoration: none;
            border-radius: var(--radius-full);
            transition: var(--transition);
            font-weight: 500;
        }

        .bottom-nav .nav-links a:hover {
            background: var(--color-gray-100);
            color: var(--color-primary);
        }

        .bottom-nav .nav-links a.active {
            background: var(--color-primary-dark);
            color: white;
        }

        .bottom-nav .btn-primary {
            padding: 0.5rem 1.25rem;
            font-size: 0.8rem;
        }

        @media (max-width: 640px) {
            .bottom-nav .nav-links a:not(.active) {
                display: none;
            }
            .bottom-nav {
                padding: 0.3rem 0.4rem 0.3rem 0.8rem;
            }
            .bottom-nav .brand-mark {
                font-size: 1rem;
            }
            .bottom-nav .btn-primary {
                padding: 0.4rem 1rem;
                font-size: 0.7rem;
            }
        }

        /* ============================================================
                   RESPONSIVE
                   ============================================================ */
        @media (max-width: 768px) {
            .hero-logo {
                font-size: 2.5rem;
            }
            .hero-stats {
                gap: 1.5rem;
                flex-wrap: wrap;
            }
            .testimonial-cards {
                grid-template-columns: 1fr;
            }
            .footer-inner {
                flex-direction: column;
                text-align: center;
            }
            .footer-links {
                justify-content: center;
            }
        }

        @media (max-width: 480px) {
            .hero-actions {
                flex-direction: column;
                width: 100%;
            }
            .hero-actions .btn-primary,
            .hero-actions .btn-secondary {
                width: 100%;
                justify-content: center;
            }
            .bottom-nav .nav-links a:not(.active) {
                display: none;
            }
        }

        /* ============================================================
                   SECTION TITLE HELPER
                   ============================================================ */
        .section-label {
            font-size: 0.7rem;
            font-weight: 600;
            letter-spacing: 0.15em;
            text-transform: uppercase;
            color: var(--color-blue);
            margin-bottom: 0.5rem;
        }

        .section-title {
            font-size: clamp(1.8rem, 4vw, 3rem);
            font-weight: 600;
            color: var(--color-primary);
            line-height: 1.2;
            margin-bottom: 0.5rem;
        }

        .section-title .serif {
            font-family: var(--font-serif);
            font-style: italic;
            color: var(--color-blue);
            font-weight: 400;
        }

        .section-desc {
            color: var(--color-text-muted);
            max-width: 600px;
            line-height: 1.7;
        }
    </style>
</head>
<body>

    <!-- ============================================================
    HERO SECTION — 品牌叙事与行动召唤
    ============================================================ -->
    <section class="hero">
        <div class="hero-content">
            <!-- Badge with live indicator -->
            <div class="hero-badge animate-fade-in-up stagger-1">
                <span class="dot"></span>
                Trusted by 10,000+ pet parents
            </div>

            <!-- Logo -->
            <h1 class="hero-logo animate-fade-in-up stagger-2">
                Pet<span>Health</span>
            </h1>

            <!-- Tagline -->
            <p class="hero-tagline animate-fade-in-up stagger-3">
                — 宠物健康管家 —
            </p>

            <!-- Main Title -->
            <h2 class="hero-title animate-fade-in-up stagger-3">
                All your pet's health,<br />
                <span class="highlight">in one place.</span>
            </h2>

            <!-- Description -->
            <p class="hero-desc animate-fade-in-up stagger-4">
                一站式宠物健康管理平台。帮您记录宠物健康数据、提醒就医、AI 辅助诊断，
                让每一只毛孩子都得到最好的照顾。
            </p>

            <!-- Actions -->
            <div class="hero-actions animate-fade-in-up stagger-5">
                <a href="#" class="btn-primary">
                    🐾 开始管理宠物健康
                </a>
                <a href="#" class="btn-secondary">
                    了解更多 →
                </a>
            </div>

            <!-- Trust Stats -->
            <div class="hero-stats animate-fade-in-up stagger-6">
                <div class="hero-stats-item">
                    <div class="number">10K+</div>
                    <span class="label">宠物档案</span>
                </div>
                <div class="hero-stats-item">
                    <div class="number">8K+</div>
                    <span class="label">健康记录</span>
                </div>
                <div class="hero-stats-item">
                    <div class="number">4.9★</div>
                    <span class="label">用户评分</span>
                </div>
                <div class="hero-stats-item">
                    <div class="number">99%</div>
                    <span class="label">满意度</span>
                </div>
            </div>
        </div>
    </section>

    <!-- ============================================================
    FEATURES GRID — 六个页面入口 (可点击跳转)
    ============================================================ -->
    <section class="section-padding" style="background: var(--color-white);">
        <div class="container">
            <div style="text-align: center; margin-bottom: 2.5rem;">
                <p class="section-label animate-fade-in-up stagger-1">核心功能</p>
                <h2 class="section-title animate-fade-in-up stagger-2">
                    全方位守护 <span class="serif">毛孩子</span> 的健康
                </h2>
                <p class="section-desc animate-fade-in-up stagger-3" style="margin: 0.5rem auto 0;">
                    六大模块，覆盖宠物健康管理的每一个环节
                </p>
            </div>

            <div class="features-grid">
                <!-- 1. 宠物档案 -->
                <a href="/pets" class="feature-card animate-fade-in-up stagger-3">
                    <div class="icon-wrap">📋</div>
                    <h3>宠物档案</h3>
                    <p>完整记录疫苗、驱虫、体检、就医史</p>
                </a>

                <!-- 2. 健康追踪 -->
                <a href="/health-records" class="feature-card animate-fade-in-up stagger-4">
                    <div class="icon-wrap">📊</div>
                    <h3>健康追踪</h3>
                    <p>体重、体温、饮食、运动量趋势</p>
                </a>

                <!-- 3. AI 健康助手 -->
                <a href="/ai-diagnosis" class="feature-card animate-fade-in-up stagger-5">
                    <div class="icon-wrap">🤖</div>
                    <h3>AI 健康助手</h3>
                    <p>症状描述 → AI 初步诊断</p>
                </a>

                <!-- 4. 智能提醒 -->
                <a href="/reminders" class="feature-card animate-fade-in-up stagger-6">
                    <div class="icon-wrap">🔔</div>
                    <h3>智能提醒</h3>
                    <p>疫苗到期、驱虫提醒</p>
                </a>

                <!-- 5. 兽医服务 -->
                <a href="/vets" class="feature-card animate-fade-in-up stagger-7">
                    <div class="icon-wrap">🏥</div>
                    <h3>兽医服务</h3>
                    <p>附近兽医查询、在线预约</p>
                </a>

                <!-- 6. 宠物社区 -->
                <a href="/community" class="feature-card animate-fade-in-up stagger-8">
                    <div class="icon-wrap">💬</div>
                    <h3>宠物社区</h3>
                    <p>与千万宠主交流经验</p>
                </a>
            </div>
        </div>
    </section>

    <!-- ============================================================
    SCROLL STORY — 沉浸式旅程叙事
    ============================================================ -->
    <section class="scroll-story">
        <div class="scroll-story-inner">
            <div class="scroll-story-text animate-fade-in-up stagger-2">
                <p class="section-label">健康旅程</p>
                <h2>
                    从 <span class="serif">出生</span> 到 <span class="serif">成年</span>，<br />
                    我们一路相伴
                </h2>
                <p>
                    为每只宠物建立完整的健康时间线。疫苗接种、定期体检、驱虫计划 ——
                    所有关键节点都在掌控之中。
                </p>
                <p style="margin-bottom: 1.5rem;">
                    每一只毛孩子的健康历程，都值得被完整记录。
                </p>
                <a href="/pets" class="btn-secondary">查看我的宠物档案 →</a>
            </div>

            <div class="scroll-story-visual animate-fade-in-up stagger-4">
                <!-- 模拟健康时间线 -->
                <div class="timeline-mock">
                    <div class="step" style="border-left-color: var(--color-blue);">
                        <span class="num">1</span>
                        <span class="label">🐾 出生 · 首次体检</span>
                        <span class="sub">2026-01</span>
                    </div>
                    <div class="step" style="border-left-color: var(--color-blue);">
                        <span class="num">2</span>
                        <span class="label">💉 第一针疫苗</span>
                        <span class="sub">2026-03</span>
                    </div>
                    <div class="step" style="border-left-color: var(--color-blue);">
                        <span class="num">3</span>
                        <span class="label">🩺 定期体检 · 健康</span>
                        <span class="sub">2026-06</span>
                    </div>
                    <div class="step" style="border-left-color: var(--color-gray-300); opacity: 0.6;">
                        <span class="num">4</span>
                        <span class="label">💊 驱虫计划</span>
                        <span class="sub">2026-09</span>
                    </div>
                    <div class="step" style="border-left-color: var(--color-gray-300); opacity: 0.6;">
                        <span class="num">5</span>
                        <span class="label">🏥 年度全面体检</span>
                        <span class="sub">2027-01</span>
                    </div>
                </div>
            </div>
        </div>
    </section>

    <!-- ============================================================
    TESTIMONIALS — 社会证明
    ============================================================ -->
    <section class="testimonials">
        <div class="container">
            <div class="testimonials-header animate-fade-in-up stagger-1">
                <h2>
                    宠主们 <span class="serif">怎么说</span>
                </h2>
                <div class="rating">
                    <span class="stars">★★★★★</span>
                    <span>Clutch 4.9/5</span>
                </div>
            </div>

            <div class="testimonial-cards">
                <div class="testimonial-card animate-fade-in-up stagger-2">
                    <div class="quote-mark">"</div>
                    <blockquote>
                        自从用了 PetHealth，再也没漏过疫苗和驱虫。AI 诊断功能真的太实用了，
                        半夜猫咪不舒服也能先判断一下。
                    </blockquote>
                    <div class="author">
                        <div class="avatar">🐱</div>
                        <div class="info">
                            <div class="name">陈女士</div>
                            <div class="role">英短 · 2岁</div>
                        </div>
                    </div>
                </div>

                <div class="testimonial-card animate-fade-in-up stagger-3">
                    <div class="quote-mark">"</div>
                    <blockquote>
                        健康趋势图让我能直观看到狗狗的体重变化。之前换粮后体重下降，
                        第一时间就发现了，及时调整。
                    </blockquote>
                    <div class="author">
                        <div class="avatar">🐕</div>
                        <div class="info">
                            <div class="name">张先生</div>
                            <div class="role">柯基 · 3岁</div>
                        </div>
                    </div>
                </div>

                <div class="testimonial-card animate-fade-in-up stagger-4">
                    <div class="quote-mark">"</div>
                    <blockquote>
                        社区功能太棒了！上次猫咪不吃东西，在社区发了帖子，
                        好多有经验的宠主给了建议，真的帮了大忙。
                    </blockquote>
                    <div class="author">
                        <div class="avatar">🐰</div>
                        <div class="info">
                            <div class="name">林小姐</div>
                            <div class="role">垂耳兔 · 1岁</div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    

    <!-- ============================================================
    PARTNER / CTA — 行动召唤
    ============================================================ -->
    <section class="section-padding" style="background: var(--color-bg);">
        <div class="container">
            <div class="partner-cta animate-fade-in-up stagger-2">
                <h2>
                    和 <span style="color: var(--color-blue);">PetHealth</span> 一起，<br />
                    守护毛孩子的健康
                </h2>
                <p style="color: var(--color-text-muted); max-width: 500px; margin: 0 auto 2rem; line-height: 1.7;">
                    立即加入 10,000+ 宠主的行列，用科技让养宠更轻松、更安心。
                </p>
                <div class="actions">
                    <a href="#" class="btn-primary" style="padding: 0.85rem 2.5rem; font-size: 1rem;">
                        🐾 免费注册，开始使用
                    </a>
                    <a href="#" class="btn-secondary">
                        了解更多 →
                    </a>
                </div>
            </div>
        </div>
    </section>

    <!-- ============================================================
    FOOTER
    ============================================================ -->
    <footer class="footer">
        <div class="footer-inner">
            <div style="display: flex; align-items: center; gap: 0.75rem;">
                <span style="font-family: var(--font-serif); font-size: 1.5rem; font-style: italic; color: var(--color-primary-dark);">
                    Pet<span style="color: var(--color-blue);">Health</span>
                </span>
                <span style="font-size: 0.75rem; color: var(--color-text-light);">· 宠物健康管家</span>
            </div>
            <div class="footer-links">
                <a href="#">首页</a>
                <a href="/pets">宠物档案</a>
                <a href="/ai-diagnosis">AI 助手</a>
                <a href="/community">社区</a>
                <a href="#">关于我们</a>
            </div>
        </div>
        <div class="footer-bottom">
            <span>© 2026 PetHealth. All rights reserved.</span>
            <span>Made with ❤️ for every pet</span>
        </div>
    </footer>

    <!-- ============================================================
    FLOATING BOTTOM NAV — 便捷导航
    ============================================================ -->
    <nav class="bottom-nav" aria-label="底部导航">
        <span class="brand-mark">PH</span>
        <div class="nav-links">
            <a href="#" class="active">首页</a>
            <a href="/pets">档案</a>
            <a href="/ai-diagnosis">AI</a>
            <a href="/community">社区</a>
            <a href="/reminders">提醒</a>
        </div>
        <a href="#" class="btn-primary">开始使用</a>
    </nav>

    <!-- ============================================================
    LUCIDE ICONS 初始化
    ============================================================ -->
    <script>
        lucide.createIcons();
    </script>

</body>
</html>
```