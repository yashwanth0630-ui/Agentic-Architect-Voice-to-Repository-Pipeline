import React, { useState } from 'react';
import { ScreenType } from '../../types';
import { IdeaEntity } from '../../db/schemas';
import { Plus, ArrowRight, ChevronRight, Sparkles, Terminal, Cpu, Laptop, HardDrive, Wifi } from 'lucide-react';

interface DashboardScreenProps {
  ideas: IdeaEntity[];
  onNavigate: (screen: ScreenType) => void;
  onSelectIdea: (idea: IdeaEntity) => void;
}

export const DashboardScreen: React.FC<DashboardScreenProps> = ({
  ideas,
  onNavigate,
  onSelectIdea,
}) => {
  const [quickInput, setQuickInput] = useState('');
  const [isDictating, setIsDictating] = useState(false);

  // Take top 3 recent ideas
  const recentIdeas = ideas.slice(0, 3);

  const handleQuickCaptureSubmit = (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!quickInput.trim()) {
      onNavigate('capture');
      return;
    }
    // Navigate to capture with prefill or state
    sessionStorage.setItem('prefill_title', quickInput);
    setQuickInput('');
    onNavigate('capture');
  };

  const toggleVoiceCapture = () => {
    setIsDictating(!isDictating);
    if (!isDictating) {
      if (navigator.vibrate) navigator.vibrate(30);
      setTimeout(() => {
        setQuickInput('Kernel TC/XDP socket latency probe with ring buffer');
        setIsDictating(false);
      }, 1500);
    }
  };

  return (
    <div className="flex flex-col w-full px-5 pt-4 pb-28 space-y-5 max-w-lg mx-auto">
      {/* Greeting & Minimal Brand Header (Matches Image 3.jpeg) */}
      <div className="space-y-1">
        <div className="flex items-center gap-2 mb-1.5">
          <span className="w-2 h-2 rounded-full bg-[#ff6b00]" />
          <span className="text-[12px] font-medium tracking-wide uppercase text-[#a8a29e] font-mono-code">
            Local Engine Synced
          </span>
        </div>
        <h1 className="text-[24px] font-semibold tracking-tight text-[#faf8f5] leading-tight font-display">
          Good evening, Alex
        </h1>
        <p className="text-[14px] text-[#e2bfb0]">
          Everything is saved offline on your device.
        </p>
      </div>

      {/* Primary & Secondary Actions (Matches Image 3.jpeg) */}
      <div className="space-y-2.5 pt-1">
        {/* Primary Action: Prominent Orange Capture Button */}
        <button
          onClick={() => onNavigate('capture')}
          className="w-full h-12 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-medium text-[15px] flex items-center justify-between px-4 transition-all shadow-[0_4px_16px_rgba(255,107,0,0.22)] active:scale-[0.99] cursor-pointer"
        >
          <div className="flex items-center gap-3 whitespace-nowrap min-w-0">
            <div className="w-6 h-6 rounded-full bg-[#121110]/15 flex items-center justify-center">
              <Plus className="w-4 h-4 stroke-[3]" />
            </div>
            <span className="font-semibold whitespace-nowrap text-[#121110]">
              Capture an idea
            </span>
          </div>
          <ArrowRight className="w-5 h-5 flex-shrink-0 opacity-80" />
        </button>

        {/* Secondary Action: Visually quieter warm neutral button */}
        <button
          onClick={() => onNavigate('brainstorm')}
          className="w-full h-11 rounded-xl bg-[#24211e] hover:bg-[#2c2927] text-[#faf8f5] font-medium text-[14px] flex items-center justify-between px-4 transition-all border border-[#292524] active:scale-[0.99] cursor-pointer"
        >
          <div className="flex items-center gap-3 whitespace-nowrap min-w-0">
            <Sparkles className="w-4 h-4 text-[#ffbd58] flex-shrink-0" />
            <span className="text-[#faf8f5] whitespace-nowrap">Brainstorm with AI</span>
          </div>
          <ChevronRight className="w-4 h-4 text-[#a8a29e] flex-shrink-0" />
        </button>
      </div>

      {/* Quick Ingestion Bar */}
      <form
        onSubmit={handleQuickCaptureSubmit}
        className="w-full bg-[#121110] rounded-xl p-2.5 border border-[#292524] flex items-center justify-between gap-2 shadow-inner"
      >
        <div className="flex items-center gap-2 flex-1 min-w-0 pl-1">
          <span className="text-[13px] font-mono-code text-[#ff6b00] select-none">&gt;</span>
          <input
            type="text"
            value={quickInput}
            onChange={(e) => setQuickInput(e.target.value)}
            placeholder="Quick spark prompt or directive..."
            className="w-full bg-transparent text-[13px] text-[#faf8f5] placeholder:text-[#78716c] focus:outline-none truncate"
          />
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          <button
            type="button"
            onClick={toggleVoiceCapture}
            className={`w-8 h-8 rounded-lg flex items-center justify-center transition-all ${
              isDictating
                ? 'bg-[#ff6b00] text-[#121110] animate-pulse'
                : 'bg-[#211f1d] text-[#ffb693] hover:bg-[#2c2927]'
            }`}
            title="Dictate Audio Spark"
          >
            <span className="text-[11px] font-mono-code font-bold">MIC</span>
          </button>
          <button
            type="submit"
            className="w-8 h-8 rounded-lg bg-[#ff6b00] text-[#121110] flex items-center justify-center font-bold active:scale-95 transition-all shadow-[0_0_12px_rgba(255,107,0,0.3)]"
            title="Store Prompt"
          >
            <ArrowRight className="w-4 h-4" />
          </button>
        </div>
      </form>

      {/* Recent Ideas Clean List (Matches Image 3.jpeg) */}
      <div className="pt-2 space-y-2">
        <div className="flex items-center justify-between">
          <h2 className="text-[18px] font-semibold text-[#faf8f5] tracking-tight whitespace-nowrap font-display">
            Recent ideas
          </h2>
          <button
            onClick={() => onNavigate('vault')}
            className="text-[14px] font-medium text-[#ffb693] hover:text-[#ff7a1a] transition-colors"
          >
            View all
          </button>
        </div>

        {/* Clean, borderless list of 3 recent ideas */}
        <div className="divide-y divide-[#292524]/60">
          {recentIdeas.map((idea) => (
            <div
              key={idea.id}
              onClick={() => onSelectIdea(idea)}
              className="group py-3.5 flex items-center justify-between transition-colors hover:bg-[#24211e]/40 -mx-2 px-2 rounded-lg cursor-pointer"
            >
              <div className="space-y-1 min-w-0 pr-3 flex-1">
                <h3 className="text-[15px] font-medium text-[#faf8f5] truncate group-hover:text-[#ffb693] transition-colors">
                  {idea.title}
                </h3>
                <div className="flex items-center gap-2 text-[12px] text-[#a8a29e]">
                  <span className="font-mono-code text-[11px] px-1.5 py-0.5 rounded bg-[#ff6b00]/20 text-[#ffb693] font-medium whitespace-nowrap">
                    {idea.category}
                  </span>
                  <span className="text-[#a98a7d]">·</span>
                  <span className="text-[#e2bfb0]">{idea.relativeTime}</span>
                </div>
              </div>
              <ChevronRight className="w-5 h-5 text-[#78716c] group-hover:text-[#ff6b00] flex-shrink-0 transition-colors" />
            </div>
          ))}
        </div>
      </div>

      {/* Environment Bridges & Diagnostics Strip (Matches Stitch specs) */}
      <div className="space-y-2.5 pt-2">
        <div className="flex items-center justify-between">
          <h3 className="text-[14px] font-semibold text-[#faf8f5] flex items-center gap-2 font-display">
            <Cpu className="w-4 h-4 text-[#ff6b00]" />
            Environment Bridges
          </h3>
          <span className="text-[11px] font-mono-code text-[#ff6b00] bg-[#ff6b00]/10 px-2 py-0.5 rounded">
            PORT :7420
          </span>
        </div>

        <div className="grid grid-cols-2 gap-2.5">
          <button
            onClick={() => onNavigate('stepper')}
            className="p-3 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] flex flex-col items-start justify-between text-left shadow-sm active:scale-[0.98] transition-all cursor-pointer"
          >
            <div className="w-7 h-7 rounded-lg bg-[#121110] flex items-center justify-center text-[#ffb693] mb-2">
              <Terminal className="w-4 h-4" />
            </div>
            <span className="text-[13px] font-semibold text-[#faf8f5]">Verify Toolchains</span>
            <span className="text-[11px] font-mono-code text-[#78716c] mt-0.5">Rust 1.78 · Go 1.22</span>
          </button>

          <button
            onClick={() => onNavigate('details')}
            className="p-3 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] flex flex-col items-start justify-between text-left shadow-sm active:scale-[0.98] transition-all cursor-pointer"
          >
            <div className="w-7 h-7 rounded-lg bg-[#121110] flex items-center justify-center text-[#ff6b00] mb-2">
              <Laptop className="w-4 h-4" />
            </div>
            <span className="text-[13px] font-semibold text-[#faf8f5]">Laptop Bridge</span>
            <span className="text-[11px] font-mono-code text-[#ff6b00] mt-0.5">Active :7420 (4ms)</span>
          </button>
        </div>
      </div>

      {/* Mini Telemetry status footer */}
      <div className="pt-2 flex items-center justify-between text-[#78716c] text-[11px] font-mono-code border-t border-[#292524]/60">
        <div className="flex items-center gap-1.5">
          <HardDrive className="w-3.5 h-3.5 text-[#ffb693]" />
          <span>4.2 GB Cache / NVMe</span>
        </div>
        <div className="flex items-center gap-1.5">
          <Wifi className="w-3.5 h-3.5 text-[#ffbd58]" />
          <span>Bridge 0 Cloud egress</span>
        </div>
      </div>
    </div>
  );
};
