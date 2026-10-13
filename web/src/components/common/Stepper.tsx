import React from 'react';
import { Check, CircleDot, Clock } from 'lucide-react';

export interface StepperItem {
  id: string | number;
  label: string;
  sublabel?: string;
  status: 'completed' | 'active' | 'pending';
  badge?: string;
}

interface StepperProps {
  steps: StepperItem[];
  activeStepIndex?: number;
  onStepClick?: (index: number) => void;
  orientation?: 'vertical' | 'horizontal';
  className?: string;
}

/**
 * Approved Component Pattern 6: Stepper
 * Visual pipeline step progression component with orange-accented active states,
 * completed checks, and pending badges.
 */
export const Stepper: React.FC<StepperProps> = ({
  steps,
  activeStepIndex,
  onStepClick,
  orientation = 'vertical',
  className = '',
}) => {
  return (
    <div className={`w-full ${className}`}>
      {orientation === 'horizontal' ? (
        <div className="flex items-center justify-between gap-2 overflow-x-auto pb-2">
          {steps.map((step, idx) => {
            const isCompleted = step.status === 'completed';
            const isActive = step.status === 'active' || idx === activeStepIndex;

            return (
              <React.Fragment key={step.id}>
                <button
                  type="button"
                  onClick={() => onStepClick?.(idx)}
                  className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border text-left transition-all ${
                    isActive
                      ? 'bg-[#271e16] border-[#ff6b00]/40 text-[#faf8f5]'
                      : isCompleted
                      ? 'bg-[#1d1b19] border-[#373432] text-[#a8a29e]'
                      : 'bg-[#151311] border-[#292524] text-[#78716c]'
                  }`}
                >
                  <div
                    className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] font-mono-code font-bold ${
                      isCompleted
                        ? 'bg-[#ff6b00] text-[#121110]'
                        : isActive
                        ? 'bg-[#ff6b00]/20 text-[#ffb693] border border-[#ff6b00]'
                        : 'bg-[#211f1d] text-[#78716c]'
                    }`}
                  >
                    {isCompleted ? <Check className="w-3 h-3 stroke-[3]" /> : idx + 1}
                  </div>
                  <div className="text-xs whitespace-nowrap font-medium">{step.label}</div>
                </button>
                {idx < steps.length - 1 && (
                  <div className={`h-[1px] flex-1 min-w-4 ${isCompleted ? 'bg-[#ff6b00]/40' : 'bg-[#292524]'}`} />
                )}
              </React.Fragment>
            );
          })}
        </div>
      ) : (
        <div className="relative pl-3 space-y-4 before:absolute before:left-[17px] before:top-2 before:bottom-3 before:w-[2px] before:bg-[#292524]">
          {steps.map((step, idx) => {
            const isCompleted = step.status === 'completed';
            const isActive = step.status === 'active' || idx === activeStepIndex;

            return (
              <div
                key={step.id}
                onClick={() => onStepClick?.(idx)}
                className={`relative flex items-start gap-3 p-3 rounded-xl border transition-all ${
                  isActive
                    ? 'bg-[#211b15] border-[#ff6b00]/40 shadow-sm'
                    : isCompleted
                    ? 'bg-[#191715] border-[#292524]'
                    : 'bg-[#151311] border-[#201e1c] opacity-70'
                }`}
              >
                {/* Step indicator dot */}
                <div
                  className={`relative z-10 w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-mono-code font-bold flex-shrink-0 mt-0.5 ${
                    isCompleted
                      ? 'bg-[#ff6b00] text-[#121110]'
                      : isActive
                      ? 'bg-[#ff6b00]/20 text-[#ffb693] border border-[#ff6b00] shadow-[0_0_8px_rgba(255,107,0,0.3)]'
                      : 'bg-[#211f1d] text-[#78716c] border border-[#373432]'
                  }`}
                >
                  {isCompleted ? (
                    <Check className="w-3.5 h-3.5 stroke-[3]" />
                  ) : isActive ? (
                    <CircleDot className="w-3.5 h-3.5 text-[#ff6b00]" />
                  ) : (
                    <span>{idx + 1}</span>
                  )}
                </div>

                {/* Step Content */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center justify-between gap-2">
                    <span
                      className={`text-sm font-semibold ${
                        isActive ? 'text-[#faf8f5]' : isCompleted ? 'text-[#e7e1de]' : 'text-[#a8a29e]'
                      }`}
                    >
                      {step.label}
                    </span>
                    {step.badge && (
                      <span className="text-[10px] font-mono-code px-2 py-0.5 rounded bg-[#271e16] text-[#ffb693] border border-[#ff6b00]/20">
                        {step.badge}
                      </span>
                    )}
                  </div>
                  {step.sublabel && (
                    <p className="text-xs text-[#a8a29e] mt-0.5 font-sans leading-relaxed">
                      {step.sublabel}
                    </p>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
