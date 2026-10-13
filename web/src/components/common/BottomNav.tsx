import React, { useState } from 'react';
import { ScreenType } from '../../types';
import { LayoutGrid, Lightbulb, Plus, Sparkles, Terminal, ChevronUp, Cpu, History, Wifi } from 'lucide-react';

interface BottomNavProps {
  currentScreen: ScreenType;
  onNavigate: (screen: ScreenType) => void;
  ideaCount?: number;
}

export const BottomNav: React.FC<BottomNavProps> = ({
  currentScreen,
  onNavigate,
  ideaCount = 7,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);

  const navItems = [
    {
      id: 'dashboard' as ScreenType,
      label: 'Dash',
      icon: <LayoutGrid className="w-5 h-5 mb-0.5" />,
    },
    {
      id: 'vault' as ScreenType,
      label: 'Vault',
      icon: <Lightbulb className="w-5 h-5 mb-0.5" />,
      badge: ideaCount > 0 ? ideaCount : undefined,
    },
    // Center FAB spacer
    {
      id: 'capture' as ScreenType,
      label: 'Capture',
      isCenterAction: true,
      icon: <Plus className="w-6 h-6 stroke-[2.5]" />,
    },
    {
      id: 'brainstorm' as ScreenType,
      label: 'AI Hub',
      icon: <Sparkles className="w-5 h-5 mb-0.5" />,
    },
    {
      id: 'project' as ScreenType,
      label: 'Projects',
      icon: <Terminal className="w-5 h-5 mb-0.5" />,
    },
  ];

  return (
    <>
      {/* Expandable Navigation Drawer (Pattern 8) */}
      {isExpanded && (
        <div
          className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm transition-opacity"
          onClick={() => setIsExpanded(false)}
        >
          <div
            className="absolute bottom-20 inset-x-4 max-w-sm mx-auto bg-[#211f1d] border border-[#292524] rounded-2xl p-4 shadow-[0_12px_36px_rgba(0,0,0,0.8)] animate-in fade-in slide-in-from-bottom-4 duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between pb-3 mb-3 border-b border-[#292524]">
              <span className="text-[12px] font-mono-code uppercase tracking-wider text-[#ffb693]">
                Quick Deck Directives
              </span>
              <button
                onClick={() => setIsExpanded(false)}
                className="text-[11px] font-mono-code text-[#a8a29e] hover:text-[#faf8f5]"
              >
                Close ✕
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => {
                  setIsExpanded(false);
                  onNavigate('capture');
                }}
                className="p-3 rounded-xl bg-[#1d1b19] hover:bg-[#2c2927] border border-[#292524] text-left transition-colors flex flex-col gap-1.5"
              >
                <div className="w-7 h-7 rounded-lg bg-[#ff6b00]/20 text-[#ff6b00] flex items-center justify-center">
                  <Plus className="w-4 h-4" />
                </div>
                <span className="text-[12px] font-medium text-[#faf8f5]">New Spark</span>
                <span className="text-[10px] text-[#a8a29e]">Dictate or write</span>
              </button>

              <button
                onClick={() => {
                  setIsExpanded(false);
                  onNavigate('stepper');
                }}
                className="p-3 rounded-xl bg-[#1d1b19] hover:bg-[#2c2927] border border-[#292524] text-left transition-colors flex flex-col gap-1.5"
              >
                <div className="w-7 h-7 rounded-lg bg-[#ea9f00]/20 text-[#ea9f00] flex items-center justify-center">
                  <Cpu className="w-4 h-4" />
                </div>
                <span className="text-[12px] font-medium text-[#faf8f5]">Setup Stepper</span>
                <span className="text-[10px] text-[#a8a29e]">Pipeline step 3/4</span>
              </button>

              <button
                onClick={() => {
                  setIsExpanded(false);
                  onNavigate('timeline');
                }}
                className="p-3 rounded-xl bg-[#1d1b19] hover:bg-[#2c2927] border border-[#292524] text-left transition-colors flex flex-col gap-1.5"
              >
                <div className="w-7 h-7 rounded-lg bg-[#ffb693]/20 text-[#ffb693] flex items-center justify-center">
                  <History className="w-4 h-4" />
                </div>
                <span className="text-[12px] font-medium text-[#faf8f5]">Timeline Log</span>
                <span className="text-[10px] text-[#a8a29e]">Project chronology</span>
              </button>

              <button
                onClick={() => {
                  setIsExpanded(false);
                  onNavigate('details');
                }}
                className="p-3 rounded-xl bg-[#1d1b19] hover:bg-[#2c2927] border border-[#292524] text-left transition-colors flex flex-col gap-1.5"
              >
                <div className="w-7 h-7 rounded-lg bg-[#2c2927] text-[#ff6b00] flex items-center justify-center">
                  <Wifi className="w-4 h-4" />
                </div>
                <span className="text-[12px] font-medium text-[#faf8f5]">Daemon Bridge</span>
                <span className="text-[10px] text-[#a8a29e]">Ping localhost:7420</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main Bottom Nav Bar */}
      <nav
        className="fixed bottom-0 inset-x-0 z-40 bg-[#121110]/95 backdrop-blur-2xl border-t border-[#292524] shadow-[0_-8px_28px_rgba(0,0,0,0.65)]"
        aria-label="Bottom Navigation"
      >
        {/* Subtle expand handle pill */}
        <div className="w-full flex justify-center -mt-3.5 mb-0.5">
          <button
            onClick={() => setIsExpanded(!isExpanded)}
            title={isExpanded ? 'Collapse Navigation' : 'Expand Quick Deck'}
            className="px-3 py-0.5 rounded-full bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#a8a29e] hover:text-[#ff6b00] flex items-center gap-1 transition-all shadow-sm active:scale-95"
          >
            <ChevronUp className={`w-3 h-3 transition-transform ${isExpanded ? 'rotate-180' : ''}`} />
            <span className="text-[9px] font-mono-code uppercase font-semibold text-[#a8a29e]">Quick Deck</span>
          </button>
        </div>

        <div className="flex items-center justify-around px-2 h-16 max-w-lg mx-auto relative">
          {navItems.map((item) => {
            if (item.isCenterAction) {
              return (
                <div key={item.id} className="flex-shrink-0 px-2 -mt-4">
                  <button
                    onClick={() => onNavigate('capture')}
                    aria-label="Capture an idea"
                    className="flex items-center justify-center w-12 h-12 rounded-full bg-[#ff6b00] text-[#121110] shadow-[0_0_24px_rgba(255,107,0,0.45)] hover:bg-[#ff7a1a] active:scale-95 transition-all focus:outline-none focus:ring-2 focus:ring-[#ff6b00]"
                  >
                    {item.icon}
                  </button>
                </div>
              );
            }

            const isActive = currentScreen === item.id;

            return (
              <button
                key={item.id}
                onClick={() => onNavigate(item.id)}
                className={`flex-1 flex flex-col items-center justify-center min-w-0 py-1 transition-all relative ${
                  isActive
                    ? 'text-[#ff6b00] font-semibold'
                    : 'text-[#a8a29e] hover:text-[#e7e1de]'
                }`}
              >
                <div className="relative">
                  {item.icon}
                  {item.badge && (
                    <span className="absolute -top-1 -right-2 px-1 rounded-full text-[9px] font-mono-code bg-[#ff6b00] text-[#121110] font-bold">
                      {item.badge}
                    </span>
                  )}
                </div>
                <span className="text-[11px] tracking-wide whitespace-nowrap">
                  {item.label}
                </span>
                {isActive && (
                  <span className="w-1 h-1 rounded-full bg-[#ff6b00] mt-0.5" />
                )}
              </button>
            );
          })}
        </div>

        {/* Android Native Pill Indicator bar */}
        <div className="w-full flex justify-center pb-2 pt-0.5">
          <div className="w-32 h-1 rounded-full bg-[#373432]/80" />
        </div>
      </nav>
    </>
  );
};
