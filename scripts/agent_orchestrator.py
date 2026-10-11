"""
AI Agent Orchestrator for Idea Beacon / Agentic Architect
Handles user intent recognition, multi-file project scaffolding, offline-first templating,
and coordinated lifecycle execution between ProjectManager and PreviewServer.
"""

from dataclasses import dataclass
import json
import os
import re
import time
from typing import Any, Dict, List, Optional, Tuple
import httpx

from project_manager import ProjectManager, project_manager, sanitize_project_id
from preview_server import PreviewServerManager, preview_manager

OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
DEFAULT_MODEL = os.getenv("DEFAULT_MODEL", "qwen2.5-coder:1.5b")


@dataclass
class OrchestrationResult:
    success: bool
    project_id: str
    project_name: str
    workspace: str
    status: str
    preview_url: Optional[str] = None
    preview_port: Optional[int] = None
    message: str = ""
    files_created: List[str] = None
    stage: str = "COMPLETED"
    error: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return {
            "success": self.success,
            "project_id": self.project_id,
            "project_name": self.project_name,
            "workspace": self.workspace,
            "status": self.status,
            "preview_url": self.preview_url,
            "preview_port": self.preview_port,
            "message": self.message,
            "files_created": self.files_created or [],
            "stage": self.stage,
            "error": self.error
        }


class AgentOrchestrator:
    """
    Coordinates project creation, file generation, and live preview servers.
    Provides offline-first resilience with optional local LLM enhancement.
    """
    def __init__(
        self,
        pm: ProjectManager = project_manager,
        psm: PreviewServerManager = preview_manager
    ):
        self.pm = pm
        self.psm = psm

    @staticmethod
    def detect_intent(prompt: str) -> Tuple[str, str, str]:
        """
        Analyzes prompt to determine:
        1. intent_type: 'NEW_PROJECT' vs 'SINGLE_MODULE'
        2. project_slug: e.g. 'company-website'
        3. display_title: e.g. 'Company Website'
        """
        p_lower = prompt.lower()

        # Keywords signaling a multi-file project or website workspace
        project_signals = [
            "website", "web site", "landing page", "web page", "folder",
            "project", "app", "dashboard", "portal", "portfolio", "store",
            "company", "startup", "frontend", "homepage", "web"
        ]

        is_project = any(sig in p_lower for sig in project_signals)

        # Extract meaningful name components
        cleaned = re.sub(
            r"(?i)\b(create|build|make|generate|a|an|the|folder|where|i|can|for|my|our|new|simple|modern|responsive|website|web|site|project|app|please|me|us|to|in|of|with|that|here|this|want)\b",
            " ",
            prompt
        )
        words = re.findall(r"[a-zA-Z0-9]+", cleaned)

        if words:
            slug = "-".join(w.lower() for w in words[:4])
            display_title = " ".join(w.capitalize() for w in words[:4])
        else:
            if "company" in p_lower or "business" in p_lower:
                slug = "company-website"
                display_title = "Company Website"
            elif "portfolio" in p_lower:
                slug = "portfolio-site"
                display_title = "Developer Portfolio"
            elif "dashboard" in p_lower:
                slug = "admin-dashboard"
                display_title = "Admin Dashboard"
            else:
                slug = "modern-website"
                display_title = "Modern Web Project"

        # Ensure slug ends with appropriate identifier if context warrants
        if is_project and not any(k in slug for k in ["site", "web", "app", "project", "page"]):
            slug = f"{slug}-website"

        if is_project and "website" in slug and "website" not in display_title.lower() and "site" not in display_title.lower():
            display_title = f"{display_title} Website"

        intent_type = "NEW_PROJECT" if is_project else "SINGLE_MODULE"
        return intent_type, sanitize_project_id(slug), display_title

    def generate_website_template(
        self,
        project_name: str,
        prompt: str
    ) -> Dict[str, str]:
        """
        Generates production-grade, highly aesthetic modern web files offline.
        Uses rich responsive design, CSS glassmorphism, interactive DOM logic, and clean docs.
        """
        safe_title = project_name.replace("-", " ").title()

        # 1. index.html
        index_html = f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="description" content="{safe_title} - Created with Idea Beacon Agentic Architect">
  <title>{safe_title}</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="styles.css">
