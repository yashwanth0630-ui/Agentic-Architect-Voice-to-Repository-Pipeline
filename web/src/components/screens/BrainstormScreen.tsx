import React, { useState } from 'react';
import { ScreenType } from '../../types';
import {
  ArrowUp,
  ChevronDown,
  Sparkles,
  FolderPlus,
  Download,
  Check,
  RefreshCw,
  Globe,
  ExternalLink,
  BookmarkPlus,
  Layers,
  Cpu,
  Info,
} from 'lucide-react';
import { TextShimmer } from '../common/TextShimmer';
import { Alert } from '../common/Alert';
import { Stepper, StepperItem } from '../common/Stepper';
import { bridgeService } from '../../services/bridgeService';
import { BeaconDatabase } from '../../db/BeaconDatabase';

interface BrainstormScreenProps {
  onNavigate: (screen: ScreenType) => void;
}

type ModelOption = 'gemini-3.8-flash-search' | 'gemini-3.8-flash' | 'gemini-4-argon' | 'ollama';

interface SpecResponse {
  architectureFlow: string;
  recommendedLibraries: { name: string; version: string; reason?: string }[];
  requirements: { label: string; value: string }[];
  roadmap?: string[];
  tokenCount: string;
  sources?: { title: string; url: string }[];
  summary?: string;
  executionModel: string;
}

