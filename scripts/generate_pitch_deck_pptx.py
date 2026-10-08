"""
Agentic Architect — High-Definition 16:9 PowerPoint (PPTX) Pitch Deck Generator
Uses python-pptx to generate a professional, widescreen, dark-themed presentation.
Covers mobile NPU edge computing, laptop AI bridge, dual IDE injection, and multi-agent priming.
"""

import os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.enum.shapes import MSO_SHAPE

# Slide Dimensions: 16:9 Widescreen (13.333 x 7.5 inches)
SLIDE_WIDTH = Inches(13.333)
SLIDE_HEIGHT = Inches(7.5)

OUTPUT_FILE = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 
    "Agentic_Architect_Pitch_Deck.pptx"
)

# Dark Mode Color Palette (iQOO / Obsidian Ember Theme)
COLOR_BG_DARK = RGBColor(10, 14, 23)        # #0A0E17 Deep Obsidian
COLOR_CARD_BG = RGBColor(19, 27, 46)        # #131B2E Card Navy Dark
COLOR_CARD_BORDER = RGBColor(34, 48, 74)    # #22304A Slate Border
COLOR_TEXT_WHITE = RGBColor(255, 255, 255)  # #FFFFFF Primary White
COLOR_TEXT_MUTED = RGBColor(148, 163, 184)  # #94A3B8 Slate Gray
COLOR_AMBER = RGBColor(245, 158, 11)        # #F59E0B Ember Amber
COLOR_CYAN = RGBColor(6, 182, 212)          # #06B6D4 Qualcomm Cyan
COLOR_EMERALD = RGBColor(16, 185, 129)      # #10B981 Success Emerald
COLOR_BLUE = RGBColor(59, 130, 246)         # #3B82F6 Accent Blue
COLOR_RED = RGBColor(239, 68, 68)           # #EF4444 Danger Red


def create_blank_slide(prs):
    """Creates a blank slide with a full-bleed dark background."""
    blank_slide_layout = prs.slide_layouts[6]
    slide = prs.slides.add_slide(blank_slide_layout)
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, SLIDE_WIDTH, SLIDE_HEIGHT)
    bg.fill.solid()
    bg.fill.fore_color.rgb = COLOR_BG_DARK
    bg.line.fill.background()
    return slide


def add_header_footer(slide, slide_num, total_slides=6, category="AGENTIC ARCHITECT • iQOO HACKATHON"):
    """Adds standard header accent, category title, and footer metadata."""
    # Top accent bar
    top_bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.5), Inches(0.4), Inches(0.08), Inches(0.35))
    top_bar.fill.solid()
    top_bar.fill.fore_color.rgb = COLOR_AMBER
    top_bar.line.fill.background()

    # Category Title
    tx_box = slide.shapes.add_textbox(Inches(0.7), Inches(0.38), Inches(6.0), Inches(0.4))
    tf = tx_box.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.text = category.upper()
    p.font.name = "Arial"
    p.font.size = Pt(11)
    p.font.bold = True
    p.font.color.rgb = COLOR_AMBER

    # Top right badge
    tx_right = slide.shapes.add_textbox(Inches(7.0), Inches(0.38), Inches(5.8), Inches(0.4))
    tf_r = tx_right.text_frame
    p_r = tf_r.paragraphs[0]
    p_r.text = "SNAPDRAGON NPU (45 TOPS) • QUALCOMM GENIEX SDK • RTX GPU BRIDGE"
    p_r.alignment = PP_ALIGN.RIGHT
    p_r.font.name = "Arial"
    p_r.font.size = Pt(9.5)
    p_r.font.color.rgb = COLOR_TEXT_MUTED

    # Header divider
    h_line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.5), Inches(0.85), Inches(12.333), Inches(0.015))
    h_line.fill.solid()
    h_line.fill.fore_color.rgb = COLOR_CARD_BORDER
    h_line.line.fill.background()

    # Footer divider
    f_line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0.5), Inches(6.9), Inches(12.333), Inches(0.015))
    f_line.fill.solid()
    f_line.fill.fore_color.rgb = COLOR_CARD_BORDER
    f_line.line.fill.background()

    # Footer Left
    tx_foot = slide.shapes.add_textbox(Inches(0.5), Inches(6.95), Inches(9.0), Inches(0.35))
    tf_f = tx_foot.text_frame
    p_f = tf_f.paragraphs[0]
    p_f.text = "Agentic Architect — Voice-to-Repository Autonomous Multi-Agent Priming System"
    p_f.font.name = "Arial"
    p_f.font.size = Pt(9.5)
    p_f.font.color.rgb = COLOR_TEXT_MUTED

    # Footer Right: Slide Number
    tx_num = slide.shapes.add_textbox(Inches(10.0), Inches(6.95), Inches(2.833), Inches(0.35))
    tf_n = tx_num.text_frame
    p_n = tf_n.paragraphs[0]
    p_n.text = f"Slide {slide_num} of {total_slides}"
    p_n.alignment = PP_ALIGN.RIGHT
    p_n.font.name = "Arial"
    p_n.font.size = Pt(9.5)
    p_n.font.bold = True
    p_n.font.color.rgb = COLOR_CYAN


