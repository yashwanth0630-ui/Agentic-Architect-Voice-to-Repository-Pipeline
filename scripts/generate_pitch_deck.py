"""
Agentic Architect — 5-Slide Pitch Deck Generator
Uses ReportLab to generate a high-definition 16:9 widescreen presentation PDF.
Explains the offline "Red Light" architecture, Snapdragon NPU efficiency, and multi-agent workflows.
"""

import os
from reportlab.lib.pagesizes import landscape
from reportlab.lib.colors import HexColor
from reportlab.lib.units import inch
from reportlab.pdfgen import canvas

# Dimensions: 16:9 widescreen in points (960 x 540)
PAGE_WIDTH = 960
PAGE_HEIGHT = 540

OUTPUT_FILE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "Agentic_Architect_Pitch_Deck.pdf")

# Color Palette (Dark Mode iQOO Theme)
BG_DARK = HexColor("#0A0E17")
CARD_BG = HexColor("#131B2E")
CARD_BORDER = HexColor("#22304A")
TEXT_WHITE = HexColor("#FFFFFF")
TEXT_MUTED = HexColor("#94A3B8")
ACCENT_AMBER = HexColor("#F59E0B")
ACCENT_CYAN = HexColor("#06B6D4")
ACCENT_EMERALD = HexColor("#10B981")
ACCENT_BLUE = HexColor("#3B82F6")

def draw_header_footer(c, slide_num, total_slides=5, category="AGENTIC ARCHITECT • iQOO HACKATHON"):
    # Header Accent Line
    c.setFillColor(ACCENT_AMBER)
    c.rect(40, PAGE_HEIGHT - 28, 4, 16, fill=True, stroke=False)
    
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(50, PAGE_HEIGHT - 24, category.upper())
    
    # Header right: Status badge
    c.setFont("Helvetica", 9)
    c.setFillColor(TEXT_MUTED)
    c.drawRightString(PAGE_WIDTH - 40, PAGE_HEIGHT - 24, "SNAPDRAGON 8 ELITE / 8 GEN 3 • QUALCOMM GENIEX SDK")
    
    # Subtle Divider
    c.setStrokeColor(CARD_BORDER)
    c.setLineWidth(1)
    c.line(40, PAGE_HEIGHT - 38, PAGE_WIDTH - 40, PAGE_HEIGHT - 38)
    
    # Footer Divider
    c.line(40, 40, PAGE_WIDTH - 40, 40)
    
    # Footer text
    c.setFont("Helvetica", 9)
    c.setFillColor(TEXT_MUTED)
    c.drawString(40, 26, "Agentic Architect — Voice-to-Repository Autonomous Multi-Agent Priming System")
    
    # Slide Number Badge
    c.setFillColor(ACCENT_CYAN)
    c.drawRightString(PAGE_WIDTH - 40, 26, f"Slide {slide_num} of {total_slides}")

def draw_card(c, x, y, width, height, title="", accent_color=ACCENT_BLUE):
    # Background
    c.setFillColor(CARD_BG)
    c.setStrokeColor(CARD_BORDER)
    c.setLineWidth(1)
    c.roundRect(x, y, width, height, 8, fill=True, stroke=True)
    
    # Top Accent Bar
    c.setFillColor(accent_color)
    c.roundRect(x, y + height - 4, width, 4, 2, fill=True, stroke=False)
    
    # Card Title
    if title:
        c.setFont("Helvetica-Bold", 13)
        c.setFillColor(TEXT_WHITE)
        c.drawString(x + 16, y + height - 24, title)

