---
name: Obsidian Ember Agentic Interface
colors:
  surface: '#131316'
  surface-dim: '#131316'
  surface-bright: '#39393c'
  surface-container-lowest: '#0e0e11'
  surface-container-low: '#1c1b1e'
  surface-container: '#201f22'
  surface-container-high: '#2a2a2d'
  surface-container-highest: '#353438'
  on-surface: '#e5e1e5'
  on-surface-variant: '#e5beb2'
  inverse-surface: '#e5e1e5'
  inverse-on-surface: '#313033'
  outline: '#ac897e'
  outline-variant: '#5c4037'
  surface-tint: '#ffb59c'
  primary: '#ffb59c'
  on-primary: '#5c1900'
  primary-container: '#ff570b'
  on-primary-container: '#511500'
  inverse-primary: '#ab3600'
  secondary: '#ffb68e'
  on-secondary: '#542200'
  secondary-container: '#eb6b01'
  on-secondary-container: '#491d00'
  tertiary: '#ffb599'
  on-tertiary: '#5a1c00'
  tertiary-container: '#f66018'
  on-tertiary-container: '#4f1700'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdbcf'
  primary-fixed-dim: '#ffb59c'
  on-primary-fixed: '#390c00'
  on-primary-fixed-variant: '#832700'
  secondary-fixed: '#ffdbca'
  secondary-fixed-dim: '#ffb68e'
  on-secondary-fixed: '#331200'
  on-secondary-fixed-variant: '#773300'
  tertiary-fixed: '#ffdbce'
  tertiary-fixed-dim: '#ffb599'
  on-tertiary-fixed: '#370e00'
  on-tertiary-fixed-variant: '#7f2b00'
  background: '#131316'
  on-background: '#e5e1e5'
  surface-variant: '#353438'
typography:
  headline-xl:
    fontFamily: Plus Jakarta Sans
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.03em
  headline-xl-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
    letterSpacing: -0.005em
  body-lg:
    fontFamily: Plus Jakarta Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
    letterSpacing: 0em
  body-md:
    fontFamily: Plus Jakarta Sans
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
    letterSpacing: 0em
  body-sm:
    fontFamily: Plus Jakarta Sans
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 18px
    letterSpacing: 0.01em
  label-lg:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.02em
  label-md:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.03em
  label-sm:
    fontFamily: JetBrains Mono
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.05em
rounded:
  sm: 0.5rem
  DEFAULT: 1rem
  md: 1.5rem
  lg: 2rem
  xl: 3rem
  full: 9999px
spacing:
  gutter: 1.5rem
  gutter-mobile: 0.75rem
  margin: 2rem
  margin-mobile: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2.5rem
---

## Brand & Style

This design system establishes a high-performance, immersive workspace engineered for autonomous AI workflows and multimodal synthesis. The brand embodies the intelligence, precision, and raw energy of concentrated computation—visualized as an incandescent ember radiating within an obsidian void.

The aesthetic fuses **Hyper-Refined Dark Glassmorphism** with **Atmospheric Radial Chromatics**:
- **Atmosphere:** Infinite void spaces layered beneath a dominant coronal gradient that bleeds down from the interface header, simulating raw cognitive energy.
- **Surfaces:** Translucent smoked obsidian panes accented by refractive glass edges, specular hairline borders, and selective fiery luminescence.
- **Audience & Mindset:** Forward-looking developers, quantitative researchers, and product operators orchestrating complex autonomous agents who require peak focus, zero cognitive glare, and immediate visceral confirmation of autonomous actions.
- **Emotional Resonance:** Sovereign control, computational speed, deep quietude, and kinetic energy.

## Colors

The palette is strictly calibrated for dark-mode environments, leveraging deep zero-floor values to maximize optical dynamic range against vibrant chromatic embers.

### Base Surface Architecture
- **Base Canvas (`#050507`):** The primary optical anchor. Purest dark matter devoid of cool blue bias.
- **Elevated Canvas (`#0A0A0C`):** Underlying base for structural panels, drawers, and persistent rails.
- **Glass Smoked (`rgba(255, 255, 255, 0.03)` to `rgba(255, 255, 255, 0.06)`): Translucent structural fills with optical diffusion.

