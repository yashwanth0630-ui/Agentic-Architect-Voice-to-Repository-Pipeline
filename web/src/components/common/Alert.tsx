import React from 'react';
import { AlertCircle, CheckCircle2, Info, AlertTriangle, X } from 'lucide-react';

export type AlertType = 'info' | 'success' | 'warning' | 'error';

interface AlertProps {
  type?: AlertType;
  title?: string;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  onDismiss?: () => void;
  className?: string;
}

export const Alert: React.FC<AlertProps> = ({
  type = 'info',
  title,
  message,
  actionLabel,
  onAction,
  onDismiss,
  className = '',
}) => {
  const config = {
    info: {
      bg: 'bg-[#211f1d] border-[#373432]',
      text: 'text-[#e7e1de]',
      icon: <Info className="w-4 h-4 text-[#ffb693] flex-shrink-0" />,
      accent: 'text-[#ffb693]',
    },
    success: {
      bg: 'bg-[#271e16] border-[#ff6b00]/30',
      text: 'text-[#faf8f5]',
      icon: <CheckCircle2 className="w-4 h-4 text-[#ff6b00] flex-shrink-0" />,
      accent: 'text-[#ffb693]',
    },
    warning: {
      bg: 'bg-[#271e16] border-[#4a2e18]',
      text: 'text-[#ffddb1]',
      icon: <AlertTriangle className="w-4 h-4 text-[#ea9f00] flex-shrink-0" />,
      accent: 'text-[#ea9f00]',
    },
    error: {
      bg: 'bg-[#291717] border-[#5a2222]',
      text: 'text-[#ffdad6]',
      icon: <AlertCircle className="w-4 h-4 text-[#ff6b00] flex-shrink-0" />,
      accent: 'text-[#ff6b00]',
    },
  }[type];

  return (
    <div
      role="alert"
      className={`rounded-xl border p-3.5 flex items-start justify-between gap-3 shadow-md transition-all ${config.bg} ${className}`}
    >
      <div className="flex items-start gap-2.5 flex-1 min-w-0">
        <div className="mt-0.5">{config.icon}</div>
        <div className="flex-1 min-w-0">
          {title && <h4 className="text-[13px] font-semibold text-[#faf8f5] mb-0.5">{title}</h4>}
          <p className="text-[12px] leading-relaxed text-[#e7e1de]/90 break-words">{message}</p>
          {actionLabel && onAction && (
            <button
              onClick={onAction}
              className={`mt-2 text-[12px] font-semibold font-mono-code uppercase tracking-wider ${config.accent} hover:underline active:scale-95 transition-all`}
            >
              {actionLabel} →
            </button>
          )}
        </div>
      </div>
      {onDismiss && (
        <button
          onClick={onDismiss}
          className="text-[#a8a29e] hover:text-[#faf8f5] p-1 rounded-md transition-colors"
          aria-label="Dismiss alert"
        >
          <X className="w-3.5 h-3.5" />
        </button>
      )}
    </div>
  );
};