# ── SLIDE 1: Title & Executive Vision ───────────────────────────────────────────
def draw_slide_1(c):
    c.setFillColor(BG_DARK)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=True, stroke=False)
    draw_header_footer(c, 1, category="EXECUTIVE PITCH • IQOO INNOVATION")
    
    # Big Hero Tag
    c.setFillColor(ACCENT_CYAN)
    c.setFont("Helvetica-Bold", 12)
    c.drawString(60, 410, "UNIVERSAL ON-DEVICE DEVELOPER SCAFFOLDING")
    
    # Main Headline
    c.setFillColor(TEXT_WHITE)
    c.setFont("Helvetica-Bold", 34)
    c.drawString(60, 365, "Agentic Architect")
    
    c.setFont("Helvetica", 18)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(60, 335, "Spoken Ideas on iQOO Smartphone  ➔  AI-Primed Repository on Laptop")
    
    # Vision Paragraph
    c.setFont("Helvetica", 12)
    c.setFillColor(TEXT_MUTED)
    c.drawString(60, 290, "Turns fleeting developer voice prompts into deterministic, production-grade repositories")
    c.drawString(60, 272, "with full AI agent context manifests, RFC 7807 contracts, and automated IDE orchestration.")
    
    # 3 Pillar Metric Cards
    card_w = 265
    card_h = 160
    card_y = 75
    
    # Card 1: Mobile Layer
    draw_card(c, 60, card_y, card_w, card_h, "1. Mobile Layer (iQOO)", ACCENT_AMBER)
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(76, card_y + 110, "SNAPDRAGON NPU INFERENCE")
    c.setFont("Helvetica", 10)
    c.setFillColor(TEXT_MUTED)
    c.drawString(76, card_y + 92, "• Voice / Audio Prompt Ingestion")
    c.drawString(76, card_y + 74, "• Qwen 2.5-Coder / Phi-4-Mini (INT4)")
    c.drawString(76, card_y + 56, "• Qualcomm GenieX SDK (45 TOPS)")
    c.drawString(76, card_y + 38, "• 100% Offline • Zero Cloud Latency")
    
    # Card 2: Core Engine
    draw_card(c, 345, card_y, card_w, card_h, "2. Core Manifest Engine", ACCENT_CYAN)
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_CYAN)
    c.drawString(361, card_y + 110, "CONTEXT MANIFEST PYRAMID")
    c.setFont("Helvetica", 10)
    c.setFillColor(TEXT_MUTED)
    c.drawString(361, card_y + 92, "• Zod Validation & RFC 7807 Schema")
    c.drawString(361, card_y + 74, "• AGENTS.md, .cursorrules, CLAUDE.md")
    c.drawString(361, card_y + 56, "• Domain Skills: API, UI & Tests")
    c.drawString(361, card_y + 38, "• bundle.zip Stream / File Writer")
    
    # Card 3: Laptop Handoff
    draw_card(c, 630, card_y, card_w, card_h, "3. Green Light Handoff", ACCENT_EMERALD)
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_EMERALD)
    c.drawString(646, card_y + 110, "DESKTOP AUTO-BOOT & PRIMING")
    c.setFont("Helvetica", 10)
    c.setFillColor(TEXT_MUTED)
    c.drawString(646, card_y + 92, "• iQOO Office Kit folder transfer")
    c.drawString(646, card_y + 74, "• bootstrap.sh / .ps1 auto-unpack")
    c.drawString(646, card_y + 56, "• Automated npm install hydration")
    c.drawString(646, card_y + 38, "• Boots Cursor & Primes Cline / Aider")

