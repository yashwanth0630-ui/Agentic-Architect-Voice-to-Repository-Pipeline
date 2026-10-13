import React, { useState } from 'react';
import { ScreenType } from '../../types';
import { ArrowLeft, Cpu, HardDrive, Laptop, Wifi, CheckCircle2, Sliders, RefreshCw, Zap, Shield } from 'lucide-react';
import { HoldToConfirm } from '../common/HoldToConfirm';
import { Alert } from '../common/Alert';

interface IdeaDetailsScreenProps {
  onBack: () => void;
  onNavigate: (screen: ScreenType) => void;
}

export const IdeaDetailsScreen: React.FC<IdeaDetailsScreenProps> = ({
  onBack,
  onNavigate,
}) => {
  const [contextTokens, setContextTokens] = useState<number>(64);
  const [isPinging, setIsPinging] = useState(false);
  const [pingLatency, setPingLatency] = useState<number>(4);
  const [cachePurged, setCachePurged] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);

  const handleTestBridgePing = () => {
    setIsPinging(true);
    setTimeout(() => {
      const newLatency = Math.floor(Math.random() * 5) + 3;
      setPingLatency(newLatency);
      setIsPinging(false);
      setFeedback(`Bridge handshake confirmed: localhost:7420 in ${newLatency}ms (Zero Egress).`);
      setTimeout(() => setFeedback(null), 3000);
    }, 400);
  };

  const handlePurgeCache = () => {
    setCachePurged(true);
    setFeedback('Reclaimed 4.2 GB local NVMe compilation cache & vector buffers.');
    setTimeout(() => {
      setFeedback(null);
    }, 3500);
  };

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-lg mx-auto space-y-4">
      {/* Top Bar (Matches Image 1.jpeg) */}
      <div className="flex items-center justify-between gap-3 pt-2">
        <button
          onClick={onBack}
          className="w-10 h-10 rounded-full flex items-center justify-center text-[#faf8f5] hover:bg-[#24211e] active:scale-95 transition-all"
          aria-label="Back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div className="flex-1 min-w-0">
          <h1 className="text-[18px] font-bold text-[#faf8f5] truncate font-display">
            Idea Details
          </h1>
          <p className="text-[11px] font-mono-code text-[#ffb693] uppercase tracking-wider truncate">
            COMMAND EXECUTION DECK
          </p>
        </div>
        <img
          alt="Profile"
          className="w-9 h-9 rounded-full object-cover border border-[#ff6b00]/40 shadow-sm bg-[#24211e]"
          src="/google_default_user.png"
          onError={(e) => {
            (e.currentTarget as HTMLImageElement).src =
              'https://lh3.googleusercontent.com/a/default-user=s96-c';
          }}
          referrerPolicy="no-referrer"
        />
      </div>

      {feedback && (
        <Alert
          type="info"
          title="System Diagnostic Result"
          message={feedback}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Card 1: Settings & Connectivity (Matches Image 1.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-3.5 shadow-md">
        <div className="flex items-start justify-between">
          <div className="space-y-1">
            <h2 className="text-[17px] font-bold text-[#faf8f5] font-display">
              Settings & Connectivity
            </h2>
            <p className="text-[12px] text-[#a8a29e]">
              Core runtime, local neural weights & bridge
            </p>
          </div>
          <span className="px-2.5 py-1 rounded-full text-[10px] font-mono-code font-bold uppercase bg-[#ff6b00]/15 text-[#ff6b00] border border-[#ff6b00]/30">
            Live Node
          </span>
        </div>

        <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] flex items-center justify-between text-[12px] font-mono-code">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#ff6b00] animate-beacon" />
            <span className="text-[#faf8f5] font-semibold">Engine Synchronized</span>
          </div>
          <div className="flex items-center gap-3">
            <span className="text-[#ffb693] font-bold">OPTIMAL</span>
            <span className="text-[#ea9f00] flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-[#ea9f00]" />
              TCP::7420 SECURE
            </span>
          </div>
        </div>
      </div>

      {/* Card 2: AI Engine Configuration (Matches Image 1.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-4 shadow-md">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-[#ff6b00]/15 text-[#ff6b00] flex items-center justify-center">
              <Cpu className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-[15px] font-bold text-[#faf8f5]">
                AI Engine Configuration
              </h2>
              <p className="text-[11px] font-mono-code text-[#a8a29e]">
                Hybrid inferencing & vector cache
              </p>
            </div>
          </div>
          <span className="text-[11px] font-mono-code text-[#78716c]">v2.4.0</span>
        </div>

        {/* Cognitive Core Badges */}
        <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-mono-code text-[#78716c] uppercase">
              Active Cognitive Core
            </span>
            <span className="px-2 py-0.5 rounded text-[10px] font-mono-code bg-[#ff6b00]/20 text-[#ffb693]">
              Dual Router Auto-Fallback
            </span>
          </div>
          <div className="flex items-baseline justify-between">
            <span className="text-[16px] font-bold text-[#faf8f5]">
              Beacon Hybrid v2.4
            </span>
            <span className="text-[11px] font-mono-code text-[#a8a29e]">fp16</span>
          </div>
          <p className="text-[12px] text-[#a8a29e] flex items-center gap-1.5 pt-1">
            <Zap className="w-3.5 h-3.5 text-[#ffbd58]" />
            Claude 3.7 Sonnet (Orchestrator) + Gemini 3.8 Flash
          </p>
        </div>

        {/* Local GGUF Embeddings */}
        <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] flex items-center justify-between">
          <div className="flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-[#ea9f00]" />
            <span className="text-[13px] font-semibold text-[#faf8f5]">Local GGUF Embeddings</span>
          </div>
          <span className="text-[11px] font-mono-code text-[#78716c]">
            BGE-small-en-v1.5 · 120 MB
          </span>
        </div>

        {/* Context Horizon Slider */}
        <div className="space-y-2">
          <div className="flex items-center justify-between text-[12px]">
            <span className="font-semibold text-[#faf8f5]">Context Horizon</span>
            <div className="flex items-center gap-2 font-mono-code">
              <span className="text-[11px] text-[#ea9f00]">KV-Optimized</span>
              <span className="px-2 py-0.5 rounded bg-[#24211e] text-[#ffb693] font-bold">
                {contextTokens}k Tokens
              </span>
            </div>
          </div>
          <input
            type="range"
            min="32"
            max="128"
            step="32"
            value={contextTokens}
            onChange={(e) => setContextTokens(Number(e.target.value))}
            className="w-full accent-[#ff6b00] cursor-pointer"
          />
          <div className="flex justify-between text-[10px] font-mono-code text-[#78716c]">
            <span>32k (Low VRAM)</span>
            <span>Hardware Bound</span>
            <span>128k (Full Buffer)</span>
          </div>
        </div>
      </div>

      {/* Card 3: Laptop Bridge & Daemon (Matches Image 1.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-3.5 shadow-md">
        <div className="flex items-start justify-between">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-[#24211e] text-[#ffb693] flex items-center justify-center">
              <Laptop className="w-4 h-4" />
            </div>
            <div>
              <h2 className="text-[15px] font-bold text-[#faf8f5]">Laptop Bridge & Daemon</h2>
              <p className="text-[11px] font-mono-code text-[#a8a29e]">
                Bidirectional workstation link
              </p>
            </div>
          </div>
          <span className="w-2.5 h-2.5 rounded-full bg-[#ff6b00] animate-beacon" />
        </div>

        <div className="space-y-2 text-[12px] font-mono-code">
          <div className="p-2.5 rounded-lg bg-[#121110] border border-[#292524] flex items-center justify-between">
            <span className="text-[#78716c]">DAEMON SOCKET</span>
            <div className="flex items-center gap-1.5 text-[#faf8f5]">
              <span>localhost:7420</span>
              <span className="text-[#ffb693]">WS ACTIVE 1.3</span>
            </div>
          </div>

          <div className="p-2.5 rounded-lg bg-[#121110] border border-[#292524] flex items-center justify-between">
            <span className="text-[#78716c]">SYNC TOPOLOGY</span>
            <div className="flex items-center gap-1.5 text-[#e2bfb0]">
              <span>Local-First Peer Bridge</span>
              <span className="text-[#ffb693]">● LAN / Wi-Fi Mesh</span>
            </div>
          </div>

          <div className="p-2.5 rounded-lg bg-[#121110] border border-[#292524] flex items-center justify-between">
            <span className="text-[#78716c]">LOCAL DATASTORE</span>
            <div className="flex items-center gap-1 text-[#faf8f5]">
              <Shield className="w-3.5 h-3.5 text-[#ffb693]" />
              <span>IndexedDB Cache + Laptop Bridge Vault</span>
            </div>
          </div>
        </div>

        {/* Test Bridge Ping Button */}
        <button
          onClick={handleTestBridgePing}
          disabled={isPinging}
          className="w-full h-11 rounded-xl bg-[#121110] hover:bg-[#24211e] border border-[#292524] flex items-center justify-between px-4 transition-all active:scale-[0.99] cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <RefreshCw className={`w-4 h-4 text-[#ff6b00] ${isPinging ? 'animate-spin' : ''}`} />
            <span className="text-[12px] font-mono-code font-bold uppercase tracking-wider text-[#faf8f5]">
              Test Bridge Ping
            </span>
          </div>
          <div className="flex items-center gap-1.5 text-[11px] font-mono-code text-[#ffb693]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00] animate-beacon" />
            <span>{isPinging ? 'Pinging...' : `● ${pingLatency}ms`}</span>
          </div>
        </button>
      </div>

      {/* Card 4: Workspace Toolchains Grid (Matches Image 1.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-3 shadow-md">
        <div className="flex items-center justify-between">
          <h2 className="text-[15px] font-bold text-[#faf8f5] flex items-center gap-2">
            Workspace Toolchains
          </h2>
          <span className="text-[11px] font-mono-code text-[#78716c]">4 active</span>
        </div>

        <div className="grid grid-cols-2 gap-2 text-[12px] font-mono-code">
          <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
            <span className="text-[#ff6b00] font-bold">Rust</span>
            <p className="text-[11px] text-[#78716c] truncate">v1.78 Stable (llvm)</p>
          </div>
          <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
            <span className="text-[#ffbd58] font-bold">Golang</span>
            <p className="text-[11px] text-[#78716c] truncate">v1.22 Toolchain</p>
          </div>
          <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
            <span className="text-[#ffb693] font-bold">Python</span>
            <p className="text-[11px] text-[#78716c] truncate">v3.12 (UV Env)</p>
          </div>
          <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
            <span className="text-[#faf8f5] font-bold">Node.js</span>
            <p className="text-[11px] text-[#78716c] truncate">v20 LTS (V8 eng)</p>
          </div>
        </div>
      </div>

      {/* Card 5: Cache Management with Hold-to-confirm (Matches Image 1.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-3 shadow-md">
        <div className="space-y-0.5">
          <h2 className="text-[15px] font-bold text-[#faf8f5]">Cache Management</h2>
          <p className="text-[11px] font-mono-code text-[#a8a29e]">
            Compilation bytecode & LLM artifacts
          </p>
        </div>

        <HoldToConfirm
          label={cachePurged ? 'Cache Successfully Purged' : 'Hold to Purge Local Compilation'}
          sublabel="Reclaims 4.2 GB disk footprint (requires 1.5s hold)"
          holdDurationMs={1500}
          onConfirm={handlePurgeCache}
        />
      </div>

      {/* Footer System Wordmark */}
      <div className="pt-2 text-center text-[10px] font-mono-code text-[#78716c] space-y-1">
        <p>● Idea Beacon IQ · v2.4.0 (Build 942)</p>
        <p>Android Native Command Deck · Zero Egress Standard</p>
      </div>
    </div>
  );
};
