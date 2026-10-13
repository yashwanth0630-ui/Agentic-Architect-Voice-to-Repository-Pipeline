import React from 'react';
import { TimelineMilestoneEntity } from '../../db/schemas';
import { History, GitCommit, FileText, Cpu, Check, Clock } from 'lucide-react';

interface ProjectTimelineProps {
  milestones: TimelineMilestoneEntity[];
  onSelectMilestone?: (milestone: TimelineMilestoneEntity) => void;
  className?: string;
}

/**
 * Approved Component Pattern 5: Project Timeline
 * Visual project history timeline with status badges, tags, commit markers,
 * and orange-accented connectors.
 */
export const ProjectTimeline: React.FC<ProjectTimelineProps> = ({
  milestones,
  onSelectMilestone,
  className = '',
}) => {
  const getBadgeStyle = (statusType?: string) => {
    switch (statusType) {
      case 'authored':
        return 'bg-[#271e16] text-[#ffb693] border-[#ff6b00]/30';
      case 'actions':
        return 'bg-[#1b251d] text-[#86efac] border-[#22c55e]/30';
      case 'commits':
        return 'bg-[#1e1c24] text-[#c084fc] border-[#a855f7]/30';
      default:
        return 'bg-[#211f1d] text-[#e7e1de] border-[#373432]';
    }
  };

  const getIcon = (statusType?: string) => {
    switch (statusType) {
      case 'authored':
        return <FileText className="w-3.5 h-3.5 text-[#ff6b00]" />;
      case 'actions':
        return <Cpu className="w-3.5 h-3.5 text-[#22c55e]" />;
      case 'commits':
        return <GitCommit className="w-3.5 h-3.5 text-[#a855f7]" />;
      default:
        return <History className="w-3.5 h-3.5 text-[#ffb693]" />;
    }
  };

  if (!milestones || milestones.length === 0) {
    return (
      <div className={`p-8 rounded-xl border border-[#292524] bg-[#191715] text-center ${className}`}>
        <History className="w-8 h-8 text-[#78716c] mx-auto mb-2 opacity-50" />
        <p className="text-sm font-medium text-[#a8a29e]">No milestones recorded yet.</p>
        <p className="text-xs text-[#78716c] mt-1">Milestones are authored as ideas evolve into code.</p>
      </div>
    );
  }

  return (
    <div className={`relative pl-6 space-y-4 before:absolute before:left-[11px] before:top-3 before:bottom-3 before:w-[2px] before:bg-gradient-to-b before:from-[#ff6b00] before:via-[#373432] before:to-[#292524] ${className}`}>
      {milestones.map((milestone) => (
        <div
          key={milestone.id}
          onClick={() => onSelectMilestone?.(milestone)}
          className={`relative group rounded-xl border border-[#292524] bg-[#191715] hover:border-[#ff6b00]/40 p-3.5 transition-all cursor-pointer shadow-sm hover:shadow-[0_4px_16px_rgba(255,107,0,0.08)]`}
        >
          {/* Node Dot on Left Rail */}
          <div className="absolute -left-[23px] top-4 w-5 h-5 rounded-full bg-[#151311] border-2 border-[#ff6b00] flex items-center justify-center shadow-[0_0_8px_rgba(255,107,0,0.4)] group-hover:scale-110 transition-transform">
            <div className="w-1.5 h-1.5 rounded-full bg-[#ffb693]" />
          </div>

          {/* Header Row */}
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-2">
              <span className="p-1 rounded-md bg-[#211f1d] border border-[#373432]">
                {getIcon(milestone.statusType)}
              </span>
              <h4 className="text-sm font-semibold text-[#faf8f5] group-hover:text-[#ffb693] transition-colors line-clamp-1">
                {milestone.title}
              </h4>
            </div>

            <div className="flex items-center gap-1.5 flex-shrink-0">
              <span
                className={`text-[10px] font-mono-code font-bold px-2 py-0.5 rounded-md border ${getBadgeStyle(
                  milestone.statusType
                )}`}
              >
                {milestone.statusBadge || 'Logged'}
              </span>
            </div>
          </div>

          {/* Description */}
          <p className="text-xs text-[#a8a29e] mt-2 font-sans leading-relaxed">
            {milestone.description}
          </p>

          {/* Footer Metadata & Tags */}
          <div className="flex items-center justify-between gap-2 mt-3 pt-2.5 border-t border-[#292524]/60 text-[11px] font-mono-code text-[#78716c]">
            <div className="flex items-center gap-1.5">
              <Clock className="w-3 h-3 text-[#ff6b00]/70" />
              <span>{milestone.timestamp}</span>
            </div>

            <div className="flex items-center gap-1.5 flex-wrap justify-end">
              {milestone.speed && (
                <span className="text-[#ffb693] font-bold">{milestone.speed}</span>
              )}
              {milestone.tags &&
                milestone.tags.map((tag) => (
                  <span
                    key={tag}
                    className="px-1.5 py-0.5 rounded bg-[#211f1d] text-[#a8a29e] border border-[#373432]/60 text-[10px]"
                  >
                    {tag}
                  </span>
                ))}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
};