# ── SLIDE 2: The "Red Light" Architecture & The Disconnected Developer ────────
def draw_slide_2(c):
    c.setFillColor(BG_DARK)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=True, stroke=False)
    draw_header_footer(c, 2, category="PROBLEM & ARCHITECTURAL SOLUTION")
    
    # Headline
    c.setFont("Helvetica-Bold", 22)
    c.setFillColor(TEXT_WHITE)
    c.drawString(60, 450, "The \"Red Light\" Scenario & The Disconnected Developer")
    c.setFont("Helvetica", 12)
    c.setFillColor(TEXT_MUTED)
    c.drawString(60, 430, "How to capture sudden technical breakthroughs when away from your keyboard without cloud fragility.")
    
    # Comparison layout: 2 Large Cards
    w = 405
    h = 330
    y = 65
    
    # Left: The Broken Status Quo
    draw_card(c, 60, y, w, h, "The Status Quo: Friction & Cloud Fragility", HexColor("#EF4444"))
    
    points_bad = [
        ("Inspiration Lost in Transit", "Developers get architecture ideas during commutes, red lights, or walking. By the time they reach a laptop, mental context is lost."),
        ("Cloud LLM Latency & Cost", "External cloud APIs (OpenAI, Claude) require continuous 5G connection, suffer round-trip network lag, and cost per token."),
        ("Security & IP Leak Risk", "Sending internal proprietary schemas and architectural requirements over public APIs introduces compliance risk."),
        ("Cold Repository Start", "Manually setting up directory trees, TypeScript configs, ESLint rules, and agent instructions takes 30-45 minutes per project.")
    ]
    
    cur_y = y + h - 45
    for title, desc in points_bad:
        c.setFont("Helvetica-Bold", 11)
        c.setFillColor(HexColor("#FCA5A5"))
        c.drawString(76, cur_y, f"❌  {title}")
        cur_y -= 14
        c.setFont("Helvetica", 9)
        c.setFillColor(TEXT_MUTED)
        c.drawString(92, cur_y, desc[:68])
        cur_y -= 12
        if len(desc) > 68:
            c.drawString(92, cur_y, desc[68:])
            cur_y -= 16
        else:
            cur_y -= 14

    # Right: Agentic Architect Offline Pipeline
    draw_card(c, 495, y, w, h, "The Agentic Architect Solution: 100% Offline NPU", ACCENT_EMERALD)
    
    points_good = [
        ("Hands-Free Spoken Ingestion", "Speak natural-language requirements directly into your iQOO phone: \"FastAPI backend, Next.js frontend, Tailwind UI\"."),
        ("Snapdragon Hexagon NPU Acceleration", "Runs quantized Qwen 2.5-Coder / Phi-4-Mini in ~160ms on-device via Qualcomm GenieX SDK with 0 cloud calls."),
        ("Strict Constrained Grammar Decoding", "Constrains model output directly into verified JSON adhering to strict Zod and RFC 7807 schemas."),
        ("Instant 'Green Light' Handoff", "Arrive at desk: iQOO Office Kit automatically syncs bundle.zip. Laptop boots IDE primed with all rules.")
    ]
    
    cur_y = y + h - 45
    for title, desc in points_good:
        c.setFont("Helvetica-Bold", 11)
        c.setFillColor(HexColor("#6EE7B7"))
        c.drawString(511, cur_y, f"✅  {title}")
        cur_y -= 14
        c.setFont("Helvetica", 9)
        c.setFillColor(TEXT_MUTED)
        c.drawString(527, cur_y, desc[:68])
        cur_y -= 12
        if len(desc) > 68:
            c.drawString(527, cur_y, desc[68:])
            cur_y -= 16
        else:
            cur_y -= 14

