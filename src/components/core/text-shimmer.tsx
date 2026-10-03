import React from 'react';

export interface TextShimmerProps {
  children: React.ReactNode;
  as?: React.ElementType;
  className?: string;
  duration?: number;
  spread?: number;
}

export const TextShimmer: React.FC<TextShimmerProps> = ({
  children,
  as: Component = 'p',
  className = '',
  duration = 1,
  spread = 2
}) => {
  return (
    <>
      <Component
        className={`inline-block select-none bg-[length:250%_100%] bg-clip-text text-transparent ${className}`}
        style={{
          backgroundImage: `linear-gradient(90deg, rgba(255, 255, 255, 0.28) 0%, rgba(255, 255, 255, 0.98) 50%, rgba(255, 255, 255, 0.28) 100%)`,
          animation: `text-shimmer ${duration}s ease-in-out infinite alternate`,
          WebkitBackgroundClip: 'text',
          WebkitTextFillColor: 'transparent'
        }}
      >
        {children}
      </Component>
      <style>{`
        @keyframes text-shimmer {
          0% {
            background-position: 100% 50%;
          }
          100% {
            background-position: 0% 50%;
          }
        }
      `}</style>
    </>
  );
};

export function TextShimmerBasic() {
  return (
    <TextShimmer className="font-mono text-sm" duration={1}>
      Generating code...
    </TextShimmer>
  );
}

export default TextShimmer;
