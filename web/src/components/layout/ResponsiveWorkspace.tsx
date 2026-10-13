import React, { useState, useEffect } from 'react';
import { ScreenType } from '../../types';
import { BridgeDiagnostics, bridgeService } from '../../services/bridgeService';
import { BridgeStatusModal } from '../common/BridgeStatusModal';
import { TextShimmer } from '../common/TextShimmer';
import {
  Server,
  Sparkles,
  Layers,
  FolderGit2,
  Terminal,
  Activity,
  Plus,
  RefreshCw,
  Search,
  ExternalLink,
} from 'lucide-react';

interface ResponsiveWorkspaceProps {
  currentScreen: ScreenType;
  onNavigate: (screen: ScreenType) => void;
  children: React.ReactNode;
  ideaCount: number;
}

/**
 * Responsive Workspace Layout
 * Replaces simulated phone bezel with a responsive, modern developer companion layout.
 * Orange-first minimalist theme, Inter typography, JetBrains Mono for code.
 */
export const ResponsiveWorkspace: React.FC<ResponsiveWorkspaceProps> = ({
  currentScreen,
  onNavigate,
  children,
  ideaCount,
}) => {
  const [isBridgeModalOpen, setIsBridgeModalOpen] = useState(false);
  const [bridgeDiag, setBridgeDiag] = useState<BridgeDiagnostics | null>(null);

  useEffect(() => {
    // Initial check and subscription to bridge status
    bridgeService.checkConnection();
    const unsub = bridgeService.subscribe((diag) => setBridgeDiag(diag));
    return unsub;
  }, []);

  const getScreenTitle = () => {
    switch (currentScreen) {
      case 'dashboard':
        return 'Developer Workspace';
      case 'capture':
        return 'Quick Idea Capture';
      case 'vault':
        return 'SQLite Idea Vault';
      case 'details':
        return 'Idea Details';
      case 'brainstorm':
        return 'AI Co-Pilot Hub';
      case 'project':
        return 'Connected Repositories';
      case 'stepper':
        return 'Environment Setup';
      case 'timeline':
        return 'Project Timeline';
      default:
        return 'Idea Beacon IQ';
    }
  };

  const isBridgeConnected = bridgeDiag?.status === 'CONNECTED';

  return (
    <div className="min-h-screen w-full bg-[#151311] text-[#faf8f5] flex flex-col antialiased selection:bg-[#ff6b00]/30 selection:text-[#faf8f5]">
      {/* Top Application Bar (Responsive) */}
      <header className="sticky top-0 z-40 w-full bg-[#121110]/95 backdrop-blur-md border-b border-[#292524] shadow-[0_4px_20px_rgba(0,0,0,0.5)]">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between gap-3">
          {/* Brand Logo & Title */}
          <div className="flex items-center gap-3 min-w-0">
            <button
              onClick={() => onNavigate('dashboard')}
              className="flex items-center gap-2.5 text-left group focus:outline-none"
            >
              <img
                alt="Idea Beacon App Logo"
                className="h-8 w-8 rounded-xl object-cover shadow-[0_2px_10px_rgba(255,107,0,0.35)] border border-[#ff6b00]/40 ring-1 ring-[#ff8533]/20 bg-[#24211e] group-hover:scale-105 transition-transform"
                src="/idea_beacon_logo.webp"
                onError={(e) => {
                  (e.currentTarget as HTMLImageElement).src = '/img.png';
                }}
                referrerPolicy="no-referrer"
              />
              <div className="flex flex-col min-w-0">
                <div className="flex items-center gap-1.5 leading-none">
                  <span className="text-[17px] font-bold tracking-tight text-[#faf8f5] truncate font-display">
                    Idea Beacon
                  </span>
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-mono-code font-bold uppercase bg-[#ff6b00]/20 text-[#ff6b00] border border-[#ff6b00]/30">
                    IQ
                  </span>
                </div>
                <span className="text-[11px] font-mono-code text-[#a8a29e] truncate mt-0.5">
                  {getScreenTitle()}
                </span>
              </div>
            </button>
          </div>

          {/* Desktop Navigation Links */}
          <nav className="hidden md:flex items-center gap-1 bg-[#1d1b19]/80 p-1 rounded-xl border border-[#292524]">
            <button
              onClick={() => onNavigate('dashboard')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                currentScreen === 'dashboard'
                  ? 'bg-[#271e16] text-[#ffb693] shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#24211e]'
              }`}
            >
              Dashboard
            </button>
            <button
              onClick={() => onNavigate('vault')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 transition-all ${
                currentScreen === 'vault'
                  ? 'bg-[#271e16] text-[#ffb693] shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#24211e]'
              }`}
            >
              <span>Idea Vault</span>
              <span className="px-1.5 py-0.2 rounded-full text-[10px] font-mono-code bg-[#211f1d] text-[#a8a29e]">
                {ideaCount}
              </span>
            </button>
            <button
              onClick={() => onNavigate('brainstorm')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                currentScreen === 'brainstorm'
                  ? 'bg-[#271e16] text-[#ffb693] shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#24211e]'
              }`}
            >
              AI Co-Pilot
            </button>
            <button
              onClick={() => onNavigate('project')}
              className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                currentScreen === 'project' || currentScreen === 'timeline' || currentScreen === 'stepper'
                  ? 'bg-[#271e16] text-[#ffb693] shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#24211e]'
              }`}
            >
              Projects
            </button>
          </nav>

          {/* Right Action Bar */}
          <div className="flex items-center gap-2">
            {/* Quick Capture Button */}
            <button
              onClick={() => onNavigate('capture')}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#ff6b00] hover:bg-[#ff8533] text-[#121110] font-semibold text-xs shadow-[0_2px_12px_rgba(255,107,0,0.3)] active:scale-95 transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4 stroke-[2.5]" />
              <span className="hidden sm:inline">Capture Idea</span>
            </button>

            {/* Laptop Bridge Connectivity Pill */}
            <button
              onClick={() => setIsBridgeModalOpen(true)}
              className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl border transition-all cursor-pointer ${
                isBridgeConnected
                  ? 'bg-[#271e16] border-[#ff6b00]/40 text-[#ffb693]'
                  : 'bg-[#1d1b19] border-[#292524] text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
              title="FastAPI Laptop Bridge Status & Settings"
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  isBridgeConnected
                    ? 'bg-[#ff6b00] shadow-[0_0_8px_#ff6b00]'
                    : bridgeDiag?.status === 'CONNECTING'
                    ? 'bg-[#ea9f00] animate-pulse'
                    : 'bg-[#78716c]'
                }`}
              />
              <span className="text-[11px] font-mono-code font-bold hidden sm:inline">
                {isBridgeConnected ? `Bridge (${bridgeDiag?.latencyMs ?? 0}ms)` : 'Bridge Offline'}
              </span>
              <Server className="w-3.5 h-3.5 sm:hidden" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Responsive Body */}
      <main className="flex-1 w-full max-w-6xl mx-auto flex flex-col px-4 sm:px-6 py-6 pb-24 md:pb-12">
        {children}
      </main>

      {/* Bridge Status & Settings Modal */}
      <BridgeStatusModal
        isOpen={isBridgeModalOpen}
        onClose={() => setIsBridgeModalOpen(false)}
      />
    </div>
  );
};