# ── SLIDE 3: Snapdragon NPU Inference & Qualcomm GenieX SDK ───────────────────
def draw_slide_3(c):
    c.setFillColor(BG_DARK)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=True, stroke=False)
    draw_header_footer(c, 3, category="MOBILE HARDWARE & ACCELERATION LAYER")
    
    # Headline
    c.setFont("Helvetica-Bold", 22)
    c.setFillColor(TEXT_WHITE)
    c.drawString(60, 450, "Snapdragon NPU Acceleration & Qualcomm GenieX SDK")
    c.setFont("Helvetica", 12)
    c.setFillColor(TEXT_MUTED)
    c.drawString(60, 430, "On-device INT4 quantized execution delivers desktop-class code reasoning with zero cloud egress.")
    
    # 4 Telemetry Metrics at the top
    metric_w = 195
    metric_h = 75
    y_top = 340
    
    metrics = [
        ("45 TOPS", "NPU Peak Compute", ACCENT_CYAN, "Qualcomm Hexagon NPU"),
        ("168 ms", "Inference Latency", ACCENT_EMERALD, "Sub-second on-device turn"),
        ("1.85 GB", "Memory Footprint", ACCENT_AMBER, "INT4 Quantized weights"),
        ("58.2 tok/s", "Generation Throughput", ACCENT_BLUE, "Instant grammar output")
    ]
    
    for i, (val, label, col, sub) in enumerate(metrics):
        mx = 60 + i * (metric_w + 20)
        c.setFillColor(CARD_BG)
        c.setStrokeColor(CARD_BORDER)
        c.roundRect(mx, y_top, metric_w, metric_h, 8, fill=True, stroke=True)
        c.setFont("Helvetica-Bold", 20)
        c.setFillColor(col)
        c.drawString(mx + 14, y_top + 45, val)
        c.setFont("Helvetica-Bold", 10)
        c.setFillColor(TEXT_WHITE)
        c.drawString(mx + 14, y_top + 28, label)
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(mx + 14, y_top + 14, sub)

    # 2 Feature Deep-Dive Cards
    card_y = 65
    card_h = 250
    card_w = 405
    
    # Left: Qualcomm GenieX SDK Runtime
    draw_card(c, 60, card_y, card_w, card_h, "Qualcomm GenieX SDK Architecture", ACCENT_CYAN)
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_CYAN)
    c.drawString(76, card_y + 205, "HARDWARE-AWARE MODEL COMPILATION")
    
    geniex_points = [
        ("Graph Quantization (INT4 / INT8)", "Weights compiled using Qualcomm AI Model Efficiency Toolkit (AIMET) preserving code semantics."),
        ("Hexagon Direct Tensor Offload", "Bypasses CPU/GPU thermal limits for sustained, energy-efficient generation without throttling."),
        ("Grammar-Guided Constrained Decoding", "Regex/EBNF constraints guarantee that model tokens strictly parse into ProjectConfig JSON."),
        ("Dual Model Fallback Strategy", "Primary: Qwen 2.5-Coder 7B INT4 (complex stacks); Secondary: Phi-4-Mini 3.8B (ultra-low power).")
    ]
    cur_y = card_y + 185
    for title, desc in geniex_points:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(TEXT_WHITE)
        c.drawString(76, cur_y, f"• {title}")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(88, cur_y, desc)
        cur_y -= 17

    # Right: Structured Output Schema
    draw_card(c, 495, card_y, card_w, card_h, "Zero-Hallucination JSON Constraints", ACCENT_AMBER)
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(511, card_y + 205, "SYNTACTIC PARSING ENFORCEMENT")
    
    schema_points = [
        ("Zod Schema Invariant", "Ensures runtime, architecture, install/dev/test commands, and packages match schema."),
        ("Polyglot Stack Detection", "Automatically identifies frameworks (FastAPI, Next.js, Go Gin, Spring Boot, Vue, Tailwind)."),
        ("Safe Boundary Pre-Configuration", "Embeds zero-secret rules, lockfile integrity, and non-destructive Git constraints into JSON."),
        ("Offline Kotlin Zip Streamer", "Packages manifest files directly to /sdcard/iQOO_Share/bundle.zip with zero network activity.")
    ]
    cur_y = card_y + 185
    for title, desc in schema_points:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(TEXT_WHITE)
        c.drawString(511, cur_y, f"• {title}")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(523, cur_y, desc)
        cur_y -= 17