</head>
<body>
  <!-- Navigation Header -->
  <header class="navbar">
    <div class="nav-container">
      <a href="#" class="brand-logo">
        <span class="logo-beacon">⚡</span>
        <span class="logo-text">{safe_title}</span>
      </a>
      <nav class="nav-links" id="navLinks">
        <a href="#home" class="nav-link active">Home</a>
        <a href="#services" class="nav-link">Services</a>
        <a href="#about" class="nav-link">About</a>
        <a href="#contact" class="nav-link">Contact</a>
      </nav>
      <div class="nav-actions">
        <span class="status-badge" title="Live Preview Stream">
          <span class="status-dot"></span> LIVE PREVIEW
        </span>
        <button class="mobile-toggle" id="mobileToggle" aria-label="Toggle Menu">☰</button>
      </div>
    </div>
  </header>

  <!-- Hero Section -->
  <main>
    <section id="home" class="hero-section">
      <div class="hero-glow"></div>
      <div class="container hero-content">
        <div class="pill-tag">🚀 Autonomous Project Scaffolding</div>
        <h1 class="hero-title">
          Empowering the Future of <br>
          <span class="gradient-text">{safe_title}</span>
        </h1>
        <p class="hero-subtitle">
          Generated via natural-language voice orchestration directly from Idea Beacon.
          Fast, responsive, and ready for deployment.
        </p>
        <div class="hero-buttons">
          <a href="#services" class="btn btn-primary">Explore Services</a>
          <a href="#contact" class="btn btn-secondary">Get in Touch</a>
        </div>
        <div class="hero-stats">
          <div class="stat-card">
            <div class="stat-number" id="latencyStat">18ms</div>
            <div class="stat-label">Response Time</div>
          </div>
          <div class="stat-card">
            <div class="stat-number">100%</div>
            <div class="stat-label">Responsive</div>
          </div>
          <div class="stat-card">
            <div class="stat-number">0.0.0.0</div>
            <div class="stat-label">LAN Bound</div>
          </div>
        </div>
      </div>
    </section>

    <!-- Services Section -->
    <section id="services" class="services-section">
      <div class="container">
        <div class="section-header">
          <h2 class="section-title">Core Capabilities</h2>
          <p class="section-desc">Engineered for high performance and modern user experiences.</p>
        </div>
        <div class="services-grid">
          <article class="service-card">
            <div class="card-icon">⚡</div>
            <h3>Intelligent Architecture</h3>
            <p>Built with modular component architecture, ensuring seamless scalability and high performance across devices.</p>
          </article>
          <article class="service-card">
            <div class="card-icon">📱</div>
            <h3>Cross-Device Sync</h3>
            <p>Designed for real-time Wi-Fi orchestration between mobile devices and laptop development environments.</p>
          </article>
          <article class="service-card">
            <div class="card-icon">🛡️</div>
            <h3>Enterprise Security</h3>
            <p>Safe sandbox isolation, path traversal guards, and RFC 1918 private subnet validation out of the box.</p>
          </article>
        </div>
      </div>
    </section>

    <!-- About Section -->
    <section id="about" class="about-section">
      <div class="container about-grid">
        <div class="about-text">
          <div class="pill-tag">About The Project</div>
          <h2>Built for Creators & Engineers</h2>
          <p>
            This website workspace was initiated by the prompt:
            <em class="prompt-quote">"{prompt}"</em>
          </p>
          <p>
            You can modify the HTML, CSS, and JS files directly in your workspace folder.
            The preview server automatically serves all updates live over your local network.
          </p>
          <ul class="about-list">
            <li>✓ Full source code access in your local project workspace</li>
            <li>✓ Zero cloud dependencies required</li>
            <li>✓ Instant mobile browser preview via Wi-Fi</li>
          </ul>
        </div>
        <div class="about-preview-box">
          <div class="code-preview-header">
            <span class="code-dot red"></span>
            <span class="code-dot yellow"></span>
            <span class="code-dot green"></span>
            <span class="code-title">workspace-manifest.json</span>
          </div>
          <pre class="code-snippet"><code>{{
  "project": "{safe_title}",
  "engine": "Idea Beacon Agentic Architect",
  "status": "ready",
  "lanPreview": true
}}</code></pre>
        </div>
      </div>
    </section>

    <!-- Contact Section -->
    <section id="contact" class="contact-section">
      <div class="container">
        <div class="contact-card">
          <div class="contact-header">
            <h2>Let's Build Together</h2>
            <p>Send a message through this interactive contact form.</p>
          </div>
          <form id="contactForm" class="contact-form">
            <div class="form-group">
              <label for="name">Your Name</label>
              <input type="text" id="name" name="name" placeholder="Alex Morgan" required>
            </div>
            <div class="form-group">
              <label for="email">Email Address</label>
              <input type="email" id="email" name="email" placeholder="alex@example.com" required>
            </div>
            <div class="form-group">
              <label for="message">Message</label>
              <textarea id="message" name="message" rows="4" placeholder="How can we collaborate?" required></textarea>
            </div>
            <button type="submit" class="btn btn-primary btn-block" id="submitBtn">
              Send Message
            </button>
            <div id="formFeedback" class="form-feedback hidden"></div>
          </form>
        </div>
      </div>
    </section>
  </main>

  <!-- Footer -->
  <footer class="footer">
    <div class="container footer-content">
      <p>© 2026 {safe_title}. Generated with Idea Beacon • Universal AI Architecture.</p>
      <a href="#home" class="back-to-top">Back to top ↑</a>
    </div>
  </footer>

  <script src="app.js"></script>
