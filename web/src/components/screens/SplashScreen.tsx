import React, { useEffect, useState, useRef } from 'react';
import { ChevronRight, Volume2, VolumeX, Sparkles, Box, ShieldCheck, Zap } from 'lucide-react';

interface SplashScreenProps {
  onDismiss: () => void;
  autoDismissMs?: number;
}

type AnimationPhase = 'vector_draw' | 'triad_pillars' | 'convergence' | 'ready';

export const SplashScreen: React.FC<SplashScreenProps> = ({
  onDismiss,
  autoDismissMs = 3800,
}) => {
  const [progress, setProgress] = useState(0);
  const [phase, setPhase] = useState<AnimationPhase>('vector_draw');
  const [isClosing, setIsClosing] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(true);
  const audioContextRef = useRef<AudioContext | null>(null);

  // Synthesize ultra-clean ambient micro-tones (inspired by luxury tech films)
  const playChime = (freq: number, type: OscillatorType = 'sine', duration = 0.3) => {
    if (!soundEnabled) return;
    try {
      if (!audioContextRef.current) {
        const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
        audioContextRef.current = new AudioCtx();
      }
      const ctx = audioContextRef.current;
      if (ctx.state === 'suspended') {
        ctx.resume();
      }
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = type;
      osc.frequency.setValueAtTime(freq, ctx.currentTime);
      gain.gain.setValueAtTime(0.04, ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + duration);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start();
      osc.stop(ctx.currentTime + duration);
    } catch {
      // Audio autoplay policy fallback
    }
  };

  useEffect(() => {
    const startTime = Date.now();
    let hasPlayedChime = false;

    const interval = setInterval(() => {
      const elapsed = Date.now() - startTime;
      const current = Math.min(100, Math.round((elapsed / (autoDismissMs * 0.88)) * 100));
      setProgress(current);

      // Phase transitions matching The X Subscription choreography
      if (current < 30) {
        setPhase('vector_draw');
      } else if (current < 65) {
        setPhase('triad_pillars');
      } else if (current < 92) {
        setPhase('convergence');
      } else {
        setPhase('ready');
        if (!hasPlayedChime) {
          hasPlayedChime = true;
          playChime(587.33, 'sine', 0.5); // D5 chime
          setTimeout(() => playChime(880, 'sine', 0.6), 120); // A5 chime
        }
      }

      if (elapsed >= autoDismissMs) {
        clearInterval(interval);
        handleDismiss();
      }
    }, 40);

    return () => clearInterval(interval);
  }, [autoDismissMs, soundEnabled]);

  const handleDismiss = () => {
    setIsClosing(true);
    setTimeout(() => {
      onDismiss();
    }, 450);
  };

  // Status message according to animation phase
  const getPhaseTelemetry = () => {
    switch (phase) {
      case 'vector_draw':
        return 'VECTOR SYNTHESIS // MATRIX 01';
      case 'triad_pillars':
        return 'UNIFYING CORE SUBSCRIPTION ENGINES';
      case 'convergence':
        return 'BEACON NEURAL EMBLEM RESOLVED';
      case 'ready':
        return 'INITIALIZATION COMPLETE • READY';
    }
  };

  return (
    <aside
      aria-label="Welcome Loading Screen"
      className={`fixed inset-0 z-[100] flex flex-col items-center justify-between bg-[#121110] text-[#faf8f5] px-6 py-7 transition-all duration-500 ease-out select-none overflow-hidden ${
        isClosing ? 'opacity-0 scale-95 pointer-events-none' : 'opacity-100 pointer-events-auto'
      }`}
    >
      {/* 
        Background Technical Wireframe & Coordinate Grid 
        (Matching the high-contrast, razor-clean film aesthetic of The X Subscription)
      */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        {/* Fine crosshair micro-grid */}
        <div
          className="absolute inset-0 opacity-[0.04]"
          style={{
            backgroundImage: `
              linear-gradient(to right, #ff6b00 1px, transparent 1px),
              linear-gradient(to bottom, #ff6b00 1px, transparent 1px)
            `,
            backgroundSize: '48px 48px',
          }}
        />

        {/* Ambient Warm Amber Atmosphere */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[420px] h-[420px] rounded-full bg-[radial-gradient(circle,rgba(255,107,0,0.14)_0%,rgba(18,17,16,0)_70%)] blur-3xl animate-pulse" />

        {/* Precision Corner Technical Markers */}
        <div className="absolute top-4 left-4 font-mono-code text-[10px] tracking-widest text-[#a8a29e]/40 hidden sm:block">
          SYS.SYS // LATENCY 4ms // BEACON-V2
        </div>
        <div className="absolute top-4 right-4 font-mono-code text-[10px] tracking-widest text-[#a8a29e]/40 hidden sm:block">
          ENCRYPTION: QUANTUM_AES256
        </div>
        <div className="absolute bottom-4 left-4 font-mono-code text-[10px] tracking-widest text-[#a8a29e]/40 hidden sm:block">
          SUBSCRIPTION: UNIFIED CORE
        </div>
        <div className="absolute bottom-4 right-4 font-mono-code text-[10px] tracking-widest text-[#a8a29e]/40 hidden sm:block">
          NODE: OK // MEMORY 100%
        </div>
      </div>

      {/* Top Bar: Telemetry & Controls */}
      <header className="w-full flex items-center justify-between relative z-10 max-w-md">
        <div className="flex items-center gap-2.5 px-3 py-1.5 rounded-full bg-[#1c1917]/90 border border-[#292524] backdrop-blur-md">
          <span className="relative flex h-2 w-2">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-[#ff6b00] opacity-75" />
            <span className="relative inline-flex rounded-full h-2 w-2 bg-[#ff6b00]" />
          </span>
          <span className="text-[11px] font-mono-code text-[#ffb693] tracking-widest uppercase">
            {phase.toUpperCase().replace('_', ' ')}
          </span>
        </div>

        <div className="flex items-center gap-2">
          {/* Audio toggle button */}
          <button
            onClick={() => setSoundEnabled(!soundEnabled)}
            aria-label={soundEnabled ? 'Mute sound' : 'Enable sound'}
            className="p-1.5 rounded-full bg-[#1c1917]/80 hover:bg-[#24211e] text-[#a8a29e] hover:text-[#faf8f5] transition-all border border-[#292524]"
            title={soundEnabled ? 'Mute Audio' : 'Unmute Audio'}
          >
            {soundEnabled ? <Volume2 className="w-3.5 h-3.5" /> : <VolumeX className="w-3.5 h-3.5 opacity-60" />}
          </button>

          {/* Quick Skip button */}
          <button
            onClick={handleDismiss}
            className="text-[12px] font-mono-code font-medium text-[#a8a29e] hover:text-[#faf8f5] px-3.5 py-1.5 rounded-full bg-[#1c1917]/90 hover:bg-[#24211e] active:scale-95 transition-all border border-[#292524] flex items-center gap-1.5 backdrop-blur-md"
          >
            <span>SKIP</span>
            <ChevronRight className="w-3.5 h-3.5 text-[#ff6b00]" />
          </button>
        </div>
      </header>

      {/* 
        Central Cinematic Stage 
        Selector 1 path preserved: aside > div:nth-of-type(2) > div:nth-of-type(1) > div:nth-of-type(3)
        Selector 2 path preserved: aside > div:nth-of-type(2) > div:nth-of-type(3)
        Selector 3 path preserved: aside > div:nth-of-type(2) > div:nth-of-type(3) > span:nth-of-type(2)
      */}
      <div className="flex flex-col items-center text-center relative z-10 my-auto w-full max-w-sm">
        
        {/* Emblem & Morphing Stage: div:nth-of-type(1) */}
        <div className="relative flex flex-col items-center justify-center min-h-[160px] w-full mb-6">
          
          {/* Child 1: Subtle Ambient Glow behind emblem */}
          <div className="absolute w-36 h-36 rounded-full bg-[#ff6b00]/15 blur-2xl pointer-events-none transition-all duration-700" />

          {/* 
            Child 2: Phase 1 & 2 Morphing Stage:
            Animated Isometric Wireframe Cube & Triad Badges 
            (Directly inspired by The X Subscription announcement film)
          */}
          <div className={`transition-all duration-500 flex items-center justify-center ${
            phase === 'convergence' || phase === 'ready' ? 'opacity-0 scale-75 pointer-events-none absolute' : 'opacity-100 scale-100 relative'
          }`}>
            {phase === 'vector_draw' && (
              <div className="relative w-28 h-28 flex items-center justify-center">
                {/* 3D Isometric Wireframe Geometry (inspired by the Cursor 3D cube in The X Subscription film) */}
                <svg
                  className="w-24 h-24 text-[#ff6b00]"
                  viewBox="0 0 100 100"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                >
                  <polygon
                    points="50,15 85,35 50,55 15,35"
                    className="stroke-[#faf8f5] opacity-90 transition-all duration-700"
                    strokeDasharray="200"
                    strokeDashoffset={progress < 15 ? '100' : '0'}
                  />
                  <polygon
                    points="15,35 50,55 50,90 15,70"
                    className="stroke-[#ff6b00] opacity-80"
                    strokeDasharray="200"
                    strokeDashoffset={progress < 25 ? '100' : '0'}
                  />
                  <polygon
                    points="85,35 50,55 50,90 85,70"
                    className="stroke-[#ff8533] opacity-70"
                    strokeDasharray="200"
                    strokeDashoffset={progress < 25 ? '100' : '0'}
                  />
                  <circle cx="50" cy="55" r="2.5" fill="#ff6b00" />
                  <circle cx="50" cy="15" r="2" fill="#faf8f5" />
                  <circle cx="15" cy="35" r="2" fill="#ff6b00" />
                  <circle cx="85" cy="35" r="2" fill="#ff6b00" />
                </svg>
                <div className="absolute inset-0 rounded-full border border-[#ff6b00]/25 animate-spin [animation-duration:8s]" />
              </div>
            )}

            {phase === 'triad_pillars' && (
              /* The 3 Unified Subscription Engines: Ideation + Isometric Engine + Vault */
              <div className="flex items-center justify-center gap-3 sm:gap-5 py-2 transition-all duration-500">
                <div className="flex flex-col items-center gap-1.5 group">
                  <div className="w-12 h-12 rounded-xl bg-[#1c1917] border border-[#ff6b00]/50 flex items-center justify-center shadow-[0_0_16px_rgba(255,107,0,0.25)]">
                    <Sparkles className="w-5 h-5 text-[#ff6b00] animate-pulse" />
                  </div>
                  <span className="text-[9px] font-mono-code uppercase tracking-wider text-[#a8a29e]">NEURAL</span>
                </div>

                <div className="w-5 h-[1px] bg-gradient-to-r from-[#ff6b00]/60 to-[#faf8f5]/60" />

                <div className="flex flex-col items-center gap-1.5 group">
                  <div className="w-12 h-12 rounded-xl bg-[#1c1917] border border-[#faf8f5]/60 flex items-center justify-center shadow-[0_0_20px_rgba(250,248,245,0.25)]">
                    <Box className="w-5 h-5 text-[#faf8f5]" />
                  </div>
                  <span className="text-[9px] font-mono-code uppercase tracking-wider text-[#faf8f5]">ENGINE</span>
                </div>

                <div className="w-5 h-[1px] bg-gradient-to-r from-[#faf8f5]/60 to-[#ff6b00]/60" />

                <div className="flex flex-col items-center gap-1.5 group">
                  <div className="w-12 h-12 rounded-xl bg-[#1c1917] border border-[#ff6b00]/50 flex items-center justify-center shadow-[0_0_16px_rgba(255,107,0,0.25)]">
                    <ShieldCheck className="w-5 h-5 text-[#ff8533]" />
                  </div>
                  <span className="text-[9px] font-mono-code uppercase tracking-wider text-[#a8a29e]">VAULT</span>
                </div>
              </div>
            )}
          </div>

          {/* 
            Child 3: Pure brand emblem logo container (CSS Selector 1 target)
            Matches: aside > div:nth-of-type(2) > div:nth-of-type(1) > div:nth-of-type(3)
            Keeps the logo image pure, beautiful, and in the beacon warm theme
          */}
          <div
            className={`relative w-28 h-28 sm:w-32 sm:h-32 rounded-2xl p-2 bg-[#1c1917] border border-[#ff6b00]/60 shadow-[0_0_36px_rgba(255,107,0,0.35)] flex items-center justify-center overflow-hidden transition-all duration-700 ${
              phase === 'convergence' || phase === 'ready'
                ? 'opacity-100 scale-100 ring-2 ring-[#ff6b00]/30'
                : 'opacity-0 scale-75 absolute pointer-events-none'
            }`}
          >
            <img
              alt="Idea Beacon Brand Logo Emblem"
              className="w-full h-full object-cover rounded-xl drop-shadow-[0_4px_20px_rgba(255,107,0,0.4)]"
              src="/idea_beacon_logo.webp"
              onError={(e) => {
                (e.currentTarget as HTMLImageElement).src = '/img.png';
              }}
              referrerPolicy="no-referrer"
            />
          </div>
        </div>

        {/* 
          Kinetic Typography & Title Reveal: div:nth-of-type(2)
          Modeled directly after the high-fashion film title "THE X SUBSCRIPTION"
        */}
        <div className="space-y-2 mt-2">
          {/* Spaced, refined header title */}
          <h1 className="text-[24px] sm:text-[28px] font-light tracking-[0.26em] text-[#faf8f5] uppercase font-display leading-tight">
            IDEA BEACON
          </h1>
          <p className="text-[10px] sm:text-[11px] font-mono-code font-medium tracking-[0.32em] text-[#ffb693] uppercase">
            THE UNIFIED INTELLIGENCE SUITE
          </p>
        </div>

        {/* 
          Target Selector 2 & 3:
          aside > div:nth-of-type(2) > div:nth-of-type(3)
          and span:nth-of-type(2) inside it
          Replaced bulky badge with high-tech film telemetry line
        */}
        <div className="mt-5 inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#1c1917]/80 border border-[#292524] text-[#a8a29e]">
          <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00] animate-ping" />
          <span className="font-mono-code text-[10px] sm:text-[11px] text-[#faf8f5] font-normal tracking-wider">
            {getPhaseTelemetry()}
          </span>
        </div>
      </div>

      {/* 
        Bottom Minimalist Progress Architecture 
        (Fine hairline line with digital readout and stage progression)
      */}
      <div className="w-full max-w-xs flex flex-col items-center pb-3 relative z-10 space-y-3">
        {/* Precision Laser Hairline Progress Track */}
        <div className="w-full h-[2px] bg-[#24211e] rounded-full overflow-hidden relative">
          <div
            className="h-full bg-gradient-to-r from-[#ff6b00] via-[#ff8533] to-[#faf8f5] transition-all duration-100 shadow-[0_0_10px_rgba(255,107,0,0.9)]"
            style={{ width: `${progress}%` }}
          />
        </div>

        {/* Monospaced Technical Telemetry */}
        <div className="w-full flex items-center justify-between text-[#a8a29e] text-[11px] font-mono-code">
          <span className="flex items-center gap-1.5 text-[#e2bfb0]">
            <Zap className="w-3 h-3 text-[#ff6b00]" />
            <span>SYNCING REPOSITORY</span>
          </span>
          <span className="text-[#ff6b00] font-semibold tabular-nums tracking-wider">
            {String(progress).padStart(2, '0')}%
          </span>
        </div>

        {/* Interactive Click to Enter Studio when near complete */}
        {progress > 60 && (
          <button
            onClick={handleDismiss}
            className="text-[10px] font-mono-code uppercase tracking-widest text-[#a8a29e]/80 hover:text-[#ffb693] transition-colors pt-1"
          >
            [ TAP ANYWHERE TO ENTER ]
          </button>
        )}
      </div>
    </aside>
  );
};