# ── SLIDE 4: Core Engine & The Green Light Handoff ────────────────────────────
def draw_slide_4(c):
    c.setFillColor(BG_DARK)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=True, stroke=False)
    draw_header_footer(c, 4, category="CORE GENERATION & DESKTOP HANDOFF")
    
    # Headline
    c.setFont("Helvetica-Bold", 22)
    c.setFillColor(TEXT_WHITE)
    c.drawString(60, 450, "Manifest Pyramid & The \"Green Light\" Handoff")
    c.setFont("Helvetica", 12)
    c.setFillColor(TEXT_MUTED)
    c.drawString(60, 430, "A universal context layer ensuring all AI developer assistants adopt deterministic project rules.")
    
    # Left: Manifest Pyramid Table
    w_left = 490
    h_cards = 340
    y = 65
    
    draw_card(c, 60, y, w_left, h_cards, "The Multi-Agent Manifest Pyramid", ACCENT_BLUE)
    
    manifest_rows = [
        ("AGENTS.md", "Cline, Roo Code, Aider", "Root commands (npm test, dev), safety boundaries, git rules"),
        (".cursorrules", "Cursor IDE Composer", "Tech stack, typing standards, UI rules, anti-patterns"),
        ("CLAUDE.md", "Claude Code CLI", "Session initialization, build commands, test workflows"),
        ("GEMINI.md", "Google Antigravity", "Domain rules, RFC 7807 contracts, modular guidelines"),
        ("skills/api-contracts.md", "All Agents", "RFC 7807 problem details, unified success envelope schema"),
        ("skills/ui-component-system.md", "All Agents", "Tailwind CSS design tokens, accessibility (a11y), clean UI"),
        ("skills/testing-patterns.md", "All Agents", "Vitest & pytest test conventions, isolation, mock standards")
    ]
    
    cur_y = y + h_cards - 40
    for fname, target, purpose in manifest_rows:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(ACCENT_CYAN)
        c.drawString(76, cur_y, fname)
        c.setFont("Helvetica-Bold", 8)
        c.setFillColor(ACCENT_AMBER)
        c.drawString(220, cur_y, f"[{target}]")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(88, cur_y, purpose)
        cur_y -= 15

    # Right: Bridge Transfer Channels
    w_right = 320
    draw_card(c, 580, y, w_right, h_cards, "The \"Green Light\" Transfer Bridge", ACCENT_EMERALD)
    
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_EMERALD)
    c.drawString(596, y + h_cards - 40, "CHANNEL 1: iQOO OFFICE KIT")
    c.setFont("Helvetica", 8.5)
    c.setFillColor(TEXT_MUTED)
    c.drawString(596, y + h_cards - 56, "• Peer-to-peer Wi-Fi / Bluetooth fast sync")
    c.drawString(596, y + h_cards - 70, "• Direct drop into ~/iQOO_Share/bundle.zip")
    c.drawString(596, y + h_cards - 84, "• Zero cloud account or third-party server")
    
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_CYAN)
    c.drawString(596, y + h_cards - 114, "CHANNEL 2: LOCAL EXPRESS STREAM")
    c.setFont("Helvetica", 8.5)
    c.setFillColor(TEXT_MUTED)
    c.drawString(596, y + h_cards - 130, "• POST /api/export HTTP pipeline")
    c.drawString(596, y + h_cards - 144, "• Archiver v8 in-memory stream")
    c.drawString(596, y + h_cards - 158, "• Instant curl fetch over local Wi-Fi")
    
    c.setFont("Helvetica-Bold", 10)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(596, y + h_cards - 188, "RELIABILITY & ENCRYPTION")
    c.setFont("Helvetica", 8.5)
    c.setFillColor(TEXT_MUTED)
    c.drawString(596, y + h_cards - 204, "• In-memory streaming (0 temp disk leaks)")
    c.drawString(596, y + h_cards - 218, "• SHA-256 integrity check upon receipt")
    c.drawString(596, y + h_cards - 232, "• RFC 7807 error envelopes for all routes")
    c.drawString(596, y + h_cards - 246, "• 100% deterministic template outputs")