</body>
</html>"""

        # 2. styles.css
        styles_css = """/* Idea Beacon - Modern Design Tokens */
:root {
  --bg-dark: #0a0e17;
  --bg-card: rgba(18, 24, 38, 0.7);
  --bg-card-hover: rgba(26, 35, 56, 0.85);
  --border-color: rgba(255, 255, 255, 0.08);
  --text-main: #f0f4fc;
  --text-muted: #8b9bb4;
  --accent-primary: #ff5722;
  --accent-glow: rgba(255, 87, 34, 0.35);
  --accent-secondary: #ff9800;
  --success: #10b981;
  --font-sans: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
  --font-mono: 'JetBrains Mono', monospace;
  --radius-lg: 16px;
  --radius-md: 10px;
  --transition: all 0.25s cubic-bezier(0.16, 1, 0.3, 1);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html {
  scroll-behavior: smooth;
  font-family: var(--font-sans);
  background-color: var(--bg-dark);
  color: var(--text-main);
}

body {
  min-height: 100vh;
  line-height: 1.6;
  overflow-x: hidden;
}

.container {
  max-width: 1140px;
  margin: 0 auto;
  padding: 0 24px;
}

/* Navbar */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  background: rgba(10, 14, 23, 0.85);
  border-bottom: 1px solid var(--border-color);
  padding: 16px 0;
}

.nav-container {
  max-width: 1140px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--text-main);
  font-weight: 700;
  font-size: 1.15rem;
}

.logo-beacon {
  font-size: 1.4rem;
  color: var(--accent-primary);
}

.nav-links {
  display: flex;
  gap: 28px;
}

.nav-link {
  color: var(--text-muted);
  text-decoration: none;
  font-size: 0.95rem;
  font-weight: 500;
  transition: var(--transition);
}

.nav-link:hover, .nav-link.active {
  color: var(--text-main);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: var(--success);
  font-size: 0.75rem;
  font-weight: 700;
  padding: 6px 12px;
  border-radius: 999px;
  letter-spacing: 0.5px;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--success);
  border-radius: 50%;
  animation: pulse-dot 2s infinite;
}

@keyframes pulse-dot {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.3); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.mobile-toggle {
  display: none;
  background: none;
  border: none;
  color: var(--text-main);
  font-size: 1.5rem;
  cursor: pointer;
}

/* Hero Section */
.hero-section {
  position: relative;
  padding: 96px 0 64px;
  text-align: center;
  overflow: hidden;
}

.hero-glow {
  position: absolute;
  top: 10%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 600px;
  height: 350px;
  background: radial-gradient(circle, var(--accent-glow) 0%, transparent 70%);
  filter: blur(60px);
  pointer-events: none;
  z-index: 0;
}

.hero-content {
  position: relative;
  z-index: 1;
}

.pill-tag {
  display: inline-block;
  background: rgba(255, 87, 34, 0.12);
  border: 1px solid rgba(255, 87, 34, 0.35);
  color: var(--accent-secondary);
  font-size: 0.85rem;
  font-weight: 600;
  padding: 6px 16px;
  border-radius: 999px;
  margin-bottom: 24px;
}

.hero-title {
  font-size: 3.2rem;
  font-weight: 800;
  line-height: 1.2;
  margin-bottom: 20px;
  letter-spacing: -1px;
}

.gradient-text {
  background: linear-gradient(135deg, #ff7a45 0%, #ffc069 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: 1.15rem;
  color: var(--text-muted);
  max-width: 680px;
  margin: 0 auto 36px;
}

.hero-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 56px;
}

