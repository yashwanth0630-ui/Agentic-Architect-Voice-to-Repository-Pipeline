import React, { useState } from 'react';
import { ScreenType } from '../../types';
import { TimelineMilestoneEntity } from '../../db/schemas';
import { BeaconDatabase } from '../../db/BeaconDatabase';
import { ArrowLeft, Plus, History, Check, Clock, Cpu, Sparkles, FileText, GitCommit, RefreshCw } from 'lucide-react';
import { Alert } from '../common/Alert';

interface ProjectTimelineScreenProps {
  onBack: () => void;
  onNavigate: (screen: ScreenType) => void;
}

export const ProjectTimelineScreen: React.FC<ProjectTimelineScreenProps> = ({
  onBack,
  onNavigate,
}) => {
  const [filter, setFilter] = useState<'all' | 'ideas' | 'ai'>('all');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [newTag, setNewTag] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);

  const [milestones, setMilestones] = useState<TimelineMilestoneEntity[]>(() =>
    BeaconDatabase.timelineDao.getAll()
  );

  const handleRecordMilestone = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;

    const created = BeaconDatabase.timelineDao.insert({
      projectId: 'proj-hyperstream',
      timestamp: `Today ${new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`,
      title: newTitle.trim(),
      description: newDesc.trim() || 'Custom milestone recorded to local SQLite timeline.',
      statusBadge: 'Authored',
      statusType: 'authored',
      tags: newTag.trim() ? [newTag.trim()] : ['#custom-milestone'],
      isDemo: false,
    });

    setMilestones([created, ...milestones]);
    setIsModalOpen(false);
    setNewTitle('');
    setNewDesc('');
    setNewTag('');
    setFeedback('Custom milestone logged to project timeline.');
    setTimeout(() => setFeedback(null), 2500);
  };

  const filteredMilestones = milestones.filter((item) => {
    if (filter === 'ideas') return item.statusType === 'authored' || item.title.includes('Idea');
    if (filter === 'ai') return item.statusType === 'actions' || item.title.includes('AI');
    return true;
  });

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-lg mx-auto space-y-4">
      {/* Top Header (Matches Image 10.jpeg) */}
      <div className="flex items-center justify-between gap-3 pt-2">
        <button
          onClick={onBack}
          className="w-10 h-10 rounded-full flex items-center justify-center text-[#faf8f5] hover:bg-[#24211e] active:scale-95 transition-all"
          aria-label="Back"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>

        <div className="flex-1 min-w-0">
          <h1 className="text-[18px] font-bold text-[#faf8f5] truncate font-display">
            Project Chronology
          </h1>
          <p className="text-[11px] font-mono-code text-[#ffb693] truncate">
            HyperStream Probe · Event Stream
          </p>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="w-9 h-9 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] flex items-center justify-center font-bold shadow-[0_0_12px_rgba(255,107,0,0.3)] active:scale-95 transition-all"
          title="Record Custom Milestone"
        >
          <Plus className="w-5 h-5 stroke-[3]" />
        </button>
      </div>

      {feedback && (
        <Alert
          type="success"
          message={feedback}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Project Status Hero Card (Matches Image 10.jpeg) */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-2.5 shadow-md">
        <div className="flex items-center justify-between text-[11px] font-mono-code">
          <span className="px-2 py-0.5 rounded bg-[#ff6b00]/20 text-[#ffb693] font-bold">
            ACTIVE PROJECT · LIVE
          </span>
          <span className="text-[#a8a29e] flex items-center gap-1">
            <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00] animate-beacon" />
            LIVE BUS
          </span>
        </div>

        <h2 className="text-[20px] font-bold text-[#faf8f5] font-display">
          HyperStream Probe
        </h2>
        <p className="text-[12px] text-[#a8a29e]">
          eBPF-powered network latency probe &amp; kernel telemetry buffer
        </p>

        <div className="grid grid-cols-3 gap-2 pt-1 text-[11px] font-mono-code">
          <div className="p-2 rounded-lg bg-[#121110] border border-[#292524]">
            <span className="text-[#78716c] block text-[10px]">HEALTH</span>
            <span className="text-[#ffb693] font-bold">98.4% nomi...</span>
          </div>
          <div className="p-2 rounded-lg bg-[#121110] border border-[#292524]">
            <span className="text-[#78716c] block text-[10px]">TOOLCHAIN</span>
            <span className="text-[#ffb693] font-bold">Rust v1.78</span>
          </div>
          <div className="p-2 rounded-lg bg-[#121110] border border-[#292524]">
            <span className="text-[#78716c] block text-[10px]">GIT</span>
            <span className="text-[#ea9f00] font-bold">41fa02c</span>
          </div>
        </div>
      </div>

      {/* Filter Tabs (Matches Image 10.jpeg) */}
      <div className="flex items-center gap-2 overflow-x-auto no-scrollbar py-0.5">
        <button
          onClick={() => setFilter('all')}
          className={`px-3 py-1.5 rounded-xl text-[12px] font-mono-code flex items-center gap-1.5 transition-all ${
            filter === 'all'
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
              : 'bg-[#211f1d] text-[#a8a29e] hover:bg-[#2c2927]'
          }`}
        >
          <span>All Events</span>
          <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-[#121110]/30">
            {milestones.length}
          </span>
        </button>

        <button
          onClick={() => setFilter('ideas')}
          className={`px-3 py-1.5 rounded-xl text-[12px] font-mono-code transition-all ${
            filter === 'ideas'
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
              : 'bg-[#211f1d] text-[#a8a29e] hover:bg-[#2c2927]'
          }`}
        >
          Ideas
        </button>

        <button
          onClick={() => setFilter('ai')}
          className={`px-3 py-1.5 rounded-xl text-[12px] font-mono-code transition-all ${
            filter === 'ai'
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
              : 'bg-[#211f1d] text-[#a8a29e] hover:bg-[#2c2927]'
          }`}
        >
          AI Brainstorm
        </button>
      </div>

      {/* Timeline Event Stream (Pattern 5 - Matches Image 10.jpeg) */}
      <div className="space-y-3 relative pt-1">
        {filteredMilestones.map((evt) => {
          return (
            <div
              key={evt.id}
              className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4 space-y-2.5 shadow-md relative overflow-hidden"
            >
              {/* Event Header: Timestamp & Badge */}
              <div className="flex items-center justify-between text-[11px] font-mono-code">
                <span className="text-[#78716c] flex items-center gap-1.5">
                  <Clock className="w-3.5 h-3.5 text-[#ff6b00]" />
                  {evt.timestamp}
                </span>

                <span
                  className={`px-2 py-0.5 rounded text-[10px] font-mono-code font-bold ${
                    evt.statusType === 'in-progress'
                      ? 'bg-[#ea9f00]/20 text-[#ea9f00]'
                      : evt.statusType === 'actions'
                      ? 'bg-[#ff6b00]/20 text-[#ffb693]'
                      : 'bg-[#211f1d] text-[#a8a29e]'
                  }`}
                >
                  {evt.statusBadge}
                </span>
              </div>

              {/* Title & Description */}
              <div>
                <h3 className="text-[15px] font-bold text-[#faf8f5]">
                  {evt.title}
                </h3>
                <p className="text-[12px] text-[#a8a29e] mt-0.5 leading-relaxed">
                  {evt.description}
                </p>
              </div>

              {/* In Progress Stepper Preview Bar */}
              {evt.progress && (
                <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] space-y-1.5">
                  <div className="flex items-center justify-between text-[10px] font-mono-code">
                    <span className="text-[#a8a29e]">{evt.progressStep}</span>
                    <span className="text-[#ff6b00] font-bold">{evt.progress}%</span>
                  </div>
                  <div className="h-1.5 bg-[#24211e] rounded-full overflow-hidden">
                    <div
                      className="h-full bg-[#ff6b00] rounded-full"
                      style={{ width: `${evt.progress}%` }}
                    />
                  </div>
                  <div className="flex items-center justify-between text-[9px] font-mono-code text-[#78716c]">
                    <span>cargo bpf-probe --target=x86_...</span>
                    <span>{evt.speed || '3.4MB/s'}</span>
                  </div>
                </div>
              )}

              {/* Tags */}
              {evt.tags && evt.tags.length > 0 && (
                <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                  {evt.tags.map((tag) => (
                    <span
                      key={tag}
                      className="px-2 py-0.5 rounded text-[10px] font-mono-code bg-[#121110] text-[#ffb693] border border-[#292524]"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {/* Record Custom Milestone Button (Matches Image 10.jpeg) */}
      <button
        onClick={() => setIsModalOpen(true)}
        className="w-full h-12 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[13px] flex items-center justify-center gap-2 active:scale-95 transition-all shadow-sm cursor-pointer"
      >
        <Plus className="w-4 h-4 text-[#ff6b00] stroke-[3]" />
        <span>Record Custom Timeline Milestone</span>
      </button>

      {/* Custom Milestone Modal Form */}
      {isModalOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4"
          onClick={() => setIsModalOpen(false)}
        >
          <div
            className="w-full max-w-sm rounded-2xl bg-[#1d1b19] border border-[#292524] p-5 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-[#292524] pb-3">
              <h3 className="text-[16px] font-bold text-[#faf8f5] font-display">
                Record Milestone
              </h3>
              <button
                onClick={() => setIsModalOpen(false)}
                className="text-[#a8a29e] hover:text-[#faf8f5] text-[13px]"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleRecordMilestone} className="space-y-3">
              <div>
                <label className="text-[11px] font-mono-code text-[#78716c] block mb-1">
                  EVENT TITLE *
                </label>
                <input
                  type="text"
                  required
                  value={newTitle}
                  onChange={(e) => setNewTitle(e.target.value)}
                  placeholder="e.g. Ring buffer zero-drop benchmark verified"
                  className="w-full bg-[#121110] border border-[#292524] rounded-xl px-3 py-2 text-[13px] text-[#faf8f5] focus:outline-none focus:border-[#ff6b00]"
                />
              </div>

              <div>
                <label className="text-[11px] font-mono-code text-[#78716c] block mb-1">
                  DETAILS / LOG NOTE
                </label>
                <textarea
                  rows={2}
                  value={newDesc}
                  onChange={(e) => setNewDesc(e.target.value)}
                  placeholder="e.g. Sustained 250k events/sec with zero latency skew."
                  className="w-full bg-[#121110] border border-[#292524] rounded-xl p-2.5 text-[12px] text-[#faf8f5] focus:outline-none focus:border-[#ff6b00]"
                />
              </div>

              <div>
                <label className="text-[11px] font-mono-code text-[#78716c] block mb-1">
                  TAG (OPTIONAL)
                </label>
                <input
                  type="text"
                  value={newTag}
                  onChange={(e) => setNewTag(e.target.value)}
                  placeholder="#benchmarks"
                  className="w-full bg-[#121110] border border-[#292524] rounded-xl px-3 py-1.5 text-[12px] font-mono-code text-[#ffb693] focus:outline-none"
                />
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="flex-1 py-2.5 rounded-xl bg-[#24211e] hover:bg-[#2c2927] text-[13px] text-[#a8a29e]"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2.5 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-bold text-[13px] shadow-[0_0_12px_rgba(255,107,0,0.3)]"
                >
                  Log Milestone
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