### The Coronal Spectrum
- **Primary Core (`#FF5400`):** The foundational fiery orange, reserved for high-priority interactive states, focus indicators, and execution triggers.
- **Luminescent Amber (`#FF7A1A`):** The secondary highlight used for agent states in active synthesis, live waveform monitors, and pulsing edge highlights.
- **Deep Smolder (`#EA580C` & `#C2410C`):** Midtones for gradient interpolation, preventing muddy or washed-out transitions into dark backgrounds.
- **Specular Glow Tint (`rgba(255, 120, 40, 0.2)`): Applied as directional stroke highlights on the top and left bevels of active glass surfaces.

### Monochromatic Text & Status
- **Text Dominant (`#FFFFFF`):** High-clarity primary typography and key metrics.
- **Text Subdued (`#A1A1AA`):** Body context, metadata, and prompt suggestions.
- **Text Ghost (`#52525B`):** Placeholder labels, system stamps, and inactive hotkey glyphs.
- **Success (`#10B981`):** Agent task completion.
- **Warning (`#F59E0B`):** Autonomous fallback alerts and resource throttling.
- **Destructive (`#EF4444`):** Process termination and prompt interruption.

## Typography

Typography pairs geometric clarity with technical precision. 

- **Primary & Display (Plus Jakarta Sans):** Selected for its crisp apertures, contemporary human-engineered geometry, and high legibility at display scales on high-DPI pitch-black screens. Tighter letter spacing on display weights creates cohesion in headline hierarchy.
- **Technical & Utility (JetBrains Mono):** Applied selectively to status stamps, token counts, model parameters, API latency readouts, and pill indicators to ground conversational abstractions in raw computational reality.

### Scaling & Legibility Rules
- Never use font weights below `400` in dark-mode environments to prevent subpixel antialiasing degradation against deep backgrounds.
- High-contrast headlines (`#FFFFFF`) require generous line heights to prevent visual crowding when multi-line agent outputs stream in real-time.

## Layout & Spacing

The layout is structured as an interactive command dock built on a **12-column adaptive fluid grid**, anchoring an expansive central conversational canvas flanked by modular inspection panels.

### Structural Mechanics
- **The Atmospheric Header Anchor:** The top of the viewport hosts a non-interactive canvas-level radial gradient: `radial-gradient(120% 40% at 50% 0%, rgba(255, 84, 0, 0.18) 0%, rgba(234, 88, 12, 0.06) 45%, rgba(5, 5, 7, 0) 100%)`. This anchors the interface visually without distracting from central interactions.
- **The Conversational Stream:** Centered content column spanning a maximum width of `840px` to maintain optimal line-lengths during sustained code or text generation.
- **Suspended Bottom Input Dock:** Floating, detached interactive input cluster offset from the bottom edge by `space-xl` on desktop and `space-md` on mobile.

### Breakpoint Matrix
- **Desktop (1280px+):** Full 12-column presentation. Primary chat stream centered with optional collapsible secondary agent timeline / tool-use drawers (320px fixed). Canvas margin `margin` (`2rem`).
- **Tablet (768px - 1279px):** 8-column layout. Drawers convert to overlay glass sheets. Margins compress to `1.5rem`.
- **Mobile (Below 768px):** 4-column layout. Gutter `gutter-mobile` (`0.75rem`), canvas margin `margin-mobile` (`1rem`). The floating bottom dock docks flush with screen safe areas, pinning actions to bottom glass navigation.

## Elevation & Depth

Depth is not achieved through legacy dark shadows, but through **translucency, light diffusion, and edge refraction**. Dark environments demand ambient luminance and internal light models.

### Depth Layers

1. **Layer 0 (Canvas Void):** `#050507` baseline. Receives dynamic background ambient glows and gradient falls.
2. **Layer 1 (Sub-Panels & Structural Rails):** `#0A0A0C` with `backdrop-filter: blur(16px)` and subtle hairline outline: `1px solid rgba(255, 255, 255, 0.05)`.
3. **Layer 2 (Floating Cards & Agent Bubbles):** `background: rgba(255, 255, 255, 0.03)` with `backdrop-filter: blur(20px)` and a dual-border effect: top/left border `1px solid rgba(255, 255, 255, 0.10)`, bottom/right border `1px solid rgba(255, 255, 255, 0.02)`.
4. **Layer 3 (Interactive Floating Dock & Active Modals):** `background: rgba(10, 10, 12, 0.75)` with `backdrop-filter: blur(28px)`. Border is accented with `1px solid rgba(255, 120, 40, 0.25)` and a low-frequency ground-glow: `box-shadow: 0 12px 40px -10px rgba(0, 0, 0, 0.8), 0 0 30px -5px rgba(255, 84, 0, 0.12)`.
5. **Layer 4 (The Core Orb & Active Triggers):** Unrestrained radial emission. Core `box-shadow: 0 0 45px rgba(255, 84, 0, 0.4), inset 0 0 20px rgba(255, 255, 255, 0.6)`.