.btn {
  display: inline-block;
  padding: 12px 28px;
  border-radius: var(--radius-md);
  font-weight: 600;
  font-size: 0.95rem;
  text-decoration: none;
  cursor: pointer;
  transition: var(--transition);
  border: 1px solid transparent;
}

.btn-primary {
  background: linear-gradient(135deg, var(--accent-primary) 0%, var(--accent-secondary) 100%);
  color: #fff;
  box-shadow: 0 4px 20px var(--accent-glow);
}

.btn-primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(255, 87, 34, 0.5);
}

.btn-secondary {
  background: var(--bg-card);
  color: var(--text-main);
  border-color: var(--border-color);
}

.btn-secondary:hover {
  background: var(--bg-card-hover);
  border-color: rgba(255, 255, 255, 0.2);
}

.btn-block {
  width: 100%;
}

.hero-stats {
  display: flex;
  justify-content: center;
  gap: 32px;
  flex-wrap: wrap;
}

.stat-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 18px 32px;
  border-radius: var(--radius-lg);
  min-width: 160px;
}

.stat-number {
  font-size: 1.8rem;
  font-weight: 800;
  color: var(--text-main);
}

.stat-label {
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Services */
.services-section {
  padding: 80px 0;
  border-top: 1px solid var(--border-color);
}

.section-header {
  text-align: center;
  margin-bottom: 48px;
}

.section-title {
  font-size: 2.2rem;
  font-weight: 700;
  margin-bottom: 12px;
}

.section-desc {
  color: var(--text-muted);
  font-size: 1.05rem;
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 24px;
}

.service-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 32px;
  border-radius: var(--radius-lg);
  transition: var(--transition);
}

.service-card:hover {
  transform: translateY(-4px);
  border-color: rgba(255, 87, 34, 0.4);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.4);
}

.card-icon {
  font-size: 2.2rem;
  margin-bottom: 16px;
}

.service-card h3 {
  font-size: 1.25rem;
  margin-bottom: 12px;
}

.service-card p {
  color: var(--text-muted);
  font-size: 0.95rem;
}

/* About */
.about-section {
  padding: 80px 0;
  border-top: 1px solid var(--border-color);
}

.about-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 48px;
  align-items: center;
}

.prompt-quote {
  display: block;
  background: rgba(255, 255, 255, 0.04);
  padding: 10px 16px;
  border-left: 3px solid var(--accent-primary);
  border-radius: 4px;
  margin: 12px 0 16px;
  color: var(--text-main);
  font-style: normal;
}

.about-list {
  list-style: none;
  margin-top: 20px;
}

.about-list li {
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 0.95rem;
}

.about-preview-box {
  background: #05070c;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.6);
}

.code-preview-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #0f1420;
  border-bottom: 1px solid var(--border-color);
}

