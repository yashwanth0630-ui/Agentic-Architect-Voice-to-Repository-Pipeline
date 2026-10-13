import React, { useState, useEffect } from 'react';
import { Wifi, Battery, Smartphone, Maximize2 } from 'lucide-react';

interface AndroidDeviceShellProps {
  children: React.ReactNode;
}

export const AndroidDeviceShell: React.FC<AndroidDeviceShellProps> = ({ children }) => {
  const [currentTime, setCurrentTime] = useState('10:42');
  const [isFrameEnabled, setIsFrameEnabled] = useState(false);

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(
        now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', hour12: false })
      );
    };
    updateTime();
    const interval = setInterval(updateTime, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div className="min-h-screen w-full bg-[#0d0c0a] flex flex-col items-center justify-start sm:p-4 md:p-6 transition-all">
      {/* Frame Switcher Floating Control */}
      <div className="fixed top-3 right-3 z-[60] flex items-center gap-1.5 p-1 rounded-full bg-[#1d1b19]/90 border border-[#292524] backdrop-blur-md shadow-lg">
        <button
          onClick={() => setIsFrameEnabled(!isFrameEnabled)}
          className={`flex items-center gap-1.5 px-3 py-1 rounded-full text-[11px] font-mono-code transition-all ${
            isFrameEnabled
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-[0_0_10px_rgba(255,107,0,0.3)]'
              : 'text-[#a8a29e] hover:text-[#faf8f5]'
          }`}
          title="Toggle Android Device Frame"
        >
          {isFrameEnabled ? <Smartphone className="w-3.5 h-3.5" /> : <Maximize2 className="w-3.5 h-3.5" />}
          <span>{isFrameEnabled ? 'Android Frame' : 'Full View'}</span>
        </button>
      </div>

      {/* Main Container */}
      <div
        className={`w-full transition-all duration-300 relative ${
          isFrameEnabled
            ? 'max-w-[420px] my-auto rounded-[44px] border-[10px] border-[#292524] shadow-[0_25px_60px_rgba(0,0,0,0.9),0_0_30px_rgba(255,107,0,0.1)] overflow-hidden bg-[#151311]'
            : 'max-w-lg min-h-screen bg-[#151311]'
        }`}
      >
        {/* Android Native Status Bar */}
        <div className="sticky top-0 inset-x-0 z-50 h-8 px-6 flex items-center justify-between text-[12px] font-mono-code text-[#a8a29e] bg-[#121110]/95 backdrop-blur-md select-none border-b border-[#292524]/40">
          <span className="font-semibold text-[#faf8f5]">{currentTime}</span>

          {/* Camera Punch Hole in center */}
          <div className="w-3.5 h-3.5 rounded-full bg-[#0d0c0a] border border-[#292524] flex items-center justify-center">
            <div className="w-1.5 h-1.5 rounded-full bg-[#1a1715]" />
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold text-[#ffb693]">5G</span>
            <Wifi className="w-3.5 h-3.5 text-[#e2bfb0]" />
            <div className="flex items-center gap-0.5">
              <span className="text-[10px]">98%</span>
              <Battery className="w-3.5 h-3.5 text-[#ff6b00]" />
            </div>
          </div>
        </div>

        {/* Screen Viewport */}
        <div className="relative w-full min-h-[calc(100vh-32px)] flex flex-col bg-[#151311]">
          {children}
        </div>
      </div>
    </div>
  );
};