export const BrainstormScreen: React.FC<BrainstormScreenProps> = ({ onNavigate }) => {
  const [selectedModel, setSelectedModel] = useState<ModelOption>('gemini-3.8-flash-search');
  const [isModelDropdownOpen, setIsModelDropdownOpen] = useState(false);
  const [useSearchGrounding, setUseSearchGrounding] = useState(true);
  const [inputPrompt, setInputPrompt] = useState('');
  const [isGenerating, setIsGenerating] = useState(false);
  const [generationPhase, setGenerationPhase] = useState('Synthesizing kernel probe hooks & eBPF maps...');
  const [feedback, setFeedback] = useState<{ type: 'info' | 'success' | 'warning' | 'error'; message: string } | null>(null);

  // Active spec result initialized with verified eBPF architecture spec
  const [activeSpec, setActiveSpec] = useState<SpecResponse>({
    architectureFlow: 'Kernel Space (TC/XDP) → BPF RingBuffer (Async Bus) → User Space Exporter',
    recommendedLibraries: [
      { name: 'aya-rs', version: '0.1.30', reason: 'Async pure-Rust eBPF loader' },
      { name: 'tokio', version: '1.38.0', reason: 'Asynchronous event runtime' },
      { name: 'libbpf-rs', version: '0.23.0', reason: 'Low-level kernel map bindings' },
    ],
    requirements: [
      { label: 'Kernel', value: '>= 5.8 (BTF enabled)' },
      { label: 'Compiler', value: 'clang >= 14.0, llvm' },
      { label: 'Capabilities', value: 'CAP_BPF, CAP_NET_ADMIN' },
    ],
    roadmap: [
      'Phase 1: Ingress TC hook probe and ringbuffer memory maps',
      'Phase 2: Userspace async telemetry daemon with tokio',
      'Phase 3: Prometheus exporter metrics and performance validation',
    ],
    tokenCount: '4.2k tokens cached',
    sources: [
      { title: 'aya-rs Documentation & Rust eBPF Kernel Guide', url: 'https://aya-rs.dev' },
      { title: 'Linux Kernel Documentation: BPF Ring Buffer', url: 'https://docs.kernel.org/bpf/ringbuf.html' },
    ],
    summary: 'High-throughput eBPF network probe pipeline for sub-millisecond kernel socket metrics.',
    executionModel: 'gemini-3.8-flash',
  });

  const getTargetModelConfig = (choice: ModelOption) => {
    switch (choice) {
      case 'gemini-3.8-flash-search':
        return { model: 'gemini-3.8-flash', search: true, label: 'Gemini 3.8 Flash (Grounded)' };
      case 'gemini-3.8-flash':
        return { model: 'gemini-3.8-flash', search: false, label: 'Gemini 3.8 Flash' };
      case 'gemini-4-argon':
        return { model: 'gemini-4-argon', search: false, label: 'Gemini 4 Argon' };
      case 'ollama':
        return { model: 'ollama', search: false, label: 'Ollama (via Laptop Bridge)' };
    }
  };

  const handleSelectModel = (choice: ModelOption) => {
    setSelectedModel(choice);
    setIsModelDropdownOpen(false);
    if (choice === 'gemini-3.8-flash-search') {
      setUseSearchGrounding(true);
    } else {
      setUseSearchGrounding(false);
    }
  };

  const handleSendMessage = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!inputPrompt.trim() && !isGenerating) return;

    const userText = inputPrompt.trim() || 'Refine telemetry hook schema';
    setInputPrompt('');
    setIsGenerating(true);

    const config = getTargetModelConfig(selectedModel);

    if (config.model === 'ollama') {
      setGenerationPhase('Routing request to local Ollama via Laptop Bridge (qwen2.5-coder)...');
    } else if (config.model === 'gemini-4-argon') {
      setGenerationPhase('Engaging Gemini 4 Argon deep architectural reasoning...');
    } else if (config.search) {
      setGenerationPhase('Querying Google Search & verifying live specifications with Gemini 3.8 Flash...');
    } else {
      setGenerationPhase('Synthesizing architecture spec with Gemini 3.8 Flash...');
    }

    try {
      const bridgeUrl = bridgeService.getBridgeUrl();
      const response = await fetch('/api/ai/brainstorm', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          prompt: userText,
          model: config.model,
          useSearchGrounding: config.search,
          bridgeUrl,
        }),
      });

      const data = await response.json();

      if (!response.ok || !data.success) {
        const errorMsg = data.error?.message || (typeof data.error === 'string' ? data.error : data.message) || `HTTP ${response.status} from brainstorming service`;
        throw new Error(errorMsg);
      }

      // Process real structured response
      const spec = data.spec;
      const rawText = data.rawText || '';

      setActiveSpec({
        architectureFlow:
          spec?.architectureFlow ||
          `${userText.slice(0, 35)} Pipeline: Ingress Hook → Memory Bus → Userspace Exporter`,
        recommendedLibraries: spec?.recommendedLibraries?.length
          ? spec.recommendedLibraries
          : [
              { name: 'tokio', version: '1.38.0', reason: 'Async runtime' },
              { name: 'serde', version: '1.0.200', reason: 'Serialization' },
            ],
        requirements: spec?.requirements?.length
          ? spec.requirements
          : [
              { label: 'Runtime', value: 'Node.js >= 20 / Linux >= 5.8' },
              { label: 'Model', value: data.model || config.model },
            ],
        roadmap: spec?.roadmap || [
          'Phase 1: Architecture blueprint and baseline validation',
          'Phase 2: Core service implementation',
          'Phase 3: Telemetry, hardening, and test automation',
        ],
        tokenCount: `${Math.round(rawText.length / 4)} tokens processed`,
        sources: data.sources && data.sources.length > 0 ? data.sources : undefined,
        summary: spec?.summary || rawText.slice(0, 180),
        executionModel: data.model || config.model,
      });

      setFeedback({
        type: 'success',
        message: `Architecture spec updated via ${config.label}.`,
      });
      setTimeout(() => setFeedback(null), 3500);
    } catch (err: any) {
      console.error('Brainstorm error:', err);
      setFeedback({
        type: 'error',
        message: err.message || 'Brainstorming query failed. Verify backend service / bridge.',
      });
      setTimeout(() => setFeedback(null), 5000);
    } finally {
      setIsGenerating(false);
    }
  };

  const handleSaveToVault = async () => {
    try {
      const title = `${activeSpec.architectureFlow.slice(0, 45)}...`;
      const content = `## Architecture Flow\n${activeSpec.architectureFlow}\n\n## Recommended Libraries\n${activeSpec.recommendedLibraries
        .map((l) => `- **${l.name}** (${l.version})${l.reason ? `: ${l.reason}` : ''}`)
        .join('\n')}\n\n## Requirements\n${activeSpec.requirements
        .map((r) => `- **${r.label}**: ${r.value}`)
        .join('\n')}\n\n## Model\nGenerated with ${activeSpec.executionModel}`;

      // Save via bridge / IndexedDB
      const result = await bridgeService.captureIdea({
        title,
        category: 'architecture',
        preview: activeSpec.summary || activeSpec.architectureFlow.slice(0, 80),
        content,
        tags: ['brainstormed', 'ai-spec', activeSpec.executionModel],
        status: 'synced',
        isDemo: false,
      });

      if (result.idea && result.idea.id) {
        setFeedback({
          type: 'success',
          message: `Saved "${title.slice(0, 30)}" to Idea Vault (ID: ${result.idea.id})`,
        });
      } else {
        // Fallback to local in-memory DB
        BeaconDatabase.ideaDao.insert({
          title,
          category: 'architecture',
          preview: activeSpec.summary || activeSpec.architectureFlow.slice(0, 80),
          content,
          tags: ['brainstormed', 'ai-spec', activeSpec.executionModel],
          status: 'unsynced',
          isDemo: false,
          relativeTime: 'Just now',
        });
        setFeedback({
          type: 'info',
          message: 'Saved to local Idea Vault cache (queued for sync).',
        });
      }
      setTimeout(() => setFeedback(null), 3500);
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `Failed to save to Idea Vault: ${err.message}`,
      });
      setTimeout(() => setFeedback(null), 4000);
    }
  };

  const handleExportSpec = () => {
    const text = `# Architecture Specification
**Generated with**: ${activeSpec.executionModel}

## Architecture Flow
${activeSpec.architectureFlow}

## Recommended Libraries
${activeSpec.recommendedLibraries.map((l) => `- ${l.name} (${l.version})${l.reason ? `: ${l.reason}` : ''}`).join('\n')}

## Requirements
${activeSpec.requirements.map((r) => `- ${r.label}: ${r.value}`).join('\n')}

${activeSpec.roadmap ? `## Roadmap\n${activeSpec.roadmap.map((s) => `- ${s}`).join('\n')}` : ''}

${activeSpec.sources ? `## Grounded Sources\n${activeSpec.sources.map((s) => `- [${s.title}](${s.url})`).join('\n')}` : ''}
`;
    navigator.clipboard?.writeText(text);
    setFeedback({
      type: 'info',
      message: 'Architecture spec copied to clipboard as Markdown.',
    });
    setTimeout(() => setFeedback(null), 2500);
  };

  const stepperItems: StepperItem[] = (activeSpec.roadmap || [
    'Phase 1: Ingress hook probe',
    'Phase 2: Userspace async telemetry daemon',
    'Phase 3: Prometheus exporter metrics',
  ]).map((phase, idx) => ({
    id: idx + 1,
    label: phase.split(':')[0] || `Phase ${idx + 1}`,
    sublabel: phase.includes(':') ? phase.split(':')[1].trim() : phase,
    status: idx === 0 ? 'completed' : idx === 1 ? 'active' : 'pending',
  }));

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-2xl mx-auto space-y-4">
      {/* Toast Alert Feedback (Pattern 1) */}
      {feedback && (
        <Alert
          type={feedback.type}
          message={feedback.message}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Model Selector & Grounding Controls */}
      <div className="space-y-2">
        <div className="relative">
          <button
            type="button"
            onClick={() => setIsModelDropdownOpen(!isModelDropdownOpen)}
            className="w-full p-3 rounded-xl bg-[#1d1b19] border border-[#292524] flex items-center justify-between text-[12px] font-mono-code transition-colors hover:bg-[#24211e] cursor-pointer"
          >
            <div className="flex items-center gap-2 truncate">
              <Sparkles className="w-4 h-4 text-[#ff6b00] flex-shrink-0" />
              <span className="text-[#faf8f5] font-semibold">
                {selectedModel === 'gemini-3.8-flash-search' && 'Gemini 3.8 Flash (Google Search Grounded)'}
                {selectedModel === 'gemini-3.8-flash' && 'Gemini 3.8 Flash (High Throughput)'}
                {selectedModel === 'gemini-4-argon' && 'Gemini 4 Argon (Deep Reasoning)'}
                {selectedModel === 'ollama' && 'Ollama: qwen2.5-coder (Laptop Bridge)'}
              </span>
              <span className="text-[#78716c]">·</span>
              <span className="text-[#a8a29e] text-[11px]">
                {selectedModel === 'gemini-4-argon' ? 'Configurable Agent' : 'Production Model'}
              </span>
            </div>

            <div className="flex items-center gap-1 text-[#ffb693] font-bold">
              <span>Switch</span>
              <ChevronDown
                className={`w-3.5 h-3.5 transition-transform ${isModelDropdownOpen ? 'rotate-180' : ''}`}
              />
            </div>
          </button>

          {/* Model Dropdown Menu (Pattern 3) */}
          {isModelDropdownOpen && (
            <div className="absolute top-13 inset-x-0 z-30 p-1.5 rounded-xl bg-[#211f1d] border border-[#292524] shadow-2xl space-y-1">
              <button
                type="button"
                onClick={() => handleSelectModel('gemini-3.8-flash-search')}
                className="w-full text-left px-3 py-2 rounded-lg text-[12px] font-mono-code flex items-center justify-between hover:bg-[#2c2927] transition-colors"
              >
                <div>
                  <span className="text-[#faf8f5] font-semibold">Gemini 3.8 Flash (Google Search Grounded)</span>
                  <p className="text-[10px] text-[#ffb693]">Real-time web search grounding & live documentation</p>
                </div>
                {selectedModel === 'gemini-3.8-flash-search' && <Check className="w-4 h-4 text-[#ff6b00]" />}
              </button>

              <button
                type="button"
                onClick={() => handleSelectModel('gemini-3.8-flash')}
                className="w-full text-left px-3 py-2 rounded-lg text-[12px] font-mono-code flex items-center justify-between hover:bg-[#2c2927] transition-colors"
              >
                <div>
                  <span className="text-[#faf8f5] font-semibold">Gemini 3.8 Flash (High Throughput)</span>
                  <p className="text-[10px] text-[#a8a29e]">Fast architecture expansion and technical spec synthesis</p>
                </div>
                {selectedModel === 'gemini-3.8-flash' && <Check className="w-4 h-4 text-[#ff6b00]" />}
              </button>

              <button
                type="button"
                onClick={() => handleSelectModel('gemini-4-argon')}
                className="w-full text-left px-3 py-2 rounded-lg text-[12px] font-mono-code flex items-center justify-between hover:bg-[#2c2927] transition-colors"
              >
                <div>
                  <span className="text-[#faf8f5] font-semibold">Gemini 4 Argon (Deep Architecture Reasoning)</span>
                  <p className="text-[10px] text-[#a8a29e]">Complex multi-step reasoning & agent system design</p>
                </div>
                {selectedModel === 'gemini-4-argon' && <Check className="w-4 h-4 text-[#ff6b00]" />}
              </button>

              <button
                type="button"
                onClick={() => handleSelectModel('ollama')}
                className="w-full text-left px-3 py-2 rounded-lg text-[12px] font-mono-code flex items-center justify-between hover:bg-[#2c2927] transition-colors"
              >
                <div>
                  <span className="text-[#faf8f5] font-semibold">Ollama: qwen2.5-coder:7b</span>
                  <p className="text-[10px] text-[#a8a29e]">Local execution via configured laptop bridge daemon</p>
                </div>
                {selectedModel === 'ollama' && <Check className="w-4 h-4 text-[#ff6b00]" />}
              </button>
            </div>
          )}
        </div>

        {/* Mixed-Content Advisory for Ollama on HTTPS */}
        {selectedModel === 'ollama' && typeof window !== 'undefined' && window.location.protocol === 'https:' && (
          <div className="p-2.5 rounded-xl bg-[#241a14] border border-[#ff6b00]/30 flex items-start gap-2 text-[11px] font-mono-code text-[#ffb693]">
            <Info className="w-4 h-4 text-[#ff6b00] flex-shrink-0 mt-0.5" />
            <div>
              <span className="font-bold text-[#faf8f5]">Browser HTTPS Security:</span> Direct browser calls to insecure <code className="text-[#ffbd58]">http://localhost</code> are blocked by modern browsers on HTTPS sites. Connect via a secure tunnel (e.g. Tailscale Funnel or ngrok) or access the workspace locally.
            </div>
          </div>
        )}

        {/* Search Grounding Banner */}
        <div className="p-2.5 rounded-xl bg-[#1d1b19] border border-[#292524] flex items-center justify-between text-[11px] font-mono-code">
          <div className="flex items-center gap-2">
            <Globe className="w-3.5 h-3.5 text-[#ff6b00]" />
            <span className="text-[#faf8f5]">Google Search Grounding</span>
            <span className="text-[10px] text-[#a8a29e]">(gemini-3.8-flash)</span>
          </div>
          <button
            type="button"
            onClick={() => {
              const nextState = !useSearchGrounding;
              setUseSearchGrounding(nextState);
              if (nextState) {
                setSelectedModel('gemini-3.8-flash-search');
              } else if (selectedModel === 'gemini-3.8-flash-search') {
                setSelectedModel('gemini-3.8-flash');
              }
            }}
            className={`px-2.5 py-0.5 rounded-full transition-all cursor-pointer ${
              useSearchGrounding
                ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-[0_0_8px_rgba(255,107,0,0.3)]'
                : 'bg-[#24211e] text-[#a8a29e]'
            }`}
          >
            {useSearchGrounding ? 'Active' : 'Disabled'}
          </button>
        </div>
      </div>

      {/* Active AI Generation State with TEXT SHIMMER (Pattern 4) */}
      {isGenerating ? (
        <div className="rounded-2xl bg-[#1d1b19] border border-[#ff6b00]/40 p-4 space-y-3 shadow-[0_0_20px_rgba(255,107,0,0.15)] animate-pulse">
          <div className="flex items-center gap-2">
            <Sparkles className="w-4 h-4 text-[#ff6b00] animate-spin" />
            <TextShimmer className="text-[13px] font-mono-code font-semibold">
              {generationPhase}
            </TextShimmer>
          </div>
          <div className="h-1 bg-[#24211e] rounded-full overflow-hidden">
            <div className="h-full bg-[#ff6b00] rounded-full animate-progress" />
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-between px-1 text-[11px] font-mono-code text-[#78716c]">
          <div className="flex items-center gap-2 text-[#ea9f00]">
            <span className="w-2 h-2 rounded-full bg-[#ff6b00]" />
            <span>Ready for architecture synthesis</span>
          </div>
          <span>Model: {activeSpec.executionModel}</span>
        </div>
      )}

      {/* Structured Blueprint Output Cards */}
      <div className="space-y-3 pt-1">
        {/* Architecture Flow */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <h3 className="text-[13px] font-bold text-[#faf8f5] font-display flex items-center gap-1.5">
              <Layers className="w-3.5 h-3.5 text-[#ff6b00]" />
              <span>Architecture Flow</span>
            </h3>
            <span className="text-[10px] font-mono-code text-[#78716c]">{activeSpec.tokenCount}</span>
          </div>
          <div className="p-3.5 rounded-xl bg-[#1d1b19] border border-[#292524] text-[12px] font-mono-code text-[#e2bfb0] leading-relaxed break-words">
            {activeSpec.architectureFlow}
          </div>
        </div>

        {/* Recommended Libraries */}
        <div className="space-y-1.5">
          <h3 className="text-[13px] font-bold text-[#faf8f5] font-display flex items-center gap-1.5">
            <Cpu className="w-3.5 h-3.5 text-[#ff6b00]" />
            <span>Recommended Libraries</span>
          </h3>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-left font-mono-code text-[12px]">
            {activeSpec.recommendedLibraries.map((lib) => (
              <div
                key={lib.name}
                className="p-2.5 rounded-xl bg-[#1d1b19] border border-[#292524] flex flex-col justify-between space-y-1"
              >
                <div className="flex items-center justify-between">
                  <span className="text-[#ffb693] font-bold truncate">{lib.name}</span>
                  <span className="text-[10px] text-[#78716c]">{lib.version}</span>
                </div>
                {lib.reason && (
                  <p className="text-[10px] text-[#a8a29e] line-clamp-2 leading-tight">{lib.reason}</p>
                )}
              </div>
            ))}
          </div>
        </div>

        {/* Environment Requirements */}
        <div className="space-y-1.5">
          <h3 className="text-[13px] font-bold text-[#faf8f5] font-display flex items-center gap-1.5">
            <Info className="w-3.5 h-3.5 text-[#ff6b00]" />
            <span>Environment Requirements</span>
          </h3>
          <div className="p-3 rounded-xl bg-[#1d1b19] border border-[#292524] space-y-2 text-[12px] font-mono-code">
            {activeSpec.requirements.map((req) => (
              <div key={req.label} className="flex items-center justify-between">
                <span className="text-[#78716c]">{req.label}:</span>
                <span className="text-[#faf8f5] font-medium">{req.value}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Execution Roadmap (Pattern 6: Stepper) */}
        {activeSpec.roadmap && (
          <div className="space-y-1.5 pt-1">
            <h3 className="text-[13px] font-bold text-[#faf8f5] font-display">
              Implementation Stepper
            </h3>
            <div className="p-3.5 rounded-xl bg-[#1d1b19] border border-[#292524]">
              <Stepper steps={stepperItems} orientation="vertical" />
            </div>
          </div>
        )}

        {/* Google Search Grounding Sources (When present) */}
        {activeSpec.sources && activeSpec.sources.length > 0 && (
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center gap-1.5 text-[12px] font-mono-code text-[#ffb693]">
              <Globe className="w-3.5 h-3.5 text-[#ff6b00]" />
              <span>Google Search Grounded Citations</span>
            </div>
            <div className="space-y-1.5">
              {activeSpec.sources.map((s, idx) => (
                <a
                  key={idx}
                  href={s.url}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="p-2.5 rounded-xl bg-[#1d1b19] hover:bg-[#24211e] border border-[#292524] flex items-center justify-between text-[11px] font-mono-code text-[#e2bfb0] transition-colors"
                >
                  <span className="truncate pr-2">{s.title}</span>
                  <ExternalLink className="w-3.5 h-3.5 text-[#ff6b00] flex-shrink-0" />
                </a>
              ))}
            </div>
          </div>
        )}

        {/* Action Buttons: Save to Vault, Create Project & Export Spec */}
        <div className="grid grid-cols-3 gap-2 pt-2">
          <button
            type="button"
            onClick={handleSaveToVault}
            className="h-11 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
            title="Save to Idea Vault (SQLite & IndexedDB)"
          >
            <BookmarkPlus className="w-4 h-4 text-[#ff6b00]" />
            <span>Save to Vault</span>
          </button>

          <button
            type="button"
            onClick={() => onNavigate('project')}
            className="h-11 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
          >
            <FolderPlus className="w-4 h-4 text-[#ff6b00]" />
            <span>Select project</span>
          </button>

          <button
            type="button"
            onClick={handleExportSpec}
            className="h-11 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
          >
            <Download className="w-4 h-4 text-[#ffbd58]" />
            <span>Export spec</span>
          </button>
        </div>
      </div>

      {/* Message Composer Input */}
      <form
        onSubmit={handleSendMessage}
        className="w-full bg-[#121110] rounded-xl p-2 border border-[#292524] flex items-center justify-between gap-2 shadow-inner focus-within:border-[#ff6b00]/60 transition-colors"
      >
        <input
          type="text"
          value={inputPrompt}
          onChange={(e) => setInputPrompt(e.target.value)}
          placeholder={
            useSearchGrounding
              ? 'Ask Beacon with Google Search grounding (gemini-3.8-flash)...'
              : 'Instruct Beacon co-pilot to expand or refine architecture...'
          }
          className="w-full bg-transparent text-[13px] text-[#faf8f5] placeholder:text-[#78716c] focus:outline-none px-2 font-mono-code"
        />
        <button
          type="submit"
          disabled={isGenerating}
          className="w-9 h-9 rounded-lg bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] flex items-center justify-center font-bold active:scale-95 transition-all shadow-[0_0_12px_rgba(255,107,0,0.3)] disabled:opacity-50 cursor-pointer flex-shrink-0"
          aria-label="Send query"
        >
          {isGenerating ? (
            <RefreshCw className="w-4 h-4 animate-spin" />
          ) : (
            <ArrowUp className="w-4 h-4 stroke-[3]" />
          )}
        </button>
      </form>
    </div>
  );
};