.code-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.code-dot.red { background: #ef4444; }
.code-dot.yellow { background: #f59e0b; }
.code-dot.green { background: #10b981; }

.code-title {
  margin-left: 8px;
  font-size: 0.8rem;
  font-family: var(--font-mono);
  color: var(--text-muted);
}

.code-snippet {
  padding: 24px;
  font-family: var(--font-mono);
  font-size: 0.9rem;
  color: #a5b4fc;
  overflow-x: auto;
}

/* Contact */
.contact-section {
  padding: 80px 0;
  border-top: 1px solid var(--border-color);
}

.contact-card {
  max-width: 580px;
  margin: 0 auto;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 40px;
  border-radius: var(--radius-lg);
}

.contact-header {
  text-align: center;
  margin-bottom: 28px;
}

.contact-header h2 {
  font-size: 1.8rem;
  margin-bottom: 8px;
}

.contact-header p {
  color: var(--text-muted);
}

.contact-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.form-group label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
}

.form-group input, .form-group textarea {
  background: rgba(10, 14, 23, 0.8);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  padding: 12px 16px;
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 0.95rem;
  outline: none;
  transition: var(--transition);
}

.form-group input:focus, .form-group textarea:focus {
  border-color: var(--accent-primary);
  box-shadow: 0 0 0 3px var(--accent-glow);
}

.form-feedback {
  padding: 12px;
  border-radius: var(--radius-md);
  font-size: 0.9rem;
  text-align: center;
}

.form-feedback.success {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid var(--success);
  color: var(--success);
}

.hidden {
  display: none;
}

/* Footer */
.footer {
  border-top: 1px solid var(--border-color);
  padding: 32px 0;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.footer-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.back-to-top {
  color: var(--accent-primary);
  text-decoration: none;
}

/* Mobile Responsiveness */
@media (max-width: 768px) {
  .hero-title { font-size: 2.2rem; }
  .about-grid { grid-template-columns: 1fr; }
  .nav-links {
    display: none;
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    background: #0a0e17;
    flex-direction: column;
    padding: 24px;
    border-bottom: 1px solid var(--border-color);
  }
  .nav-links.open { display: flex; }
  .mobile-toggle { display: block; }
}
"""

        # 3. app.js
        app_js = """// Idea Beacon - Interactive Web Client Controller
document.addEventListener('DOMContentLoaded', () => {
  // Mobile Nav Toggle
  const mobileToggle = document.getElementById('mobileToggle');
  const navLinks = document.getElementById('navLinks');
  if (mobileToggle && navLinks) {
    mobileToggle.addEventListener('click', () => {
      navLinks.classList.toggle('open');
    });
  }

  // Smooth Navigation Highlighting
  const sections = document.querySelectorAll('section');
  const navItems = document.querySelectorAll('.nav-link');

  window.addEventListener('scroll', () => {
    let current = '';
    sections.forEach(section => {
      const sectionTop = section.offsetTop;
      if (pageYOffset >= sectionTop - 120) {
        current = section.getAttribute('id');
      }
    });

    navItems.forEach(item => {
      item.classList.remove('active');
      if (item.getAttribute('href') === `#${current}`) {
        item.classList.add('active');
      }
    });
  });

  // Dynamic Latency Counter Simulation
  const latencyStat = document.getElementById('latencyStat');
  if (latencyStat) {
    setInterval(() => {
      const ms = Math.floor(Math.random() * 8) + 14;
      latencyStat.textContent = `${ms}ms`;
    }, 3500);
  }

  // Interactive Contact Form Submission
  const contactForm = document.getElementById('contactForm');
  const formFeedback = document.getElementById('formFeedback');
  const submitBtn = document.getElementById('submitBtn');

  if (contactForm && formFeedback) {
    contactForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('name').value;

      submitBtn.disabled = true;
      submitBtn.textContent = 'Submitting...';

      setTimeout(() => {
        formFeedback.className = 'form-feedback success';
        formFeedback.textContent = `✓ Thank you, ${name}! Your inquiry was recorded in this workspace.`;
        formFeedback.classList.remove('hidden');
        contactForm.reset();
        submitBtn.disabled = false;
        submitBtn.textContent = 'Send Message';
      }, 700);
    });
  }
});
"""

        # 4. README.md
        readme_md = f"""# {safe_title}

Created automatically by **Idea Beacon / Agentic Architect** via mobile voice prompt:
> *"{prompt}"*

## 📁 Project Structure

```text
{project_name}/
├── index.html        # Main landing page with responsive layout & semantic HTML
├── styles.css        # Modern glassmorphism & dark aesthetic tokens
├── app.js            # Interactive DOM scripts & form validation
├── package.json      # Node.js project manifest & preview scripts
└── README.md         # Project documentation
```

## 🚀 Live Preview & Development

This workspace is served dynamically by the Idea Beacon local preview server.

- **Localhost Preview:** `http://localhost:<PORT>`
- **Wi-Fi LAN Preview:** Access directly on your mobile phone browser via the generated LAN URL.

To run manually with Node.js / npx:
```bash
npx serve .
# or
python -m http.server 8080
```
"""

        # 5. package.json
        package_json = json.dumps({
            "name": project_name,
            "version": "1.0.0",
            "description": f"Generated web application for {safe_title}",
            "main": "index.html",
            "scripts": {
                "start": "npx serve .",
                "preview": "python -m http.server 8080"
            },
            "keywords": ["idea-beacon", "agentic-architect", "website"],
            "license": "MIT"
        }, indent=2)

        return {
            "index.html": index_html,
            "styles.css": styles_css,
            "app.js": app_js,
            "README.md": readme_md,
            "package.json": package_json
        }

    async def enrich_with_ollama(
        self,
        prompt: str,
        project_name: str,
        templates: Dict[str, str],
        model: str = DEFAULT_MODEL
    ) -> Dict[str, str]:
        """
        Enriches templates with customized content if local Ollama is active.
        If Ollama is offline or times out, immediately and safely returns original templates.
        """
        try:
            async with httpx.AsyncClient(timeout=2.0) as client:
                health = await client.get(f"{OLLAMA_BASE_URL}/api/tags")
                if health.status_code != 200:
                    return templates

                enrich_prompt = (
                    f"Create a catchy headline, tagline, and 3 feature names for a website described as: '{prompt}'. "
                    f'Return JSON only: {{"headline": "...", "tagline": "...", "features": ["...", "...", "..."]}}'
                )
                resp = await client.post(
                    f"{OLLAMA_BASE_URL}/api/generate",
                    json={
                        "model": model,
                        "prompt": enrich_prompt,
                        "stream": False,
                        "format": "json",
                        "options": {"temperature": 0.3, "num_predict": 512}
                    },
                    timeout=5.0
                )
                if resp.status_code == 200:
                    data = json.loads(resp.json().get("response", "{}"))
                    headline = data.get("headline")
                    tagline = data.get("tagline")
                    if headline and "index.html" in templates:
                        templates["index.html"] = templates["index.html"].replace(
                            "Autonomous Project Scaffolding", str(headline)
                        )
                    if tagline and "index.html" in templates:
                        templates["index.html"] = templates["index.html"].replace(
                            "Fast, responsive, and ready for deployment.", str(tagline)
                        )
        except Exception:
            # Completely non-blocking offline fallback
            pass
        return templates

    async def execute_new_project(
        self,
        prompt: str,
        preferred_port: Optional[int] = None
    ) -> OrchestrationResult:
        """
        Full orchestration pipeline:
        1. Interpret intent and extract project slug
        2. Create isolated workspace via ProjectManager
        3. Generate multi-file website assets (offline-first with optional Ollama tuning)
        4. Launch background LAN preview server via PreviewServerManager
        5. Update persistent metadata
        6. Return complete structured response
        """
        start_time = time.time()
        intent_type, slug, display_name = self.detect_intent(prompt)

        # Stage 1: Creating Workspace
        try:
            meta = self.pm.create_project(
                name_or_prompt=display_name,
                project_type="website",
                description=f"Generated from voice prompt: '{prompt}'",
                explicit_id=slug
            )
            workspace = meta.workspace_path
            project_id = meta.project_id
        except Exception as e:
            return OrchestrationResult(
                success=False,
                project_id=slug,
                project_name=display_name,
                workspace="",
                status="failed",
                stage="CREATING_WORKSPACE",
                error=f"Workspace creation failed: {str(e)}",
                message="Failed to allocate project directory"
            )

        # Stage 2: Generating Files (Offline-first templates with optional LLM tuning)
        files_created = []
        try:
            templates = self.generate_website_template(project_id, prompt)
            templates = await self.enrich_with_ollama(prompt, project_id, templates)
            for fname, content in templates.items():
                dest_file = os.path.join(workspace, fname)
                with open(dest_file, "w", encoding="utf-8") as f:
                    f.write(content)
                files_created.append(fname)
        except Exception as e:
            return OrchestrationResult(
                success=False,
                project_id=project_id,
                project_name=display_name,
                workspace=workspace,
                status="failed",
                stage="GENERATING_FILES",
                error=f"File writing failed: {str(e)}",
                message="Failed to write project files"
            )

        # Stage 3: Starting Preview Server
        try:
            preview_info = self.psm.start_preview(
                project_id=project_id,
                workspace_path=workspace,
                preferred_port=preferred_port
            )
            preview_url = preview_info.get("preview_url")
            preview_port = preview_info.get("port")
        except Exception as e:
            return OrchestrationResult(
                success=False,
                project_id=project_id,
                project_name=display_name,
                workspace=workspace,
                status="failed",
                stage="STARTING_PREVIEW",
                error=f"Preview server failed to start: {str(e)}",
                message="Project created but preview server failed to start"
            )

        # Stage 4: Update Persistent Metadata
        self.pm.update_project(
            project_id=project_id,
            status="running" if preview_url else "created",
            preview_port=preview_port,
            preview_url=preview_url,
            files=files_created
        )

        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        msg = f"Project '{display_name}' created successfully with {len(files_created)} files."
        if preview_url:
            msg += f" Live preview available at {preview_url}"

        return OrchestrationResult(
            success=True,
            project_id=project_id,
            project_name=display_name,
            workspace=workspace,
            status="running" if preview_url else "created",
            preview_url=preview_url,
            preview_port=preview_port,
            message=msg,
            files_created=files_created,
            stage="COMPLETED"
        )


# Global singleton instance
agent_orchestrator = AgentOrchestrator()