## Shapes

The design system utilizes a **pill-shaped geometry (`3`)** system. High-radii elements evoke fluid autonomy, softening the technical density of the agentic interface and creating a friendly, advanced-hardware aesthetic.

### Shape Classifications
- **Inputs & Pill Selectors (`rounded-full` / `9999px`):** Used on all prompt command fields, status badges, model switchers, audio controls, and context tags.
- **Cards & Data Modules (`rounded-xl` / `24px`):** Large spatial surfaces housing agent logs, artifact previews, and inspection matrices.
- **Interactive Action Buttons (`rounded-full` / `9999px`):** Primary triggers, mic voice buttons, and execution triggers remain pure capsules or circles.

## Components

### Buttons & Voice Triggers
- **Primary Execution Button:** Solid fiery gradient fill `linear-gradient(135deg, #FF7A1A 0%, #FF5400 100%)`, text `#FFFFFF`, label font `JetBrains Mono` bold uppercase. Hover: subtle scale (`1.02`) and intensified drop glow (`box-shadow: 0 0 24px rgba(255, 84, 0, 0.4)`).
- **Secondary Ghost Button:** Glass fill `rgba(255, 255, 255, 0.04)`, 1px border `rgba(255, 255, 255, 0.08)`, text `#A1A1AA`. Hover: text `#FFFFFF`, border `rgba(255, 120, 40, 0.3)`.
- **Microphone / Voice Trigger:** Circular pill container (`44px x 44px`). When idle: glass background with glowing amber microphone glyph. When listening: audio-reactive pulsating outer ring with radiating box-shadows alternating between `#FF5400` and `#EA580C`.

### Pill Selectors & Status Chips
- Height `28px` to `32px`, full radius.
- Inactive: translucent dark gray fill (`rgba(255, 255, 255, 0.03)`), border `rgba(255, 255, 255, 0.06)`, text `#A1A1AA`.
- Active: amber rim `1px solid rgba(255, 120, 40, 0.4)`, background `rgba(255, 84, 0, 0.08)`, text `#FF7A1A` with a leading 6px glowing indicator dot.

### Conversational Message Bubbles
- **User Message:** Right-aligned, minimal capsule or compact card. Background `rgba(255, 255, 255, 0.06)`, border `rgba(255, 255, 255, 0.1)`, text `#FFFFFF`.
- **Autonomous Agent Output:** Left-aligned, transparent base with card-level framing. Employs a leading model metadata header featuring an amber sparkle icon (`✨`), execution timestamp, and token speed in `JetBrains Mono`.

### Interactive Bottom Input Dock
- Suspended elevated capsule floating over main content.
- Multi-row glass surface housing expanding prompt text input, model configuration pill, attachment triggers, and the voice/send command cluster.
- Focused state activates a glowing 1px ember hairline outline: `border-color: rgba(255, 120, 40, 0.4)`.

### Glowing Orb Agent Visualization
- Central or ambient floating canvas component visualizing reasoning states.
- Triple-layered radial blur composite featuring an active rotation transform:
  1. Inner core: `#FFFFFF` at 50px blur.
  2. Chromatic layer: `#FF5400` expanding to 120px blur.
  3. Outer atmosphere: `#C2410C` at 180px blur, undulating based on audio streaming or thought processing.

### Glassmorphic Artifact Cards
- Padding `space-lg`, border radius `rounded-xl`.
- Smoked backdrop (`rgba(255, 255, 255, 0.02)`), heavy 24px blur, equipped with a specular top light stroke.
- Header row includes artifact title, execution status chip, and glowing action button for copying or deploying code.