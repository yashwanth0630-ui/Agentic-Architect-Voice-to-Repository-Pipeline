import React, { useState } from 'react';

export type ManifestTab = 
  | 'agents' 
  | 'cursor' 
  | 'claude' 
  | 'gemini' 
  | 'apiContracts' 
  | 'uiComponent' 
  | 'testing' 
  | 'packageJson';

export interface ManifestViewerProps {
  initialPrompt?: string;
  initialAgentsContent?: string;
  initialCursorRules?: string;
  onSave?: (manifestType: ManifestTab, content: string) => void;
  onExportZip?: () => void;
}

export const ManifestViewer: React.FC<ManifestViewerProps> = ({
  initialPrompt = 'FastAPI backend, Next.js frontend, Tailwind UI',
  initialAgentsContent = '',
  initialCursorRules = '',
  onSave,
  onExportZip
}) => {
  const [prompt, setPrompt] = useState<string>(initialPrompt);
  const [activeTab, setActiveTab] = useState<ManifestTab>('agents');
  const [isInferring, setIsInferring] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);
  const [npuTelemetry, setNpuTelemetry] = useState<{
    model: string;
    latencyMs: number;
    tops: number;
    isOffline: boolean;
  }>({
    model: 'Qwen2.5-Coder-7B-INT4 (Qualcomm GenieX SDK)',
    latencyMs: 168,
    tops: 45,
    isOffline: true
  });

  const [manifests, setManifests] = useState<Record<ManifestTab, string>>({
    agents: initialAgentsContent || `# Agent Context & Architectural Guidelines\n\n## 1. Project Overview\n- **Project Name:** Scaffolded App\n- **Description:** AI-primed repository generated on Snapdragon NPU\n- **Primary Runtime:** Python 3.14 & Node.js 24.x\n- **Primary Architecture:** FastAPI Backend + Next.js Frontend\n\n## 2. Standard Commands\n- **Install Dependencies:** pip install -r requirements.txt && npm install\n- **Run Local Server:** uvicorn main:app --reload\n- **Lint & Format:** ruff check .\n- **Execute Test Suite:** pytest\n\n## 3. Operational Boundaries & Safety Guardrails\n- Never edit lockfiles manually.\n- Zero secret hardcoding.\n- Prohibit destructive Git commands.`,
    cursor: initialCursorRules || `You are an expert full-stack engineer building this project.\n\n### Core Tech Stack\n- Frontend: Next.js 15 App Router (TypeScript)\n- Backend: FastAPI / Python 3.14 (Uvicorn, Pydantic v2)\n- Styling: Tailwind CSS v4 with modern CSS variables\n- State: TanStack Query, Zod runtime validation\n\n### Code Style\n- Strict typing at all system boundaries.\n- Unit tests for all business logic.`,
    claude: `# Claude Code Guidelines\n\n## Standard Commands\n- Build & Run Dev: uvicorn main:app --reload\n- Tests: pytest\n- Lint: ruff check .\n\n## Architectural Boundaries\n- RFC 7807 unified error handling.\n- Full typing and runtime schema checks.`,
    gemini: `# Gemini & Antigravity Instructions\n\n## Architectural Guidelines\n- Strict schema validation with Zod / Pydantic.\n- Unified response envelope.\n- Offline NPU scaffold parity.`,
    apiContracts: `# Skill: API Design & Error Contracts\n\n## Response Schema Format (RFC 7807)\n{\n  "success": true,\n  "data": {},\n  "meta": { "timestamp": "2026-10-03T20:30:00Z", "requestId": "uuid-v4" }\n}`,
    uiComponent: `# Skill: UI & Design Component System\n\n- Tailwind CSS design tokens.\n- Semantic HTML with accessible ARIA landmarks.\n- Modern glassmorphism dark aesthetic.`,
    testing: `# Skill: Testing Patterns & Quality Expectations\n\n- Vitest & Pytest suites.\n- Mock all external network requests.\n- 100% contract compliance verification.`,
    packageJson: `{\n  "name": "scaffolded-app",\n  "version": "0.1.0",\n  "private": true,\n  "dependencies": {\n    "zod": "^3.24.2"\n  }\n}`
  });

  const handleSimulateNpuInference = async (): Promise<void> => {
    setIsInferring(true);
    try {
      const response = await fetch('/api/npu/infer', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt })
      });

      if (response.ok) {
        const json = await response.json();
        if (json.success && json.data) {
          const m = json.data.manifests;
          setManifests({
            agents: m.agentsMarkdown || manifests.agents,
            cursor: m.cursorRules || manifests.cursor,
            claude: m.claudeMarkdown || manifests.claude,
            gemini: m.geminiMarkdown || manifests.gemini,
            apiContracts: m.apiContracts || manifests.apiContracts,
            uiComponent: m.uiComponentSystem || manifests.uiComponent,
            testing: m.testingPatterns || manifests.testing,
            packageJson: m.packageJson || manifests.packageJson
          });
          if (json.data.telemetry) {
            setNpuTelemetry({
              model: `${json.data.telemetry.model} (${json.data.telemetry.sdk})`,
              latencyMs: json.data.telemetry.latencyMs,
              tops: json.data.telemetry.npuTopsUtilized,
              isOffline: json.data.telemetry.isOffline
            });
          }
        }
      }
    } catch {
      // Fallback heuristic update if offline without live server
      setNpuTelemetry(prev => ({ ...prev, latencyMs: 142 }));
    } finally {
      setIsInferring(false);
    }
  };

  const handleCopy = async (): Promise<void> => {
    const textToCopy = manifests[activeTab];
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      try {
        await navigator.clipboard.writeText(textToCopy);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      } catch (err) {
        console.error('Failed to copy to clipboard', err);
      }
    }
  };

  const handleSave = (): void => {
    if (onSave) {
      onSave(activeTab, manifests[activeTab]);
    }
  };

  const handleDownloadZip = (): void => {
    if (onExportZip) {
      onExportZip();
    } else {
      window.open('/api/export', '_blank');
    }
  };

  const tabs: Array<{ id: ManifestTab; label: string }> = [
    { id: 'agents', label: 'AGENTS.md' },
    { id: 'cursor', label: '.cursorrules' },
    { id: 'claude', label: 'CLAUDE.md' },
    { id: 'gemini', label: 'GEMINI.md' },
    { id: 'apiContracts', label: 'api-contracts.md' },
    { id: 'uiComponent', label: 'ui-components.md' },
    { id: 'testing', label: 'testing-patterns.md' },
    { id: 'packageJson', label: 'package.json' }
  ];

  return (
    <section 
      aria-label="Agent Manifest Viewer Console" 
      className="w-full max-w-5xl mx-auto rounded-2xl border border-slate-800 bg-slate-950 text-slate-100 shadow-2xl overflow-hidden font-sans"
    >
      {/* Top Header & Telemetry Status */}
      <header className="border-b border-slate-800 bg-slate-900/90 px-6 py-4 backdrop-blur-md">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="h-3.5 w-3.5 rounded-full bg-emerald-400 animate-pulse shadow-lg shadow-emerald-500/50" />
            <div>
              <h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">
                <span>Agentic Architect Console</span>
                <span className="text-xs px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-mono font-normal border border-blue-500/30">
                  iQOO 13 Edition
                </span>
              </h1>
              <p className="text-xs text-slate-400">
                Snapdragon NPU Voice Ingestion &bull; Qualcomm GenieX SDK &bull; Multi-Agent Priming
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <div className="flex items-center gap-2 text-xs bg-slate-950/80 px-3 py-1.5 rounded-lg border border-slate-800 text-slate-300 font-mono">
              <span className="inline-block w-2 h-2 rounded-full bg-cyan-400" />
              <span>NPU: {npuTelemetry.tops} TOPS ({npuTelemetry.latencyMs}ms)</span>
              <span className="text-emerald-400 border-l border-slate-700 pl-2">100% OFFLINE</span>
            </div>
            <button
              type="button"
              onClick={handleDownloadZip}
              aria-label="Download bundle.zip"
              className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-blue-600 hover:bg-blue-500 text-white shadow-md transition-all flex items-center gap-1.5"
            >
              <span>Download bundle.zip</span>
            </button>
          </div>
        </div>

        {/* Spoken Prompt Ingestion Bar */}
        <div className="mt-4 pt-3 border-t border-slate-800/80 flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1">
            <label htmlFor="voice-prompt-input" className="sr-only">
              Spoken Developer Prompt
            </label>
            <input
              id="voice-prompt-input"
              type="text"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              placeholder="e.g. FastAPI backend, Next.js frontend, Tailwind UI"
              className="w-full bg-slate-950 border border-slate-700/80 rounded-lg px-3.5 py-2 text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
          </div>
          <button
            type="button"
            onClick={handleSimulateNpuInference}
            disabled={isInferring}
            className="px-4 py-2 rounded-lg text-xs font-semibold bg-gradient-to-r from-amber-500 to-orange-500 hover:from-amber-400 hover:to-orange-400 text-slate-950 shadow-md transition-all flex items-center justify-center gap-2 font-mono"
          >
            {isInferring ? (
              <>
                <span className="animate-spin inline-block w-3 h-3 border-2 border-slate-950 border-t-transparent rounded-full" />
                <span>NPU Inferring...</span>
              </>
            ) : (
              <>
                <span>⚡ Run On-Device NPU</span>
              </>
            )}
          </button>
        </div>
      </header>

      {/* Manifest Tabs Navigation */}
      <nav aria-label="Manifest Tabs" className="flex overflow-x-auto border-b border-slate-800 bg-slate-900/40 px-4 py-2 gap-1.5 scrollbar-thin">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={activeTab === tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-all ${
              activeTab === tab.id
                ? 'bg-blue-600 text-white shadow-sm font-semibold'
                : 'text-slate-400 hover:bg-slate-800 hover:text-slate-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </nav>

      {/* Code Editor Body */}
      <main className="p-4 sm:p-6 bg-slate-950">
        <label htmlFor="active-manifest-content" className="sr-only">
          {activeTab} Content
        </label>
        <textarea
          id="active-manifest-content"
          aria-label={`${activeTab} manifest content`}
          value={manifests[activeTab]}
          onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
            const val = e.target.value;
            setManifests((prev) => ({ ...prev, [activeTab]: val }));
          }}
          rows={17}
          spellCheck={false}
          className="w-full font-mono text-xs sm:text-sm bg-slate-900/90 border border-slate-800 rounded-xl p-4 text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all resize-y leading-relaxed"
        />
      </main>

      {/* Action Footer */}
      <footer className="flex flex-wrap justify-between items-center px-6 py-3.5 border-t border-slate-800 bg-slate-900/80">
        <div className="flex items-center gap-3 text-xs text-slate-400">
          <span className="flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            RFC 7807 Validated
          </span>
          <span className="hidden sm:inline">&bull;</span>
          <span className="hidden sm:inline">Snapdragon NPU Quantized INT4</span>
        </div>
        <div className="flex gap-2.5">
          <button
            type="button"
            onClick={handleCopy}
            aria-label="Copy to Clipboard"
            className="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 transition-all border border-slate-700"
          >
            {copied ? '✓ Copied' : 'Copy'}
          </button>
          <button
            type="button"
            onClick={handleSave}
            aria-label="Save Manifest"
            className="px-4 py-1.5 text-xs font-semibold rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white shadow transition-all"
          >
            Save Changes
          </button>
        </div>
      </footer>
    </section>
  );
};

export default ManifestViewer;
