# Skill: UI & Design Component System

## Architectural Principles
1. **Design System & Styling**:
   - Use utility classes via Tailwind CSS or CSS variables.
   - Absolutely no raw inline style objects (`style={{...}}`) except for dynamic CSS transforms or container queries.
   - Support dark mode by default (`dark:` variant or system preference tokens).

2. **Component Composition**:
   - Prefer functional components with explicit TypeScript interfaces for props (`interface ButtonProps { ... }`).
   - Keep presentational components pure and stateless; extract stateful logic and data queries into custom hooks.
   - Reusable primitives live in `src/components/ui/` (buttons, inputs, cards, dialogs).
   - Domain composite widgets live in `src/components/features/`.

3. **Accessibility (a11y)**:
   - Provide explicit `aria-label` or `aria-labelledby` attributes for icon-only buttons and interactive controls.
   - Ensure keyboard navigability (`Tab`, `Escape`, `Enter`, `Space`) on all modal/dropdown dialogs.
   - Semantic HTML: Use `<header>`, `<main>`, `<section>`, `<nav>`, `<article>`, `<button>` instead of clickable `<div>` elements.

4. **Animations & Polish**:
   - Micro-interactions on buttons, hovers, active states, and transitions (e.g. `transition-all duration-200 ease-in-out`).
   - Loading skeletons and optimistic UI updates for async operations.