# ── SLIDE 5: Laptop Auto-Boot & Multi-Agent Orchestration ──────────────────────
def draw_slide_5(c):
    c.setFillColor(BG_DARK)
    c.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, fill=True, stroke=False)
    draw_header_footer(c, 5, category="LAPTOP EXECUTION & MULTI-AGENT PRIMING")
    
    # Headline
    c.setFont("Helvetica-Bold", 22)
    c.setFillColor(TEXT_WHITE)
    c.drawString(60, 450, "Laptop Auto-Boot & Multi-Agent Priming")
    c.setFont("Helvetica", 12)
    c.setFillColor(TEXT_MUTED)
    c.drawString(60, 430, "Developer sits down, runs bootstrap.sh, and begins coding immediately with fully primed agents.")
    
    # 3 Workflow Steps Across
    w = 265
    h = 330
    y = 65
    
    # Step 1: Bootstrap Execution
    draw_card(c, 60, y, w, h, "Step 1: Unpack & Hydrate", ACCENT_AMBER)
    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(ACCENT_AMBER)
    c.drawString(76, y + h - 45, "AUTOMATED WORKSPACE SETUP")
    
    step1_items = [
        ("bootstrap.sh / .ps1", "One-command terminal execution."),
        ("Archive Ingestion", "Detects local iQOO_Share/ or pulls from Express endpoint."),
        ("Clean Extraction", "Unpacks into ./scaffolded-workspace with exact folder structure."),
        ("Dependency Hydration", "Executes npm install automatically to install packages."),
        ("Manifest Verification", "Verifies AGENTS.md, .cursorrules, CLAUDE.md, and skills/ are intact.")
    ]
    cur_y = y + h - 65
    for title, desc in step1_items:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(TEXT_WHITE)
        c.drawString(76, cur_y, f"• {title}")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(88, cur_y, desc)
        cur_y -= 16

    # Step 2: Auto-Boot IDE
    draw_card(c, 345, y, w, h, "Step 2: IDE Auto-Boot", ACCENT_CYAN)
    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(ACCENT_CYAN)
    c.drawString(361, y + h - 45, "INSTANT DEVELOPER CONSOLE")
    
    step2_items = [
        ("Cursor / VS Code Launch", "Script automatically launches cursor ./scaffolded-workspace."),
        ("Zero Manual Setup", "No need to create folders or write starter configuration files."),
        ("ManifestViewer.tsx", "Interactive visual console for adjusting manifests or prompts."),
        ("RFC 7807 Live Validation", "API endpoint contracts and test suites ready out-of-the-box."),
        ("Full Monorepo Support", "Express, Next.js, FastAPI, or Kotlin pre-configured.")
    ]
    cur_y = y + h - 65
    for title, desc in step2_items:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(TEXT_WHITE)
        c.drawString(361, cur_y, f"• {title}")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(373, cur_y, desc)
        cur_y -= 16

    # Step 3: Agent Priming
    draw_card(c, 630, y, w, h, "Step 3: Multi-Agent Priming", ACCENT_EMERALD)
    c.setFont("Helvetica-Bold", 9)
    c.setFillColor(ACCENT_EMERALD)
    c.drawString(646, y + h - 45, "DETERMINISTIC COLLABORATION")
    
    step3_items = [
        ("Cursor Composer", "Instantly adopts .cursorrules for type-safe code completions."),
        ("Claude Code CLI", "Reads CLAUDE.md to understand build, lint, and test commands."),
        ("Cline & Roo Code", "Strictly constrained by AGENTS.md guardrails and boundaries."),
        ("Aider Terminal", "Picks up repo architecture map for precise patch creation."),
        ("Zero Cold-Start Lag", "Agents start generating production code from second one.")
    ]
    cur_y = y + h - 65
    for title, desc in step3_items:
        c.setFont("Helvetica-Bold", 9)
        c.setFillColor(TEXT_WHITE)
        c.drawString(646, cur_y, f"• {title}")
        cur_y -= 11
        c.setFont("Helvetica", 8)
        c.setFillColor(TEXT_MUTED)
        c.drawString(658, cur_y, desc)
        cur_y -= 16

def generate_pitch_deck():
    c = canvas.Canvas(OUTPUT_FILE, pagesize=(PAGE_WIDTH, PAGE_HEIGHT))
    
    # 5 Slides
    draw_slide_1(c)
    c.showPage()
    
    draw_slide_2(c)
    c.showPage()
    
    draw_slide_3(c)
    c.showPage()
    
    draw_slide_4(c)
    c.showPage()
    
    draw_slide_5(c)
    c.showPage()
    
    c.save()
    print(f"[PitchDeckGenerator] Successfully generated 5-slide PDF: {OUTPUT_FILE}")

if __name__ == "__main__":
    generate_pitch_deck()
