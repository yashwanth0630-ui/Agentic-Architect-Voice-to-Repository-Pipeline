import React, { useState, useEffect } from 'react';
import { IdeaEntity } from '../../db/schemas';
import { ArrowLeft, Check, Eye, Code, List, CheckSquare, Mic, Lock, ShieldCheck } from 'lucide-react';
import { Alert } from '../common/Alert';

/**
 * Automatically generates a readable title from idea content.
 * Strips code fences, markdown headers, list markers, links, and collapses whitespace.
 * Truncates at a clean word boundary up to maxLength (default 42) and appends an ellipsis.
 */
export function generateTitleFromContent(content: string, maxLength: number = 42): string {
  if (!content || !content.trim()) return '';

  let text = content.replace(/```[\s\S]*?```/g, ' ');
  if (!text.trim()) {
    text = content.replace(/```[a-zA-Z0-9_-]*/g, ' ');
  }

  const cleaned = text
    .replace(/`([^`]+)`/g, '$1')
    .replace(/^[\s]*[#>-]+(\s|\[[ xX]\])?/gm, ' ')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\*{1,2}([^*]+)\*{1,2}/g, '$1')
    .replace(/~~([^~]+)~~/g, '$1')
    .replace(/(?:^|\s)_(.+?)_(?:\s|$)/g, ' $1 ')
    .replace(/\s+/g, ' ')
    .trim();

  if (!cleaned) return '';

  if (cleaned.length <= maxLength) {
    return cleaned;
  }

  const truncated = cleaned.slice(0, maxLength);
  const lastSpace = truncated.lastIndexOf(' ');
  const wordBoundary = lastSpace > 15 ? lastSpace : maxLength;

  return cleaned.slice(0, wordBoundary).trim() + '...';
}

export interface CaptureScreenProps {
  onSave: (idea: Partial<IdeaEntity>) => void;
  onCancel: () => void;
  initialIdea?: IdeaEntity | null;
  saveDelayMs?: number;
}

export const CaptureScreen: React.FC<CaptureScreenProps> = ({
  onSave,
  onCancel,
  initialIdea,
  saveDelayMs,
}) => {
  const [title, setTitle] = useState(initialIdea?.title || '');
  const [content, setContent] = useState(
    initialIdea?.content ||
      `### Architecture Hypothesis
Inject eBPF tracepoints directly at \`tcp_v4_connect\` and \`tcp_finish_connect\` within kernel space to isolate sub-millisecond pod hop latency.

\`\`\`rust
// Kernel ring buffer attach point
#[kprobe(name = "tcp_connect")]
pub fn probe_tcp_connect(ctx: ProbeContext) -> u32 {
    let sock = ctx.arg::<*const sock>(0);
    // write trace timestamp to ring buffer
    0
}
\`\`\`

### Ingress Directives
- Map Type: BPF_MAP_TYPE_RINGBUF (64 pages)
- Export bus: aya-rs async ring exporter`
  );
  const [category, setCategory] = useState<IdeaEntity['category']>(initialIdea?.category || 'systems');
  const [previewMode, setPreviewMode] = useState(false);
  const [isDictating, setIsDictating] = useState(false);
  const [showSaveAlert, setShowSaveAlert] = useState(false);
  const [validationError, setValidationError] = useState('');

  useEffect(() => {
    if (initialIdea) {
      setTitle(initialIdea.title || '');
      setContent(initialIdea.content || '');
      setCategory(initialIdea.category || 'systems');
    } else {
      const prefill = sessionStorage.getItem('prefill_title');
      if (prefill) {
        setTitle(prefill);
        sessionStorage.removeItem('prefill_title');
      }
    }
  }, [initialIdea]);

  const handleSave = () => {
    const trimmedTitle = title.trim();
    const trimmedContent = content.trim();

    if (!trimmedTitle && !trimmedContent) {
      setValidationError('Please provide an idea title or description to store in local vault.');
      return;
    }

    const resolvedTitle = trimmedTitle || generateTitleFromContent(trimmedContent);
    if (!resolvedTitle) {
      setValidationError('Please provide an idea title or description to store in local vault.');
      return;
    }

    setValidationError('');

    const newIdea: Partial<IdeaEntity> = {
      ...(initialIdea ? { id: initialIdea.id } : {}),
      title: resolvedTitle,
      category,
      content,
      preview: content.replace(/[#`\n]/g, ' ').slice(0, 120).trim() + '...',
      tags: initialIdea?.tags || [`#${category}`, '#beacon', '#local'],
      status: 'unsynced',
      updatedAt: new Date().toISOString(),
      relativeTime: 'Edited just now',
    };

    setShowSaveAlert(true);
    const delay = saveDelayMs !== undefined ? saveDelayMs : 800;
    if (delay === 0) {
      onSave(newIdea);
    } else {
      setTimeout(() => {
        onSave(newIdea);
      }, delay);
    }
  };

  const insertSnippet = (snippet: string) => {
    setContent((prev) => prev + '\n' + snippet);
  };

  const toggleDictation = () => {
    setIsDictating(!isDictating);
    if (!isDictating) {
      if (navigator.vibrate) navigator.vibrate(50);
      setTimeout(() => {
        setContent((prev) => prev + '\n\n// Voice capture transcript:\n// Captured latency metric via socket eBPF hook at ingress.');
        setIsDictating(false);
      }, 2000);
    }
  };

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-lg mx-auto space-y-4">
      {/* Top Header: Back, Title, Orange Save Check Action (Matches Image 12.jpeg) */}
      <div className="flex items-center justify-between gap-3 pt-2">
        <button
          onClick={onCancel}
          className="w-10 h-10 rounded-full flex items-center justify-center text-[#faf8f5] hover:bg-[#24211e] active:scale-95 transition-all"
          aria-label="Back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h1 className="text-[20px] font-bold text-[#faf8f5] font-display flex-1">
          {initialIdea ? 'Edit Spark' : 'Capture'}
        </h1>
        <button
          onClick={handleSave}
          className="h-10 px-4 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-bold flex items-center justify-center gap-1.5 shadow-[0_0_16px_rgba(255,107,0,0.35)] active:scale-95 transition-all cursor-pointer"
          aria-label="Save idea"
        >
          <Check className="w-5 h-5 stroke-[3]" />
          <span className="text-[13px]">Save</span>
        </button>
      </div>

      {/* Local Storage & Security Strip */}
      <div className="flex items-center justify-between px-3 py-1.5 rounded-lg bg-[#1a1816] border border-[#292524] text-[#a8a29e] text-[11px] font-mono-code">
        <div className="flex items-center gap-2">
          <Lock className="w-3.5 h-3.5 text-[#ff6b00]" />
          <span>Local offline storage · IndexedDB vault</span>
        </div>
        <span className="flex items-center gap-1 text-[#e2bfb0]">
          <ShieldCheck className="w-3.5 h-3.5 text-[#ffbd58]" />
          Local-First Vault
        </span>
      </div>

      {/* Save Success Alert Feedback (Pattern 1) */}
      {showSaveAlert && (
        <Alert
          type="success"
          title="Spark Stored Successfully"
          message="Stored in local Idea Vault and queued for laptop bridge synchronization."
        />
      )}

      {/* Validation Error Alert (Pattern 1) */}
      {validationError && (
        <Alert
          type="error"
          title="Validation Warning"
          message={validationError}
          onDismiss={() => setValidationError('')}
        />
      )}

      {/* Idea Title Input (Matches Image 12.jpeg) */}
      <div className="space-y-1">
        <input
          type="text"
          value={title}
          onChange={(e) => {
            setTitle(e.target.value);
            if (validationError) setValidationError('');
          }}
          placeholder="eBPF-powered network latency probe for..."
          className="w-full bg-transparent text-[20px] font-semibold text-[#faf8f5] placeholder:text-[#78716c] focus:outline-none border-b border-[#292524] pb-2 pt-1 font-display"
        />
      </div>

      {/* Rapid Directive Category Presets */}
      <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-1">
        {(['systems', 'runtime', 'database', 'architecture', 'infra', 'wasm'] as IdeaEntity['category'][]).map(
          (cat) => (
            <button
              key={cat}
              type="button"
              onClick={() => setCategory(cat)}
              className={`px-2.5 py-1 rounded-md text-[11px] font-mono-code transition-all cursor-pointer ${
                category === cat
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-[0_0_10px_rgba(255,107,0,0.3)]'
                  : 'bg-[#211f1d] text-[#a8a29e] hover:bg-[#2c2927]'
              }`}
            >
              #{cat}
            </button>
          )
        )}
      </div>

      {/* Editor Box with Markdown Toolbar (Matches Image 12.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] overflow-hidden flex flex-col shadow-lg">
        {/* Formatting Toolbar */}
        <div className="flex items-center justify-between px-3 py-2 bg-[#211f1d] border-b border-[#292524]">
          <div className="flex items-center gap-3 text-[#a8a29e]">
            <button
              type="button"
              onClick={() => insertSnippet('```rust\n// code here\n```')}
              className="hover:text-[#faf8f5] p-1 rounded"
              title="Code Block"
            >
              <Code className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => insertSnippet('- Directive item')}
              className="hover:text-[#faf8f5] p-1 rounded"
              title="Bullet List"
            >
              <List className="w-4 h-4" />
            </button>
            <button
              type="button"
              onClick={() => insertSnippet('- [ ] Task to verify')}
              className="hover:text-[#faf8f5] p-1 rounded"
              title="Checkbox List"
            >
              <CheckSquare className="w-4 h-4" />
            </button>
            <span className="text-[#373432]">|</span>
            <span className="text-[11px] font-mono-code text-[#78716c]">Markdown v2</span>
          </div>

          <button
            type="button"
            onClick={() => setPreviewMode(!previewMode)}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-md text-[11px] font-mono-code transition-colors ${
              previewMode
                ? 'bg-[#ff6b00]/20 text-[#ff6b00] font-bold'
                : 'text-[#a8a29e] hover:text-[#faf8f5]'
            }`}
          >
            <Eye className="w-3.5 h-3.5" />
            <span>Preview</span>
          </button>
        </div>

        {/* Content Area / Preview */}
        {previewMode ? (
          <div className="p-4 min-h-[220px] max-h-[360px] overflow-y-auto space-y-3 text-[13px] leading-relaxed text-[#e7e1de] font-mono-code">
            <pre className="whitespace-pre-wrap font-mono-code bg-[#121110] p-3 rounded-xl border border-[#292524] text-[#faf8f5]">
              {content}
            </pre>
          </div>
        ) : (
          <textarea
            value={content}
            onChange={(e) => {
              setContent(e.target.value);
              if (validationError) setValidationError('');
            }}
            rows={10}
            className="w-full bg-[#121110] p-4 text-[13px] font-mono-code leading-relaxed text-[#faf8f5] placeholder:text-[#78716c] focus:outline-none resize-none border-0"
            placeholder="Write architecture specs, Rust kprobe hooks, or directives..."
          />
        )}

        {/* Bottom Directive Presets & Dictate Button (Matches Image 12.jpeg) */}
        <div className="flex items-center justify-between px-3 py-2.5 bg-[#1d1b19] border-t border-[#292524]">
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => insertSnippet('`code`')}
              className="px-2 py-0.5 rounded bg-[#24211e] hover:bg-[#2c2927] text-[11px] font-mono-code text-[#a8a29e]"
            >
              `code`
            </button>
            <button
              type="button"
              onClick={() => insertSnippet('{\n  "spec": "v1"\n}')}
              className="px-2 py-0.5 rounded bg-[#24211e] hover:bg-[#2c2927] text-[11px] font-mono-code text-[#a8a29e]"
            >
              {'{ }'}
            </button>
            <button
              type="button"
              onClick={() => insertSnippet('->')}
              className="px-2 py-0.5 rounded bg-[#24211e] hover:bg-[#2c2927] text-[11px] font-mono-code text-[#a8a29e]"
            >
              -&gt;
            </button>
          </div>

          <button
            type="button"
            onClick={toggleDictation}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full text-[12px] font-medium transition-all ${
              isDictating
                ? 'bg-[#ff6b00] text-[#121110] font-bold animate-pulse'
                : 'bg-[#24211e] text-[#ffb693] hover:bg-[#2c2927]'
            }`}
          >
            <Mic className="w-3.5 h-3.5 text-[#ff6b00]" />
            <span>{isDictating ? 'Recording...' : 'Dictate'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
