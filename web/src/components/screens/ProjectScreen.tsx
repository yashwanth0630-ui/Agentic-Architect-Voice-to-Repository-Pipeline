import React, { useState, useEffect } from 'react';
import { ScreenType, Repository, CodeDiffFile, ChangeProposal } from '../../types';
import {
  Terminal,
  Cpu,
  Settings,
  RefreshCw,
  ChevronRight,
  Activity,
  GitBranch,
  GitCommit,
  GitFork,
  Plus,
  CheckCircle2,
  AlertTriangle,
  FolderGit2,
  ExternalLink,
} from 'lucide-react';
import { Alert } from '../common/Alert';
import { HoldToConfirm } from '../common/HoldToConfirm';
import { DiffViewer } from '../common/DiffViewer';
import { bridgeService } from '../../services/bridgeService';

interface ProjectScreenProps {
  onNavigate: (screen: ScreenType) => void;
}

export const ProjectScreen: React.FC<ProjectScreenProps> = ({ onNavigate }) => {
  const [activeTab, setActiveTab] = useState<'overview' | 'diffs' | 'environments' | 'history' | 'config'>('overview');
  const [probeQuery, setProbeQuery] = useState('cargo xtask run --trace-sockets');
  const [queryOutput, setQueryOutput] = useState<string | null>(null);
  const [isRunningQuery, setIsRunningQuery] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'info' | 'warning' | 'error'; message: string } | null>(null);

  // Repository & Diff state
  const [repos, setRepos] = useState<Repository[]>([]);
  const [selectedRepoId, setSelectedRepoId] = useState<string>('repo-hyperstream-ebpf');
  const [activeProposal, setActiveProposal] = useState<ChangeProposal | null>(null);
  const [activeDiffs, setActiveDiffs] = useState<CodeDiffFile[]>([]);
  const [commitMessage, setCommitMessage] = useState('feat(trace): attach kprobe tcp trace & ringbuf export');
  const [isCommitted, setIsCommitted] = useState(false);
  const [lastCommittedHash, setLastCommittedHash] = useState<string | null>(null);

  // New Repo Connect Form State
  const [showConnectModal, setShowConnectModal] = useState(false);
  const [newRepoName, setNewRepoName] = useState('');
  const [newRepoUrl, setNewRepoUrl] = useState('');
  const [newRepoBranch, setNewRepoBranch] = useState('main');
  const [isConnectingRepo, setIsConnectingRepo] = useState(false);

  // Load repositories on mount
  useEffect(() => {
    loadRepositories();
  }, []);

  const loadRepositories = async () => {
    const list = await bridgeService.getRepositories();
    if (list && list.length > 0) {
      setRepos(list);
      // Select first repo if current not found
      if (!list.find((r: Repository) => r.id === selectedRepoId)) {
        setSelectedRepoId(list[0].id);
      }
      loadRepoDiffs(selectedRepoId || list[0].id);
    }
  };

  const loadRepoDiffs = async (repoId: string) => {
    const result = await bridgeService.getRepositoryDiffs(repoId);
    setActiveProposal(result.proposal);
    setActiveDiffs(result.diffs);
    if (result.proposal?.status === 'committed') {
      setIsCommitted(true);
      setLastCommittedHash(result.proposal.committedHash || null);
    } else {
      setIsCommitted(false);
      setLastCommittedHash(null);
    }
  };

  const handleSelectRepo = (repoId: string) => {
    setSelectedRepoId(repoId);
    loadRepoDiffs(repoId);
  };

  // Separate workspace ID from GitHub repo ID
  const [workspaceId, setWorkspaceId] = useState<string>('ws-hyperstream');
  const [isPushing, setIsPushing] = useState(false);
  const [isPushed, setIsPushed] = useState(false);

  const currentRepo = repos.find((r) => r.id === selectedRepoId) || repos[0];

  const handleRunInstruction = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!probeQuery.trim()) return;

    setIsRunningQuery(true);
    setQueryOutput(null);

    setFeedback({
      type: 'info',
      message: `Executing natural-language instruction on workspace '${workspaceId}'...`,
    });

    try {
      const res = await bridgeService.instructWorkspace(workspaceId, probeQuery);
      setIsRunningQuery(false);

      if (res.success && res.result) {
        const changesCount = res.result.changes?.files?.length || 0;
        setQueryOutput(
          `[repo-agent] Instruction: "${probeQuery}"\n` +
          `[status] ${res.result.message || 'Instruction executed successfully'}\n` +
          `[modified] ${changesCount} file(s) modified\n` +
          (res.result.output ? `[logs]\n${res.result.output}` : '')
        );

        if (changesCount > 0) {
          setFeedback({
            type: 'success',
            message: `Workspace modified: ${changesCount} file(s) changed. Review diffs below.`,
          });
          loadRepoDiffs(workspaceId);
          setActiveTab('diffs');
        } else {
          setFeedback({
            type: 'info',
            message: 'Instruction processed. Verified that no file modifications resulted.',
          });
        }
      } else {
        setQueryOutput(`[repo-agent] Error: ${res.error || 'Execution failed'}`);
        setFeedback({
          type: 'error',
          message: res.error || 'Failed to execute instruction on workspace',
        });
      }
    } catch (err: any) {
      setIsRunningQuery(false);
      setQueryOutput(`[repo-agent] Network exception: ${err.message}`);
      setFeedback({
        type: 'error',
        message: err.message,
      });
    }
  };

  const handleAction = (actionName: string) => {
    if (actionName === 'Reprovision' || actionName === 'Environments') {
      onNavigate('stepper');
    } else if (actionName === 'History') {
      onNavigate('timeline');
    } else if (actionName === 'Review Diffs') {
      setActiveTab('diffs');
    } else {
      setFeedback({
        type: 'info',
        message: `Directive '${actionName}' dispatched to host daemon.`,
      });
      setTimeout(() => setFeedback(null), 3000);
    }
  };

  // Hold-to-Confirm Commit Action (Approval Gate)
  const handleCommitChanges = async () => {
    if (!currentRepo) return;

    setFeedback({
      type: 'info',
      message: 'Hold-to-Confirm approval verified. Committing changes to repository...',
    });

    const approvedFiles = activeDiffs.map((d) => d.path);
    const result = await bridgeService.applyRepositoryChanges(
      workspaceId || currentRepo.id,
      true, // Explicitly confirmed
      commitMessage || 'feat: apply AI idea specification',
      approvedFiles
    );

    if (result.success) {
      setIsCommitted(true);
      setLastCommittedHash(result.commitHash || null);
      setFeedback({
        type: 'success',
        message: result.message || `Changes committed successfully (commit ${result.commitHash})`,
      });
      loadRepositories();
    } else {
      setFeedback({
        type: 'error',
        message: result.error || 'Failed to commit changes to repository',
      });
    }
  };

  // Controlled Push Action (Separate operation requiring explicit confirmation)
  const handlePushChanges = async () => {
    if (!currentRepo) return;

    setIsPushing(true);
    setFeedback({
      type: 'info',
      message: 'Explicit push confirmation received. Pushing commits to remote branch...',
    });

    const result = await bridgeService.pushWorkspace(
      workspaceId || currentRepo.id,
      true, // Explicitly confirmed
      currentRepo.branch || 'main'
    );
    setIsPushing(false);

    if (result.success) {
      setIsPushed(true);
      setFeedback({
        type: 'success',
        message: result.message || `Branch ${currentRepo.branch} successfully pushed to remote origin.`,
      });
    } else {
      setFeedback({
        type: 'error',
        message: result.error || 'Failed to push changes to remote repository',
      });
    }
  };

  // Connect new repository
  const handleConnectNewRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newRepoName.trim() && !newRepoUrl.trim()) return;

    setIsConnectingRepo(true);
    const result = await bridgeService.connectRepository({
      name: newRepoName.trim() || 'custom-service',
      url: newRepoUrl.trim(),
      branch: newRepoBranch.trim() || 'main',
      isLocalBridgeLinked: true,
    });
    setIsConnectingRepo(false);

    if (result.success && result.repo) {
      setShowConnectModal(false);
      setNewRepoName('');
      setNewRepoUrl('');
      setFeedback({
        type: 'success',
        message: `Repository ${result.repo.name} connected successfully.`,
      });
      await loadRepositories();
      setSelectedRepoId(result.repo.id);
      loadRepoDiffs(result.repo.id);
    } else {
      setFeedback({
        type: 'error',
        message: result.error || 'Could not connect repository.',
      });
    }
  };

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-lg mx-auto space-y-4">
      {feedback && (
        <Alert
          type={feedback.type}
          message={feedback.message}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Connected Repositories Selector Bar */}
      <div className="flex items-center justify-between gap-2 overflow-x-auto pb-1 scrollbar-none">
        <div className="flex items-center gap-1.5 flex-1 min-w-0">
          {repos.map((repo) => {
            const isSelected = repo.id === (currentRepo?.id || selectedRepoId);
            return (
              <button
                key={repo.id}
                onClick={() => handleSelectRepo(repo.id)}
                className={`px-2.5 py-1.5 rounded-xl text-[11px] font-mono-code flex items-center gap-1.5 border transition-all cursor-pointer whitespace-nowrap ${
                  isSelected
                    ? 'bg-[#29221d] border-[#ff6b00] text-[#faf8f5] shadow-[0_0_10px_rgba(255,107,0,0.2)]'
                    : 'bg-[#181615] border-[#292524] text-[#a8a29e] hover:text-[#faf8f5]'
                }`}
              >
                <FolderGit2 className={`w-3.5 h-3.5 ${isSelected ? 'text-[#ff6b00]' : 'text-[#78716c]'}`} />
                <span className="truncate font-semibold">{repo.name}</span>
                <span className="text-[10px] text-[#78716c]">({repo.branch})</span>
              </button>
            );
          })}
        </div>

        <button
          onClick={() => setShowConnectModal(true)}
          className="px-2.5 py-1.5 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-mono-code text-[11px] flex items-center gap-1 flex-shrink-0 cursor-pointer transition-all"
        >
          <Plus className="w-3.5 h-3.5 text-[#ff6b00]" />
          <span>Connect Repo</span>
        </button>
      </div>

      {/* Connect Repo Modal / Drawer */}
      {showConnectModal && (
        <div className="rounded-2xl bg-[#1d1b19] border border-[#ff6b00]/40 p-4 space-y-3 shadow-lg animate-in fade-in duration-200">
          <div className="flex items-center justify-between">
            <span className="text-[12px] font-mono-code font-bold text-[#faf8f5] flex items-center gap-1.5">
              <FolderGit2 className="w-4 h-4 text-[#ff6b00]" />
              Connect GitHub or Local Repository
            </span>
            <button
              onClick={() => setShowConnectModal(false)}
              className="text-[#78716c] hover:text-[#faf8f5] text-[12px] font-mono-code cursor-pointer"
            >
              ✕
            </button>
          </div>

          <form onSubmit={handleConnectNewRepo} className="space-y-2.5">
            <div>
              <label className="text-[10px] font-mono-code text-[#78716c] uppercase block mb-1">
                Repository Name
              </label>
              <input
                type="text"
                placeholder="e.g. wasm-kv-store"
                value={newRepoName}
                onChange={(e) => setNewRepoName(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-[#121110] border border-[#292524] text-[12px] font-mono-code text-[#faf8f5] focus:border-[#ff6b00] focus:outline-none"
              />
            </div>

            <div>
              <label className="text-[10px] font-mono-code text-[#78716c] uppercase block mb-1">
                GitHub URL / Local Bridge Path
              </label>
              <input
                type="text"
                placeholder="https://github.com/developer/repo or /home/dev/projects/repo"
                value={newRepoUrl}
                onChange={(e) => setNewRepoUrl(e.target.value)}
                className="w-full px-3 py-2 rounded-xl bg-[#121110] border border-[#292524] text-[12px] font-mono-code text-[#faf8f5] focus:border-[#ff6b00] focus:outline-none"
              />
            </div>

            <div className="flex gap-2">
              <div className="flex-1">
                <label className="text-[10px] font-mono-code text-[#78716c] uppercase block mb-1">
                  Default Branch
                </label>
                <input
                  type="text"
                  placeholder="main"
                  value={newRepoBranch}
                  onChange={(e) => setNewRepoBranch(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#121110] border border-[#292524] text-[12px] font-mono-code text-[#faf8f5] focus:border-[#ff6b00] focus:outline-none"
                />
              </div>

              <div className="flex items-end">
                <button
                  type="submit"
                  disabled={isConnectingRepo}
                  className="h-[38px] px-4 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-mono-code font-bold text-[12px] cursor-pointer disabled:opacity-50 transition-all flex items-center gap-1.5"
                >
                  {isConnectingRepo ? 'Connecting...' : 'Connect'}
                </button>
              </div>
            </div>
          </form>
        </div>
      )}

      {/* Project Header Card */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-3 shadow-md relative overflow-hidden">
        {/* Subtle orange ambient glow */}
        <div className="pointer-events-none absolute -right-8 -top-8 w-32 h-32 rounded-full bg-[#ff6b00]/15 blur-2xl" />

        <div className="flex items-center justify-between text-[11px] font-mono-code">
          <div className="flex items-center gap-1.5">
            <span className="px-2 py-0.5 rounded bg-[#ff6b00]/20 text-[#ffb693] font-bold">
              {currentRepo ? `ACTIVE · ${currentRepo.name}` : 'ACTIVE · V0.2.4'}
            </span>
          </div>
          <span className="text-[#a8a29e] flex items-center gap-1">
            <GitCommit className="w-3 h-3 text-[#ff6b00]" />
            git: {currentRepo?.lastCommit?.hash || '4b9f1a2'} · {currentRepo?.branch || 'master'}
          </span>
        </div>

        <div>
          <h1 className="text-[20px] font-bold text-[#faf8f5] font-display">
            {currentRepo?.name === 'beacon-core-runtime'
              ? 'Beacon Core Runtime'
              : currentRepo?.name === 'wasm-kv-store'
              ? 'WASM KV Store'
              : 'HyperStream Probe (eBPF)'}
          </h1>
          <p className="text-[12px] text-[#a8a29e] mt-0.5">
            {currentRepo?.name === 'beacon-core-runtime'
              ? 'Offline-first synchronization & SQLite Idea Vault daemon'
              : currentRepo?.name === 'wasm-kv-store'
              ? 'High-performance embedded WebAssembly key-value memory layer'
              : 'High-throughput kernel socket telemetry & trace buffer'}
          </p>
        </div>

        {/* Laptop Bridge strip */}
        <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524] flex items-center justify-between text-[11px] font-mono-code">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#ff6b00] animate-beacon" />
            <span className="text-[#faf8f5]">Laptop Bridge connected</span>
          </div>
          <span className="text-[#ea9f00] font-bold">(LOCAL DEV DAEMON)</span>
        </div>
      </div>

      {/* Sub-Navigation Tabs */}
      <div className="flex items-center gap-1 p-1 rounded-xl bg-[#1d1b19] border border-[#292524] overflow-x-auto scrollbar-none">
        {(['overview', 'diffs', 'environments', 'history', 'config'] as const).map((tab) => {
          const label =
            tab === 'diffs' ? (
              <span className="flex items-center justify-center gap-1">
                <span>Diffs</span>
                {activeDiffs.length > 0 && (
                  <span className="px-1.5 py-0.2 rounded-full bg-[#ff6b00]/20 text-[#ffb693] text-[9px] font-bold">
                    {activeDiffs.length}
                  </span>
                )}
              </span>
            ) : (
              tab
            );

          return (
            <button
              key={tab}
              onClick={() => {
                if (tab === 'environments') onNavigate('stepper');
                else if (tab === 'history') onNavigate('timeline');
                else setActiveTab(tab);
              }}
              className={`flex-1 py-1.5 px-2 rounded-lg text-[12px] font-mono-code capitalize transition-all cursor-pointer whitespace-nowrap text-center ${
                activeTab === tab
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
            >
              {label}
            </button>
          );
        })}
      </div>

      {/* TAB CONTENT: DIFFS & CODE REVIEW */}
      {activeTab === 'diffs' && (
        <div className="space-y-4 animate-in fade-in duration-200">
          {/* Active Proposal Header */}
          <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4 space-y-3 shadow-md">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <span className="text-[#78716c] uppercase">CODE GENERATION &amp; REVIEW</span>
              <span
                className={`px-2 py-0.5 rounded font-bold text-[10px] ${
                  isCommitted
                    ? 'bg-[#152a18] text-[#4ade80]'
                    : 'bg-[#ff6b00]/15 text-[#ffb693]'
                }`}
              >
                {isCommitted ? 'COMMITTED TO REPO' : 'PENDING APPROVAL GATE'}
              </span>
            </div>

            <div>
              <h2 className="text-[16px] font-bold text-[#faf8f5] font-display">
                {activeProposal?.title || 'aya-trace: Attach kprobe:tcp_connect & ringbuf export'}
              </h2>
              <p className="text-[12px] text-[#a8a29e] mt-1">
                {activeProposal?.description ||
                  'Generates kernel socket trace buffer probe implementation from idea spec.'}
              </p>
            </div>

            {activeProposal?.sourceIdeaTitle && (
              <div className="p-2 rounded-lg bg-[#141211] border border-[#24211e] text-[11px] font-mono-code flex items-center justify-between">
                <span className="text-[#78716c]">Origin Spec:</span>
                <span className="text-[#ffb693] truncate max-w-[220px]">
                  {activeProposal.sourceIdeaTitle}
                </span>
              </div>
            )}
          </div>

          {/* Diff Viewer Component */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <span className="text-[#78716c] uppercase">PROPOSED FILE MODIFICATIONS</span>
              <span className="text-[#a8a29e]">
                {activeDiffs.length} file{activeDiffs.length !== 1 ? 's' : ''} affected
              </span>
            </div>

            <DiffViewer diffs={activeDiffs} />
          </div>

          {/* Commit Message Box */}
          <div className="space-y-1.5">
            <label className="text-[11px] font-mono-code uppercase text-[#78716c] block">
              Commit Message Directives
            </label>
            <input
              type="text"
              value={commitMessage}
              disabled={isCommitted}
              onChange={(e) => setCommitMessage(e.target.value)}
              className="w-full px-3 py-2 rounded-xl bg-[#121110] border border-[#292524] text-[12px] font-mono-code text-[#faf8f5] focus:border-[#ff6b00] focus:outline-none disabled:opacity-50"
            />
          </div>

          {/* Hold-to-Confirm Approval Gate (Pattern 2) */}
          <div className="space-y-2 pt-2">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <span className="text-[#ff6b00] font-bold uppercase flex items-center gap-1.5">
                <AlertTriangle className="w-3.5 h-3.5" />
                CONSEQUENTIAL APPROVAL GATE
              </span>
              <span className="text-[#78716c]">1.5s hold required</span>
            </div>

            <HoldToConfirm
              label={isCommitted ? 'Changes Committed to Branch' : 'Hold to Commit Changes'}
              sublabel={
                isCommitted
                  ? `Commit ${lastCommittedHash || currentRepo?.lastCommit?.hash} recorded on ${currentRepo?.branch || 'master'}`
                  : `Press and hold to apply changes to ${currentRepo?.name} (${currentRepo?.branch})`
              }
              holdDurationMs={1500}
              variant={isCommitted ? 'primary' : 'warning'}
              onConfirm={handleCommitChanges}
            />

            {/* Controlled Push to Remote Branch (Separate Operation requiring explicit confirmation) */}
            {isCommitted && (
              <div className="space-y-2 pt-3 border-t border-[#292524]">
                <div className="flex items-center justify-between text-[11px] font-mono-code">
                  <span className="text-[#ff6b00] font-bold uppercase flex items-center gap-1.5">
                    <GitFork className="w-3.5 h-3.5" />
                    CONTROLLED PUSH APPROVAL GATE
                  </span>
                  <span className="text-[#78716c]">Separate confirmation required</span>
                </div>

                <HoldToConfirm
                  label={isPushed ? 'Changes Pushed to Remote' : isPushing ? 'Pushing...' : 'Hold to Push to Remote'}
                  sublabel={
                    isPushed
                      ? `Branch ${currentRepo?.branch || 'main'} is up-to-date on remote origin`
                      : `Press and hold to push verified commit ${lastCommittedHash || 'HEAD'} to origin/${currentRepo?.branch || 'main'}`
                  }
                  holdDurationMs={1500}
                  variant={isPushed ? 'primary' : 'warning'}
                  onConfirm={handlePushChanges}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* TAB CONTENT: OVERVIEW */}
      {activeTab === 'overview' && (
        <>
          {/* Pending Review Directives Banner */}
          {activeDiffs.length > 0 && !isCommitted && (
            <div className="p-3 rounded-xl bg-[#291e16] border border-[#ff6b00]/40 flex items-center justify-between text-[12px]">
              <div className="flex items-center gap-2">
                <GitBranch className="w-4 h-4 text-[#ff6b00]" />
                <span className="text-[#faf8f5] font-semibold">
                  {activeDiffs.length} code diff{activeDiffs.length !== 1 ? 's' : ''} awaiting approval
                </span>
              </div>
              <button
                onClick={() => setActiveTab('diffs')}
                className="px-2.5 py-1 rounded-lg bg-[#ff6b00] text-[#121110] font-mono-code font-bold text-[11px] hover:bg-[#ff7a1a] cursor-pointer transition-all"
              >
                Review Diffs
              </button>
            </div>
          )}

          {/* Host Runtime & Telemetry Panel */}
          <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4 space-y-3 shadow-md">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <span className="text-[#78716c] uppercase">HOST RUNTIME &amp; TELEMETRY</span>
              <span className="text-[#ffb693]">✓ Host Environment Connected</span>
            </div>

            <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Cpu className="w-4 h-4 text-[#ff6b00]" />
                <div>
                  <span className="text-[13px] font-bold text-[#faf8f5] font-mono-code">
                    {currentRepo?.name || 'Workspace Core'}
                  </span>
                  <p className="text-[11px] text-[#78716c] font-mono-code">
                    Workspace: {workspaceId} · Branch: {currentRepo?.branch || 'main'}
                  </p>
                </div>
              </div>
              <span className="px-2 py-0.5 rounded text-[10px] font-mono-code bg-[#ff6b00]/15 text-[#ff6b00] font-bold">
                WORKSPACE ACTIVE
              </span>
            </div>

            {/* 3 Telemetry Metrics */}
            <div className="grid grid-cols-3 gap-2 text-[11px] font-mono-code text-center">
              <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
                <span className="text-[#78716c] block text-[10px]">REPO STATUS</span>
                <span className="text-[#faf8f5] font-bold text-[13px] truncate">{currentRepo?.status || 'Active'}</span>
              </div>
              <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
                <span className="text-[#78716c] block text-[10px]">DIFFS PENDING</span>
                <span className="text-[#ff6b00] font-bold text-[13px]">{activeDiffs.length}</span>
              </div>
              <div className="p-2.5 rounded-xl bg-[#121110] border border-[#292524]">
                <span className="text-[#78716c] block text-[10px]">APPROVAL GATE</span>
                <span className="text-[#ffb693] font-bold text-[13px]">{isCommitted ? 'Passed' : 'Gated'}</span>
              </div>
            </div>
          </div>

          {/* Provision Status Bar */}
          <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4 space-y-2.5 shadow-md">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <span className="text-[#ffb693] font-bold">WORKSPACE PROVISION STATUS</span>
              <span className="text-[#78716c]">{workspaceId}</span>
            </div>

            {/* Progress Bar with 100% Verified Workspace */}
            <div className="space-y-1">
              <div className="h-2 bg-[#121110] rounded-full overflow-hidden border border-[#292524]">
                <div className="h-full bg-[#ff6b00] rounded-full w-[100%] shadow-[0_0_8px_rgba(255,107,0,0.5)]" />
              </div>
              <div className="text-right text-[10px] font-mono-code text-[#ffb693] font-semibold">
                Isolated Workspace Active
              </div>
            </div>

            <p className="text-[12px] text-[#a8a29e] leading-relaxed">
              Target workspace linked through Python laptop bridge. Baseline commit locked with clean diff tracking.
            </p>
          </div>

          {/* Action Directives Grid */}
          <div className="space-y-2">
            <span className="text-[11px] font-mono-code uppercase text-[#78716c]">
              ACTION DIRECTIVES
            </span>
            <div className="grid grid-cols-2 gap-2">
              <button
                onClick={() => handleAction('Bridge Shell')}
                className="h-10 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-bold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all shadow-[0_0_12px_rgba(255,107,0,0.2)] cursor-pointer"
              >
                <Terminal className="w-3.5 h-3.5 stroke-[2.5]" />
                <span>Bridge Shell</span>
              </button>

              <button
                onClick={() => handleAction('Review Diffs')}
                className="h-10 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
              >
                <GitBranch className="w-3.5 h-3.5 text-[#ff6b00]" />
                <span>Review Diffs</span>
              </button>

              <button
                onClick={() => handleAction('Run Benchmark')}
                className="h-10 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
              >
                <Activity className="w-3.5 h-3.5 text-[#ffb693]" />
                <span>Run Benchmark</span>
              </button>

              <button
                onClick={() => handleAction('Reprovision')}
                className="h-10 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#faf8f5] font-semibold text-[12px] flex items-center justify-center gap-1.5 active:scale-95 transition-all cursor-pointer"
              >
                <RefreshCw className="w-3.5 h-3.5 text-[#ea9f00]" />
                <span>Reprovision</span>
              </button>
            </div>
          </div>

          {/* Natural Language Workspace Instruction */}
          <div className="space-y-2">
            <div className="flex items-center justify-between text-[11px] font-mono-code">
              <div className="flex items-center gap-1.5 text-[#ff6b00]">
                <span className="w-1.5 h-1.5 rounded-full bg-[#ff6b00]" />
                <span>NATURAL LANGUAGE WORKSPACE INSTRUCTION</span>
              </div>
              <span className="text-[#78716c]">workspace :: {workspaceId}</span>
            </div>

            <form
              onSubmit={handleRunInstruction}
              className="w-full bg-[#121110] rounded-xl p-2.5 border border-[#292524] flex items-center justify-between gap-2 shadow-inner"
            >
              <div className="flex items-center gap-2 flex-1 min-w-0 pl-1 font-mono-code text-[12px]">
                <span className="text-[#ff6b00] select-none">&gt;</span>
                <input
                  type="text"
                  value={probeQuery}
                  placeholder="e.g. Add unit test for API contract validation..."
                  onChange={(e) => setProbeQuery(e.target.value)}
                  className="w-full bg-transparent text-[#faf8f5] focus:outline-none truncate"
                />
              </div>
              <button
                type="submit"
                disabled={isRunningQuery}
                className="px-3 py-1.5 rounded-lg bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-mono-code font-bold text-[11px] uppercase tracking-wider active:scale-95 transition-all shadow-[0_0_10px_rgba(255,107,0,0.3)] disabled:opacity-50 cursor-pointer flex-shrink-0"
              >
                {isRunningQuery ? '...' : 'RUN'}
              </button>
            </form>

            {queryOutput && (
              <div className="p-3 rounded-xl bg-[#121110] border border-[#292524] text-[11px] font-mono-code text-[#e2bfb0] whitespace-pre-wrap leading-relaxed animate-in fade-in duration-200">
                {queryOutput}
              </div>
            )}
          </div>

          {/* Project Chronology Link to Timeline Screen */}
          <div className="p-3 rounded-xl bg-[#1d1b19] border border-[#292524] flex items-center justify-between">
            <div>
              <span className="text-[12px] font-mono-code text-[#78716c] uppercase block">
                PROJECT CHRONOLOGY
              </span>
              <span className="text-[13px] font-semibold text-[#faf8f5]">
                {currentRepo?.history?.length || 5} verified milestones recorded
              </span>
            </div>
            <button
              onClick={() => onNavigate('timeline')}
              className="flex items-center gap-1 text-[12px] font-mono-code font-bold text-[#ff6b00] hover:underline cursor-pointer"
            >
              <span>Full Log</span>
              <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </>
      )}
    </div>
  );
};
