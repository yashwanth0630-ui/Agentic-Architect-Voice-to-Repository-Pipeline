import React, { useState, useRef, useEffect } from 'react';
import { RotateCcw, AlertOctagon } from 'lucide-react';

interface HoldToConfirmProps {
  label: string;
  sublabel?: string;
  holdDurationMs?: number;
  onConfirm: () => void;
  icon?: React.ReactNode;
  variant?: 'danger' | 'warning' | 'primary';
  className?: string;
}

export const HoldToConfirm: React.FC<HoldToConfirmProps> = ({
  label,
  sublabel,
  holdDurationMs = 1500,
  onConfirm,
  icon,
  variant = 'warning',
  className = '',
}) => {
  const [progress, setProgress] = useState(0);
  const [isHolding, setIsHolding] = useState(false);
  const [isCompleted, setIsCompleted] = useState(false);
  const animationFrameRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);

  const startHold = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (isCompleted) return;
    setIsHolding(true);
    startTimeRef.current = Date.now();

    const update = () => {
      const elapsed = Date.now() - startTimeRef.current;
      const currentProgress = Math.min(100, (elapsed / holdDurationMs) * 100);
      setProgress(currentProgress);

      if (currentProgress >= 100) {
        setIsHolding(false);
        setIsCompleted(true);
        if (navigator.vibrate) {
          navigator.vibrate([40, 60, 80]);
        }
        onConfirm();
        setTimeout(() => {
          setIsCompleted(false);
          setProgress(0);
        }, 1200);
      } else {
        animationFrameRef.current = requestAnimationFrame(update);
      }
    };

    animationFrameRef.current = requestAnimationFrame(update);
  };

  const cancelHold = () => {
    if (isCompleted) return;
    setIsHolding(false);
    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
    }
    setProgress(0);
  };

  useEffect(() => {
    return () => {
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
    };
  }, []);

  const strokeDashoffset = 125.6 - (125.6 * progress) / 100;

  return (
    <div
      className={`relative overflow-hidden rounded-2xl bg-[#1d1b19] border border-[#292524] p-4 text-center flex flex-col items-center justify-center transition-all select-none ${
        isHolding ? 'border-[#ff6b00]/60 shadow-[0_0_24px_rgba(255,107,0,0.2)]' : ''
      } ${className}`}
    >
      {/* Background progress fill */}
      <div
        className="absolute inset-0 bg-[#ff6b00]/10 transition-all pointer-events-none"
        style={{ width: `${progress}%` }}
      />

      {/* Circular button with radial progress ring */}
      <div
        className="relative cursor-pointer touch-none active:scale-95 transition-transform my-1"
        onMouseDown={startHold}
        onMouseUp={cancelHold}
        onMouseLeave={cancelHold}
        onTouchStart={startHold}
        onTouchEnd={cancelHold}
        onTouchCancel={cancelHold}
        role="button"
        tabIndex={0}
        aria-label={label}
      >
        <svg className="w-16 h-16 transform -rotate-90" viewBox="0 0 48 48">
          <circle
            cx="24"
            cy="24"
            r="20"
            stroke="#2c2927"
            strokeWidth="3.5"
            fill="transparent"
          />
          <circle
            cx="24"
            cy="24"
            r="20"
            stroke="#ff6b00"
            strokeWidth="3.5"
            strokeDasharray="125.6"
            strokeDashoffset={strokeDashoffset}
            strokeLinecap="round"
            fill="transparent"
            className="transition-all duration-75"
          />
        </svg>

        <div className="absolute inset-0 flex items-center justify-center">
          <div
            className={`w-11 h-11 rounded-full flex items-center justify-center transition-all ${
              isCompleted
                ? 'bg-[#ff6b00] text-[#121110]'
                : isHolding
                ? 'bg-[#ff6b00]/25 text-[#ff6b00]'
                : 'bg-[#24211e] text-[#ffb693]'
            }`}
          >
            {isCompleted ? (
              <AlertOctagon className="w-5 h-5 animate-pulse" />
            ) : (
              icon || <RotateCcw className={`w-5 h-5 ${isHolding ? 'animate-spin' : ''}`} />
            )}
          </div>
        </div>
      </div>

      <div className="mt-3 space-y-1 relative z-10 pointer-events-none">
        <div className="flex items-center justify-center gap-2">
          <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00]" />
          <h4 className="text-[13px] font-semibold text-[#faf8f5]">
            {isCompleted ? 'Operation Executed' : label}
          </h4>
        </div>
        {sublabel && (
          <p className="text-[11px] font-mono-code text-[#a8a29e] max-w-[260px] leading-tight">
            {isHolding ? `Holding... ${Math.round(progress)}%` : sublabel}
          </p>
        )}
      </div>
    </div>
  );
};
