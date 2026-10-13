import React from 'react';

interface SkeletonProps {
  className?: string;
  variant?: 'text' | 'rectangular' | 'circular';
  width?: string | number;
  height?: string | number;
}

export const Skeleton: React.FC<SkeletonProps> = ({
  className = '',
  variant = 'rectangular',
  width,
  height,
}) => {
  const getVariantClass = () => {
    switch (variant) {
      case 'circular':
        return 'rounded-full';
      case 'text':
        return 'rounded h-3 my-1';
      case 'rectangular':
      default:
        return 'rounded-xl';
    }
  };

  const style: React.CSSProperties = {
    width: width !== undefined ? width : undefined,
    height: height !== undefined ? height : undefined,
  };

  return (
    <div
      style={style}
      className={`bg-[#211f1d] animate-pulse relative overflow-hidden border border-[#292524]/60 ${getVariantClass()} ${className}`}
    >
      <div className="absolute inset-0 -translate-x-full animate-[shimmer_2s_infinite] bg-gradient-to-r from-transparent via-[#2c2927]/40 to-transparent" />
    </div>
  );
};

export const IdeaCardSkeleton: React.FC = () => {
  return (
    <div className="rounded-xl bg-[#1d1b19] border border-[#292524] p-3.5 space-y-2.5">
      <div className="flex items-center justify-between">
        <Skeleton variant="rectangular" className="h-4 w-20" />
        <Skeleton variant="text" className="w-16 h-3" />
      </div>
      <Skeleton variant="rectangular" className="h-5 w-3/4" />
      <Skeleton variant="text" className="w-full h-3.5" />
      <Skeleton variant="text" className="w-2/3 h-3.5" />
    </div>
  );
};
