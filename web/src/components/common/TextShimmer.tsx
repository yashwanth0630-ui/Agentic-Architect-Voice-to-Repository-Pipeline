import React from 'react';

interface TextShimmerProps {
  children: React.ReactNode;
  className?: string;
  dotPulse?: boolean;
}

export const TextShimmer: React.FC<TextShimmerProps> = ({
  children,
  className = '',
  dotPulse = true,
}) => {
  return (
    <div className={`inline-flex items-center gap-2 ${className}`}>
      {dotPulse && (
        <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00] animate-ping" />
      )}
      <span className="text-shimmer font-medium tracking-wide">
        {children}
      </span>
    </div>
  );
};