def add_card(slide, left, top, width, height, title="", accent_color=COLOR_BLUE):
    """Draws a rounded card container with an accent bar and header."""
    card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, height)
    card.fill.solid()
    card.fill.fore_color.rgb = COLOR_CARD_BG
    card.line.color.rgb = COLOR_CARD_BORDER
    card.line.width = Pt(1)

    # Accent Top Stripe
    accent = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, left, top, width, Inches(0.08))
    accent.fill.solid()
    accent.fill.fore_color.rgb = accent_color
    accent.line.fill.background()

    if title:
        tx = slide.shapes.add_textbox(left + Inches(0.2), top + Inches(0.15), width - Inches(0.4), Inches(0.4))
        p = tx.text_frame.paragraphs[0]
        p.text = title
        p.font.name = "Arial"
        p.font.size = Pt(13)
        p.font.bold = True
        p.font.color.rgb = COLOR_TEXT_WHITE


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 1: Executive Title & Vision
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_1(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 1, category="EXECUTIVE PITCH • IQOO INNOVATION")

    # Hero Subhead
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.2), Inches(11.0), Inches(0.4))
    p = tx.text_frame.paragraphs[0]
    p.text = "UNIVERSAL ON-DEVICE DEVELOPER SCAFFOLDING & AI PRIMING"
    p.font.name = "Arial"
    p.font.size = Pt(12)
    p.font.bold = True
    p.font.color.rgb = COLOR_CYAN

    # Title
    tx_title = slide.shapes.add_textbox(Inches(0.8), Inches(1.6), Inches(11.5), Inches(0.9))
    p_t = tx_title.text_frame.paragraphs[0]
    p_t.text = "Agentic Architect"
    p_t.font.name = "Arial"
    p_t.font.size = Pt(44)
    p_t.font.bold = True
    p_t.font.color.rgb = COLOR_TEXT_WHITE

    # Headline
    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(2.6), Inches(11.5), Inches(0.5))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "Spoken Ideas on iQOO Smartphone  ➔  AI-Primed Repository on Laptop"
    p_s.font.name = "Arial"
    p_s.font.size = Pt(19)
    p_s.font.bold = True
    p_s.font.color.rgb = COLOR_AMBER

    # Vision Paragraph
    tx_body = slide.shapes.add_textbox(Inches(0.8), Inches(3.2), Inches(11.5), Inches(0.8))
    tf_b = tx_body.text_frame
    tf_b.word_wrap = True
    p_b = tf_b.paragraphs[0]
    p_b.text = (
        "Turns fleeting developer voice commands into deterministic, production-grade repositories "
        "with universal multi-agent context manifests (AGENTS.md, .cursorrules, CLAUDE.md), "
        "RFC 7807 contracts, and zero-click desktop IDE orchestration."
    )
    p_b.font.name = "Arial"
    p_b.font.size = Pt(13)
    p_b.font.color.rgb = COLOR_TEXT_MUTED

    # 3 Architecture Pillars
    card_w = Inches(3.8)
    card_h = Inches(2.3)
    top_y = Inches(4.3)

    # Card 1: Mobile Layer
    add_card(slide, Inches(0.8), top_y, card_w, card_h, "1. Mobile Layer (iQOO)", COLOR_AMBER)
    tx_c1 = slide.shapes.add_textbox(Inches(1.0), top_y + Inches(0.5), card_w - Inches(0.4), Inches(1.6))
    tf1 = tx_c1.text_frame
    tf1.word_wrap = True
    items_1 = [
        "• Native Jetpack Compose & SpeechRecognizer",
        "• Qualcomm GenieX SDK (Snapdragon Hexagon 45 TOPS)",
        "• INT4 Quantized Qwen 2.5-Coder / Phi-4-Mini",
        "• 100% Offline • Zero Cloud Latency or Token Fees"
    ]
    for i, it in enumerate(items_1):
        p = tf1.add_paragraph() if i > 0 else tf1.paragraphs[0]
        p.text = it
        p.font.name = "Arial"
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_TEXT_MUTED

    # Card 2: Core Manifest Engine
    add_card(slide, Inches(4.766), top_y, card_w, card_h, "2. Core Manifest Engine", COLOR_CYAN)
    tx_c2 = slide.shapes.add_textbox(Inches(4.966), top_y + Inches(0.5), card_w - Inches(0.4), Inches(1.6))
    tf2 = tx_c2.text_frame
    tf2.word_wrap = True
    items_2 = [
        "• Strict Zod Validation & RFC 7807 Schema Rules",
        "• Universal Manifests: AGENTS.md, .cursorrules",
        "• Domain Skills: API Contracts, UI Tokens & Testing",
        "• In-Memory /sdcard/iQOO_Share/bundle.zip Stream"
    ]
    for i, it in enumerate(items_2):
        p = tf2.add_paragraph() if i > 0 else tf2.paragraphs[0]
        p.text = it
        p.font.name = "Arial"
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_TEXT_MUTED

    # Card 3: Green Light Handoff
    add_card(slide, Inches(8.733), top_y, card_w, card_h, "3. Green Light Handoff", COLOR_EMERALD)
    tx_c3 = slide.shapes.add_textbox(Inches(8.933), top_y + Inches(0.5), card_w - Inches(0.4), Inches(1.6))
    tf3 = tx_c3.text_frame
    tf3.word_wrap = True
    items_3 = [
        "• iQOO Office Kit P2P sync / USB ADB reverse tunnel",
        "• One-click bootstrap.sh / bootstrap.ps1 auto-unpack",
        "• Automated npm install dependency hydration",
        "• Auto-boots Cursor IDE primed for Cline / Aider"
    ]
    for i, it in enumerate(items_3):
        p = tf3.add_paragraph() if i > 0 else tf3.paragraphs[0]
        p.text = it
        p.font.name = "Arial"
        p.font.size = Pt(11)
        p.font.color.rgb = COLOR_TEXT_MUTED


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 2: Problem & Architectural Solution
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_2(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 2, category="PROBLEM & ARCHITECTURAL SOLUTION")

    # Headline
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.05), Inches(11.5), Inches(0.5))
    p = tx.text_frame.paragraphs[0]
    p.text = "The \"Red Light\" Scenario & The Disconnected Developer"
    p.font.name = "Arial"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = COLOR_TEXT_WHITE

    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(11.5), Inches(0.4))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "How to capture sudden technical breakthroughs when away from your keyboard without cloud fragility."
    p_s.font.name = "Arial"
    p_s.font.size = Pt(12)
    p_s.font.color.rgb = COLOR_TEXT_MUTED

    card_w = Inches(5.7)
    card_h = Inches(4.7)
    top_y = Inches(2.0)

    # Left: Status Quo
    add_card(slide, Inches(0.8), top_y, card_w, card_h, "The Status Quo: Friction & Cloud Fragility", COLOR_RED)
    tx_bad = slide.shapes.add_textbox(Inches(1.0), top_y + Inches(0.5), card_w - Inches(0.4), card_h - Inches(0.6))
    tf_b = tx_bad.text_frame
    tf_b.word_wrap = True

    points_bad = [
        ("❌ Inspiration Lost in Transit", "Developers get architecture ideas during commutes, red lights, or walking. By the time they reach a laptop, mental context is lost."),
        ("❌ Cloud LLM Latency & Cost", "External cloud APIs (OpenAI, Claude) require continuous 5G connection, suffer round-trip lag (2-4s), and charge per token."),
        ("❌ Security & IP Leak Risk", "Sending internal proprietary schemas and architectural requirements over public APIs introduces severe compliance risk."),
        ("❌ Cold Repository Start", "Manually setting up directory trees, TypeScript configs, ESLint rules, and agent instructions takes 30-45 minutes per project.")
    ]
    for i, (title, desc) in enumerate(points_bad):
        p1 = tf_b.add_paragraph() if i > 0 else tf_b.paragraphs[0]
        p1.text = title
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = RGBColor(252, 165, 165)
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tf_b.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(10)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Right: Solution
    add_card(slide, Inches(6.833), top_y, card_w, card_h, "The Agentic Architect Solution: 100% Offline Edge", COLOR_EMERALD)
    tx_good = slide.shapes.add_textbox(Inches(7.033), top_y + Inches(0.5), card_w - Inches(0.4), card_h - Inches(0.6))
    tf_g = tx_good.text_frame
    tf_g.word_wrap = True

    points_good = [
        ("✅ Hands-Free Voice Ingestion", "Speak natural-language requirements directly into your iQOO phone: \"Build a liquidity pool monitor in Kotlin with reactive streams\"."),
        ("✅ Snapdragon Hexagon NPU Acceleration", "Runs quantized Qwen 2.5-Coder / Phi-4-Mini in ~160ms on-device via Qualcomm GenieX SDK with 0 cloud calls."),
        ("✅ Strict Grammar-Constrained Decoding", "Constrains model output directly into verified JSON adhering to strict Zod and RFC 7807 schemas."),
        ("✅ Instant 'Green Light' Handoff", "Arrive at desk: iQOO Office Kit or USB tunnel syncs bundle.zip. Laptop boots IDE primed with all rules.")
    ]
    for i, (title, desc) in enumerate(points_good):
        p1 = tf_g.add_paragraph() if i > 0 else tf_g.paragraphs[0]
        p1.text = title
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = RGBColor(110, 231, 183)
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tf_g.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(10)
        p2.font.color.rgb = COLOR_TEXT_MUTED


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 3: Snapdragon NPU Acceleration & Telemetry
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_3(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 3, category="MOBILE HARDWARE & ACCELERATION LAYER")

    # Headline
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.05), Inches(11.5), Inches(0.5))
    p = tx.text_frame.paragraphs[0]
    p.text = "Snapdragon NPU Acceleration & Qualcomm GenieX SDK"
    p.font.name = "Arial"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = COLOR_TEXT_WHITE

    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(11.5), Inches(0.4))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "On-device INT4 quantized execution delivers desktop-class code reasoning with zero cloud egress."
    p_s.font.name = "Arial"
    p_s.font.size = Pt(12)
    p_s.font.color.rgb = COLOR_TEXT_MUTED

    # 4 Top Telemetry Badges
    bw = Inches(2.78)
    bh = Inches(1.1)
    y_top = Inches(2.0)

    badges = [
        ("45 TOPS", "NPU Peak Compute", COLOR_CYAN, "Qualcomm Hexagon NPU"),
        ("168 ms", "Inference Latency", COLOR_EMERALD, "Sub-second on-device turn"),
        ("1.85 GB", "Memory Footprint", COLOR_AMBER, "INT4 Quantized weights"),
        ("58.2 tok/s", "Generation Throughput", COLOR_BLUE, "Instant grammar output")
    ]
    for i, (val, label, col, sub) in enumerate(badges):
        bx = Inches(0.8) + i * Inches(2.97)
        card = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, bx, y_top, bw, bh)
        card.fill.solid()
        card.fill.fore_color.rgb = COLOR_CARD_BG
        card.line.color.rgb = COLOR_CARD_BORDER
        card.line.width = Pt(1)

        tx_m = slide.shapes.add_textbox(bx + Inches(0.15), y_top + Inches(0.1), bw - Inches(0.3), bh - Inches(0.2))
        tf_m = tx_m.text_frame
        p1 = tf_m.paragraphs[0]
        p1.text = val
        p1.font.name = "Arial"
        p1.font.size = Pt(20)
        p1.font.bold = True
        p1.font.color.rgb = col

        p2 = tf_m.add_paragraph()
        p2.text = label
        p2.font.name = "Arial"
        p2.font.size = Pt(10)
        p2.font.bold = True
        p2.font.color.rgb = COLOR_TEXT_WHITE

        p3 = tf_m.add_paragraph()
        p3.text = sub
        p3.font.name = "Arial"
        p3.font.size = Pt(8.5)
        p3.font.color.rgb = COLOR_TEXT_MUTED

    # 2 Feature Deep-Dive Cards Below
    cw = Inches(5.7)
    ch = Inches(3.4)
    cy = Inches(3.35)

    # Left: Qualcomm GenieX SDK Runtime
    add_card(slide, Inches(0.8), cy, cw, ch, "Qualcomm GenieX SDK Architecture", COLOR_CYAN)
    tx_g = slide.shapes.add_textbox(Inches(1.0), cy + Inches(0.5), cw - Inches(0.4), ch - Inches(0.6))
    tfg = tx_g.text_frame
    tfg.word_wrap = True

    g_points = [
        ("Graph Quantization (INT4 / INT8)", "Compiled with Qualcomm AI Model Efficiency Toolkit (AIMET), preserving code structure and syntax accuracy."),
        ("Hexagon Direct Tensor Offload", "Bypasses CPU/GPU thermal limits for sustained, energy-efficient generation without thermal throttling."),
        ("Grammar-Guided Constrained Decoding", "Regex/EBNF constraints guarantee model tokens strictly parse into valid ProjectConfig JSON data classes."),
        ("Dual Model Strategy", "Primary: Qwen 2.5-Coder 7B INT4 (complex architectures); Secondary: Phi-4-Mini 3.8B (ultra-low power mode).")
    ]
    for i, (title, desc) in enumerate(g_points):
        p1 = tfg.add_paragraph() if i > 0 else tfg.paragraphs[0]
        p1.text = f"• {title}"
        p1.font.name = "Arial"
        p1.font.size = Pt(10.5)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_TEXT_WHITE
        p1.space_before = Pt(6) if i > 0 else Pt(0)

        p2 = tfg.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Right: Structured Output Schema
    add_card(slide, Inches(6.833), cy, cw, ch, "Zero-Hallucination JSON Constraints", COLOR_AMBER)
    tx_s = slide.shapes.add_textbox(Inches(7.033), cy + Inches(0.5), cw - Inches(0.4), ch - Inches(0.6))
    tfs = tx_s.text_frame
    tfs.word_wrap = True

    s_points = [
        ("Zod Schema Invariant", "Enforces runtime, architecture, install/dev/test commands, and required dependencies at parse time."),
        ("Polyglot Stack Detection", "Automatically identifies frameworks (FastAPI, Next.js, Go Gin, Spring Boot, Vue, Tailwind CSS)."),
        ("Safe Boundary Pre-Configuration", "Embeds zero-secret rules, lockfile integrity, and non-destructive Git constraints into project rules."),
        ("Offline Kotlin Zip Streamer", "Packages manifest files directly to /sdcard/iQOO_Share/bundle.zip with zero network activity.")
    ]
    for i, (title, desc) in enumerate(s_points):
        p1 = tfs.add_paragraph() if i > 0 else tfs.paragraphs[0]
        p1.text = f"• {title}"
        p1.font.name = "Arial"
        p1.font.size = Pt(10.5)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_TEXT_WHITE
        p1.space_before = Pt(6) if i > 0 else Pt(0)

        p2 = tfs.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 4: Laptop Local AI Bridge & Dual IDE Injection
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_4(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 4, category="LOCAL AI BRIDGE & IDE INJECTION")

    # Headline
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.05), Inches(11.5), Inches(0.5))
    p = tx.text_frame.paragraphs[0]
    p.text = "Laptop AI Bridge & Dual IDE Injection Engine"
    p.font.name = "Arial"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = COLOR_TEXT_WHITE

    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(11.5), Inches(0.4))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "High-speed FastAPI bridge (Port 8000) linking Android voice prompts to laptop RTX GPU & Cursor IDE."
    p_s.font.name = "Arial"
    p_s.font.size = Pt(12)
    p_s.font.color.rgb = COLOR_TEXT_MUTED

    card_w = Inches(3.8)
    card_h = Inches(4.7)
    top_y = Inches(2.0)

    # Card 1: FastAPI Network Bridge
    add_card(slide, Inches(0.8), top_y, card_w, card_h, "1. FastAPI Local Bridge", COLOR_CYAN)
    tx_c1 = slide.shapes.add_textbox(Inches(1.0), top_y + Inches(0.5), card_w - Inches(0.4), card_h - Inches(0.6))
    tf1 = tx_c1.text_frame
    tf1.word_wrap = True

    c1_items = [
        ("Port 8000 Server", "Lightweight Python FastAPI daemon listening on 0.0.0.0:8000 with CORS."),
        ("Local IP Auto-Discovery", "Automatically detects and prints Wi-Fi/LAN IPv4 addresses on startup."),
        ("Zero-Config USB Tunnel", "Supports 'adb reverse tcp:8000 tcp:8000' for zero-network USB connection."),
        ("Cleartext Security Config", "Configured via Android network_security_config.xml for instant local dev.")
    ]
    for i, (title, desc) in enumerate(c1_items):
        p1 = tf1.add_paragraph() if i > 0 else tf1.paragraphs[0]
        p1.text = f"• {title}"
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_TEXT_WHITE
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tf1.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Card 2: Method A - Direct File Writing
    add_card(slide, Inches(4.766), top_y, card_w, card_h, "2. Method A: File Injection", COLOR_AMBER)
    tx_c2 = slide.shapes.add_textbox(Inches(4.966), top_y + Inches(0.5), card_w - Inches(0.4), card_h - Inches(0.6))
    tf2 = tx_c2.text_frame
    tf2.word_wrap = True

    c2_items = [
        ("Ollama RTX GPU Inference", "Forwards prompt to local Ollama (localhost:11434) running Qwen 2.5-Coder."),
        ("Code Block Extraction", "Parses fenced markdown code (```kotlin, ```python, ```ts) automatically."),
        ("Filename Inference", "Infers clean PascalCase/snake_case filenames from prompt intent."),
        ("Direct Workspace Writing", "Writes files directly into ./injected_agents/ in the active IDE workspace.")
    ]
    for i, (title, desc) in enumerate(c2_items):
        p1 = tf2.add_paragraph() if i > 0 else tf2.paragraphs[0]
        p1.text = f"• {title}"
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_TEXT_WHITE
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tf2.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Card 3: Method B - Cursor CLI Agent
    add_card(slide, Inches(8.733), top_y, card_w, card_h, "3. Method B: Cursor Agent", COLOR_EMERALD)
    tx_c3 = slide.shapes.add_textbox(Inches(8.933), top_y + Inches(0.5), card_w - Inches(0.4), card_h - Inches(0.6))
    tf3 = tx_c3.text_frame
    tf3.word_wrap = True

    c3_items = [
        ("Autonomous CLI Execution", "Invokes 'agent -p \"<prompt>\"' via Python subprocess inside target repo."),
        ("Active Context Scaffolding", "Cursor's native agent inspects project tree and implements requested features."),
        ("Clean If/Else Toggle", "Toggled via USE_CURSOR_AGENT boolean or per-request 'use_cursor_agent: true'."),
        ("Fail-Safe Diagnostics", "Returns execution logs and exit codes without server downtime.")
    ]
    for i, (title, desc) in enumerate(c3_items):
        p1 = tf3.add_paragraph() if i > 0 else tf3.paragraphs[0]
        p1.text = f"• {title}"
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_TEXT_WHITE
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tf3.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 5: Multi-Agent Manifest Pyramid & Desktop Priming
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_5(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 5, category="CORE GENERATION & DESKTOP HANDOFF")

    # Headline
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.05), Inches(11.5), Inches(0.5))
    p = tx.text_frame.paragraphs[0]
    p.text = "The Multi-Agent Manifest Pyramid & IDE Priming"
    p.font.name = "Arial"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = COLOR_TEXT_WHITE

    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(11.5), Inches(0.4))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "A universal context layer ensuring all AI developer assistants adopt deterministic project rules."
    p_s.font.name = "Arial"
    p_s.font.size = Pt(12)
    p_s.font.color.rgb = COLOR_TEXT_MUTED

    card_h = Inches(4.7)
    top_y = Inches(2.0)

    # Left: Manifest Pyramid Table
    w_left = Inches(7.0)
    add_card(slide, Inches(0.8), top_y, w_left, card_h, "The Multi-Agent Manifest Pyramid", COLOR_BLUE)
    tx_t = slide.shapes.add_textbox(Inches(1.0), top_y + Inches(0.5), w_left - Inches(0.4), card_h - Inches(0.6))
    tft = tx_t.text_frame
    tft.word_wrap = True

    manifest_rows = [
        ("AGENTS.md", "Cline, Roo Code, Aider", "Root commands (npm test, dev), safety boundaries, git rules"),
        (".cursorrules", "Cursor IDE Composer", "Tech stack, typing standards, UI tokens, anti-patterns"),
        ("CLAUDE.md", "Claude Code CLI", "Session initialization, build commands, test workflows"),
        ("GEMINI.md", "Google Antigravity", "Domain rules, RFC 7807 contracts, modular guidelines"),
        ("skills/api-contracts.md", "All Agents", "RFC 7807 problem details, unified success envelope schema"),
        ("skills/ui-component-system.md", "All Agents", "Tailwind CSS design tokens, accessibility (a11y), clean UI"),
        ("skills/testing-patterns.md", "All Agents", "Vitest & pytest test conventions, isolation, mock standards")
    ]
    for i, (fname, target, purpose) in enumerate(manifest_rows):
        p1 = tft.add_paragraph() if i > 0 else tft.paragraphs[0]
        p1.text = f"{fname}  [{target}]"
        p1.font.name = "Arial"
        p1.font.size = Pt(10.5)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_CYAN
        p1.space_before = Pt(5) if i > 0 else Pt(0)

        p2 = tft.add_paragraph()
        p2.text = purpose
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Right: Desktop Auto-Boot Workflow
    w_right = Inches(4.4)
    add_card(slide, Inches(8.133), top_y, w_right, card_h, "Desktop Auto-Boot Workflow", COLOR_EMERALD)
    tx_r = slide.shapes.add_textbox(Inches(8.333), top_y + Inches(0.5), w_right - Inches(0.4), card_h - Inches(0.6))
    tfr = tx_r.text_frame
    tfr.word_wrap = True

    steps = [
        ("Step 1: One-Click Unpack", "bootstrap.sh / bootstrap.ps1 unpacks bundle.zip into ./scaffolded-workspace."),
        ("Step 2: Auto Hydration", "Automatically runs 'npm install' to hydrate dependencies with zero friction."),
        ("Step 3: IDE Auto-Boot", "Launches Cursor ('cursor .') or VS Code with all project windows arranged."),
        ("Step 4: Instant Agent Priming", "Composer and Cline immediately read .cursorrules and AGENTS.md—zero prompt priming needed.")
    ]
    for i, (st, desc) in enumerate(steps):
        p1 = tfr.add_paragraph() if i > 0 else tfr.paragraphs[0]
        p1.text = st
        p1.font.name = "Arial"
        p1.font.size = Pt(11)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_AMBER
        p1.space_before = Pt(8) if i > 0 else Pt(0)

        p2 = tfr.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED


# ══════════════════════════════════════════════════════════════════════════════
# SLIDE 6: Live Hardware Verification & Compliance
# ══════════════════════════════════════════════════════════════════════════════
def build_slide_6(prs):
    slide = create_blank_slide(prs)
    add_header_footer(slide, 6, category="VERIFICATION & HACKATHON COMPLIANCE")

    # Headline
    tx = slide.shapes.add_textbox(Inches(0.8), Inches(1.05), Inches(11.5), Inches(0.5))
    p = tx.text_frame.paragraphs[0]
    p.text = "Live Hardware Verification & Hackathon Compliance"
    p.font.name = "Arial"
    p.font.size = Pt(22)
    p.font.bold = True
    p.font.color.rgb = COLOR_TEXT_WHITE

    tx_sub = slide.shapes.add_textbox(Inches(0.8), Inches(1.5), Inches(11.5), Inches(0.4))
    p_s = tx_sub.text_frame.paragraphs[0]
    p_s.text = "100% verified on physical smartphone hardware and laptop RTX GPU environment."
    p_s.font.name = "Arial"
    p_s.font.size = Pt(12)
    p_s.font.color.rgb = COLOR_TEXT_MUTED

    card_h = Inches(4.7)
    top_y = Inches(2.0)

    # Left: Compliance Table
    w_left = Inches(6.8)
    add_card(slide, Inches(0.8), top_y, w_left, card_h, "iQOO Hackathon Constraints Compliance", COLOR_AMBER)
    tx_t = slide.shapes.add_textbox(Inches(1.0), top_y + Inches(0.5), w_left - Inches(0.4), card_h - Inches(0.6))
    tft = tx_t.text_frame
    tft.word_wrap = True

    compliance_items = [
        ("✅ 55% \"Red Light\" Focus", "Hands-free natural-language audio prompt capture during commutes with zero laptop keyboard friction."),
        ("✅ Snapdragon NPU Acceleration", "On-device INT4 quantized Qwen 2.5-Coder / Phi-4-Mini via Qualcomm GenieX SDK (45 TOPS); 100% offline."),
        ("✅ On-Device File Generation", "Native Kotlin ZIP streamer compiles universal manifests directly to /sdcard/iQOO_Share/bundle.zip."),
        ("✅ \"Green Light\" Desktop Handoff", "Routes bundle.zip to laptop; bootstrap script unpacks, hydrates packages, and auto-boots primed IDE."),
        ("✅ 17/17 Integration Tests", "Automated Vitest test suite validating Zod parsing, API contracts, manifest generation, and ZIP integrity.")
    ]
    for i, (title, desc) in enumerate(compliance_items):
        p1 = tft.add_paragraph() if i > 0 else tft.paragraphs[0]
        p1.text = title
        p1.font.name = "Arial"
        p1.font.size = Pt(10.5)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_EMERALD
        p1.space_before = Pt(7) if i > 0 else Pt(0)

        p2 = tft.add_paragraph()
        p2.text = desc
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_TEXT_MUTED

    # Right: Live Device Telemetry
    w_right = Inches(4.6)
    add_card(slide, Inches(7.933), top_y, w_right, card_h, "Physical Hardware Test Results", COLOR_CYAN)
    tx_r = slide.shapes.add_textbox(Inches(8.133), top_y + Inches(0.5), w_right - Inches(0.4), card_h - Inches(0.6))
    tfr = tx_r.text_frame
    tfr.word_wrap = True

    telemetry = [
        ("Mobile Client Device", "POCO X6 5G (Android 14 / TargetSDK 36)"),
        ("App Status", "Installed & Running via USB Debugging (PID: 11454)"),
        ("Reverse Port Tunnel", "adb reverse tcp:8000 tcp:8000 (Active)"),
        ("Live Inference Latency", "9,615 ms (Complete roundtrip phone ➔ GPU ➔ phone)"),
        ("Ollama Engine", "v0.35.1 running Qwen 2.5-Coder on RTX GPU"),
        ("Direct File Injection", "5,360 ms to generate AutomatedLiquidityPoolScanner.kt")
    ]
    for i, (label, val) in enumerate(telemetry):
        p1 = tfr.add_paragraph() if i > 0 else tfr.paragraphs[0]
        p1.text = f"• {label}:"
        p1.font.name = "Arial"
        p1.font.size = Pt(10)
        p1.font.bold = True
        p1.font.color.rgb = COLOR_WHITE = COLOR_TEXT_WHITE
        p1.space_before = Pt(6) if i > 0 else Pt(0)

        p2 = tfr.add_paragraph()
        p2.text = val
        p2.font.name = "Arial"
        p2.font.size = Pt(9.5)
        p2.font.color.rgb = COLOR_CYAN


def main():
    prs = Presentation()
    prs.slide_width = SLIDE_WIDTH
    prs.slide_height = SLIDE_HEIGHT

    print("[+] Building Slide 1: Title & Executive Vision...")
    build_slide_1(prs)

    print("[+] Building Slide 2: Problem & Architectural Solution...")
    build_slide_2(prs)

    print("[+] Building Slide 3: Snapdragon NPU Acceleration & Telemetry...")
    build_slide_3(prs)

    print("[+] Building Slide 4: Laptop AI Bridge & Dual IDE Injection...")
    build_slide_4(prs)

    print("[+] Building Slide 5: Multi-Agent Manifest Pyramid & Desktop Priming...")
    build_slide_5(prs)

    print("[+] Building Slide 6: Live Hardware Verification & Compliance...")
    build_slide_6(prs)

    prs.save(OUTPUT_FILE)
    print(f"\n[+] Successfully generated PowerPoint Pitch Deck at:")
    print(f"[+] {OUTPUT_FILE}")
    print(f"    Size: {round(os.path.getsize(OUTPUT_FILE) / 1024, 1)} KB\n")


if __name__ == "__main__":
    main()
