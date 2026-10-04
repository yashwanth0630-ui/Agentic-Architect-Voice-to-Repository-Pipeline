# Skill: UI & Design Component System

## Obsidian Ember Agentic Design System
1. **Design System & Styling**:
   - Palette: Surface (`#131316`), Primary Container (`#FF570B`), Primary (`#FFB59C`), Secondary Container (`#EB6B01`), Tertiary Container (`#F66018`), On-Surface (`#E5E1E5`), On-Surface-Variant (`#E5BEB2`).
   - Use utility classes via Tailwind CSS or Compose design tokens.
   - Support dark mode by default (`dark:` variant or system preference tokens).

2. **Component Composition**:
   - Fixed Atmospheric Glass Header Bar with close action, title ("Conversational Chat"), "Live" status badge, and user avatar.
   - Secondary Control: Translucent quick-dismiss "Close chat" pill.
   - Conversational Canvas: Gradient user chat bubbles, Ada AI output stream with agent metadata, parameters card, and embedded interactive contract artifact cards.
   - Suspended Bottom Input Dock: Floating capsule over screen safe area with attachment trigger, text prompt input, microphone voice trigger, and upward execution arrow button.
   - Text Shimmer Animation (`ShimmerText`) & Rose Orbit loader retained across inference states.

3. **Accessibility (a11y)**:
   - Provide explicit `aria-label` or `aria-labelledby` attributes for icon-only buttons and interactive controls.
   - Ensure keyboard navigability (`Tab`, `Escape`, `Enter`, `Space`) on all modal/dropdown dialogs.
   - Semantic HTML: Use `<header>`, `<main>`, `<section>`, `<nav>`, `<article>`, `<button>` instead of clickable `<div>` elements.

4. **Animations & Polish**:
   - Text Shimmer & Rose Orbit mathematical curve animations (`r(t) = 7.0 - 2.7s cos(7t)`).
   - Micro-interactions on buttons, hovers, active states, and scale/glow transitions.

