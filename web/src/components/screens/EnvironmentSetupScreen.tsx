import React, { useState, useEffect } from 'react';
import { ScreenType } from '../../types';
import { ArrowLeft, Check, Terminal, Play, Square, RefreshCw, ChevronUp, ChevronDown, ShieldAlert, ExternalLink } from 'lucide-react';
import { Alert } from '../common/Alert';

interface EnvironmentSetupScreenProps {
  onBack: () => void;
  onNavigate: (screen: ScreenType) => void;
}

interface StepData {
  id: number;
  title: string;
  description: string;
  status: 'completed' | 'active' | 'pending' | 'failed';
  details?: { label: string; value: string }[];
}

export const EnvironmentSetupScreen: React.FC<EnvironmentSetupScreenProps> = ({
  onBack,
}) => {
  const [projectId, setProjectId] = useState('demo-hyperstream');
  const [hostEnv, setHostEnv] = useState<any>(null);
  const [planState, setPlanState] = useState<any>(null);
  const [steps, setSteps] = useState<StepData[]>([]);
  const [activeStepIndex, setActiveStepIndex] = useState(0);
  const [progress, setProgress] = useState(0);
  const [isExecuting, setIsExecuting] = useState(false);
  const [isApproved, setIsApproved] = useState(false);
  const [isLogsExpanded, setIsLogsExpanded] = useState(false);
  const [feedback, setFeedback] = useState<{ type: 'info' | 'success' | 'warning' | 'error'; message: string } | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [bridgeOnline, setBridgeOnline] = useState<boolean>(true);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  // 1. Detect Host Environment & Fetch or Create Plan
  useEffect(() => {
    let isMounted = true;

    async function loadEnvironmentAndPlan() {
      try {
        const detectRes = await fetch('/api/environment/detect');
        if (detectRes.ok) {
          const detectData = await detectRes.json();
          if (isMounted) {
            setHostEnv(detectData.environment);
            setBridgeOnline(true);
          }
        } else {
          if (isMounted) setBridgeOnline(false);
        }

        // Fetch or create setup plan for active project
        const statusRes = await fetch(`/api/environment/status/${projectId}`);
        if (statusRes.ok) {
          const statusData = await statusRes.json();
          if (isMounted && statusData.plan) {
            applyPlanToUI(statusData.plan);
            if (statusData.preview_url) setPreviewUrl(statusData.preview_url);
            return;
          }
        }

        // Generate clean initial plan if none exists
        const planRes = await fetch('/api/environment/plan', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            prompt: 'HyperStream Probe high-performance socket trace service',
            tech_stack: 'fastapi',
            project_name: 'HyperStream Probe',
            explicit_project_id: projectId
          })
        });

        if (planRes.ok) {
          const planData = await planRes.json();
          if (isMounted && planData.plan) {
            applyPlanToUI(planData.plan);
          }
        }
      } catch {
        if (isMounted) {
          setBridgeOnline(false);
          setFeedback({
            type: 'warning',
            message: 'Laptop bridge unreachable on :8000. Operating in offline inspection mode.'
          });
          // Fallback static plan for offline demonstration
          applyFallbackPlan();
        }
      }
    }

    loadEnvironmentAndPlan();

    return () => {
      isMounted = false;
    };
  }, [projectId]);

  function applyPlanToUI(plan: any) {
    setPlanState(plan);
    setIsApproved(plan.approved || false);
    setIsExecuting(plan.status === 'in_progress');

    const rawSteps: any[] = plan.steps || [];
    const mapped: StepData[] = rawSteps.map((s, idx) => ({
      id: idx + 1,
      title: s.name || `Operation ${idx + 1}`,
      description: s.description || s.command || 'Setup task',
      status: s.status === 'completed' ? 'completed' : s.status === 'in_progress' ? 'active' : s.status === 'failed' ? 'failed' : 'pending',
      details: [
        { label: 'Type', value: s.type || 'command' },
        { label: 'Status', value: s.status || 'pending' }
      ]
    }));

    setSteps(mapped);

    const activeIdx = mapped.findIndex((s) => s.status === 'active');
    if (activeIdx !== -1) {
      setActiveStepIndex(activeIdx);
      setProgress(Math.round(((activeIdx + 0.5) / mapped.length) * 100));
    } else {
      const completedCount = mapped.filter((s) => s.status === 'completed').length;
      setProgress(Math.round((completedCount / (mapped.length || 1)) * 100));
      setActiveStepIndex(Math.min(completedCount, mapped.length - 1));
    }

    if (plan.logs && Array.isArray(plan.logs)) {
      setLogs(plan.logs);
    }
  }

  function applyFallbackPlan() {
    const fallbackSteps: StepData[] = [
      {
        id: 1,
        title: 'Detect Host Runtime',
        description: 'Check Python 3.12+, Node.js, and Git availability on host',
        status: 'completed',
        details: [
          { label: 'Python', value: '3.14.x' },
          { label: 'Node', value: '24.x' }
        ]
      },
      {
        id: 2,
        title: 'Scaffold Workspace Directory',
        description: 'Initialize isolated sandbox directory at projects/hyperstream',
        status: 'pending',
        details: [{ label: 'Path', value: 'projects/hyperstream' }]
      },
      {
        id: 3,
        title: 'Provision Virtual Environment',
        description: 'Configure isolated virtual environment and dependencies',
        status: 'pending',
        details: [{ label: 'Tool', value: 'venv / pip' }]
      },
      {
        id: 4,
        title: 'Verify & Launch Dev Server',
        description: 'Execute health check smoke test and bind preview port',
        status: 'pending',
        details: [{ label: 'Port', value: 'Dynamic' }]
      }
    ];
    setSteps(fallbackSteps);
    setLogs([
      '>> Bridge offline. Awaiting laptop connection at http://127.0.0.1:8000',
      '>> Run: python scripts/laptop_ai_server.py to enable real execution.'
    ]);
  }

  // Poll active execution status
  useEffect(() => {
    if (!isExecuting) return;

    const interval = setInterval(async () => {
      try {
        const res = await fetch(`/api/environment/status/${projectId}`);
        if (res.ok) {
          const data = await res.json();
          if (data.plan) {
            applyPlanToUI(data.plan);
            if (data.plan.status === 'completed') {
              setIsExecuting(false);
              setFeedback({
                type: 'success',
                message: 'All environment setup operations completed and verified!'
              });
              if (data.preview_url) setPreviewUrl(data.preview_url);
              clearInterval(interval);
            } else if (data.plan.status === 'failed') {
              setIsExecuting(false);
              setFeedback({
                type: 'error',
                message: 'Setup operation failed. Inspect logs for details.'
              });
              clearInterval(interval);
            }
          }
        }
      } catch {
        // Ignored during polling
      }
    }, 1200);

    return () => clearInterval(interval);
  }, [isExecuting, projectId]);

  // Explicit Hold-to-Confirm or Button Approval (Never auto-runs!)
  const handleApproveAndExecute = async () => {
    if (!bridgeOnline) {
      setFeedback({
        type: 'warning',
        message: 'Cannot execute setup: Laptop bridge is offline.'
      });
      return;
    }

    setFeedback({
      type: 'info',
      message: 'Plan approved by operator. Dispatching step-by-step setup to bridge...'
    });

    try {
      const res = await fetch('/api/environment/execute-plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          project_id: projectId,
          approved: true,
          prompt: 'HyperStream Probe setup'
        })
      });

      const data = await res.json();
      if (res.ok && data.success) {
        setIsApproved(true);
        setIsExecuting(true);
        setFeedback({
          type: 'success',
          message: 'Setup pipeline active. Executing operations on host environment.'
        });
      } else {
        setFeedback({
          type: 'error',
          message: data.error?.message || 'Failed to start execution.'
        });
      }
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `Network error executing plan: ${err.message}`
      });
    }
  };

  const handleCancelSetup = async () => {
    try {
      const res = await fetch(`/api/environment/cancel/${projectId}`, { method: 'POST' });
      if (res.ok) {
        setIsExecuting(false);
        setFeedback({
          type: 'info',
          message: 'Active setup execution cancelled by operator.'
        });
      }
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `Error cancelling setup: ${err.message}`
      });
    }
  };

  return (
    <div className="flex flex-col w-full px-4 pt-2 pb-28 max-w-lg mx-auto space-y-4">
      {/* Top Header */}
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
            Environment Setup
          </h1>
          <p className="text-[11px] font-mono-code text-[#ffb693] truncate">
            {hostEnv ? `${hostEnv.os} · Python ${hostEnv.python_version || '3.x'}` : 'Host Toolchain Discovery'}
          </p>
        </div>

        <span className="px-2.5 py-1 rounded-full text-[11px] font-mono-code font-bold bg-[#ff6b00]/20 text-[#ff6b00] border border-[#ff6b00]/30">
          ● {steps.length > 0 ? `${activeStepIndex + 1} of ${steps.length}` : 'Pending'}
        </span>
      </div>

      {feedback && (
        <Alert
          type={feedback.type}
          message={feedback.message}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Target Badge Card */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-3.5 space-y-1 shadow-md">
        <div className="flex items-center justify-between text-[11px] font-mono-code text-[#78716c]">
          <div className="flex items-center gap-2">
            <span>{hostEnv ? hostEnv.architecture : 'host-arch'}</span>
            <span>·</span>
            <span>{hostEnv ? `Node ${hostEnv.node_version || '24.x'}` : 'Node.js'}</span>
          </div>
          <span className="px-2 py-0.5 rounded text-[10px] font-mono-code bg-[#ff6b00]/15 text-[#ffb693] font-bold">
            {isApproved ? 'Approved Plan' : 'Approval Required'}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <h2 className="text-[16px] font-bold text-[#faf8f5] font-display">
            HyperStream Probe Workspace
          </h2>
          {previewUrl && (
            <a
              href={previewUrl}
              target="_blank"
              rel="noreferrer"
              className="flex items-center gap-1 text-[11px] font-mono-code text-[#ff6b00] hover:underline"
            >
              <span>Live Preview</span>
              <ExternalLink className="w-3 h-3" />
            </a>
          )}
        </div>
      </div>

      {/* Stepper Pipeline */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-4 shadow-md">
        <div className="flex items-center justify-between text-[11px] font-mono-code">
          <span className="text-[#ffb693] font-bold">PIPELINE STEPS</span>
          <span className="text-[#78716c]">
            {isExecuting ? `${progress}% Running` : isApproved ? '100% Completed' : 'Awaiting Approval'}
          </span>
        </div>

        {/* Step Nodes */}
        <div className="space-y-4 relative">
          <div className="absolute left-3.5 top-3 bottom-3 w-0.5 bg-[#292524]" />

          {steps.map((step) => {
            const isDone = step.status === 'completed';
            const isActive = step.status === 'active';
            const isFailed = step.status === 'failed';

            return (
              <div key={step.id} className="flex items-start gap-3 relative z-10">
                {isDone ? (
                  <div className="w-7 h-7 rounded-full bg-[#ff6b00] text-[#121110] flex items-center justify-center flex-shrink-0 shadow-[0_0_10px_rgba(255,107,0,0.3)]">
                    <Check className="w-4 h-4 stroke-[3]" />
                  </div>
                ) : isActive ? (
                  <div className="w-7 h-7 rounded-full bg-[#121110] border-2 border-[#ff6b00] flex items-center justify-center flex-shrink-0 shadow-[0_0_14px_rgba(255,107,0,0.4)]">
                    <div className="w-2.5 h-2.5 rounded-full bg-[#ff6b00] animate-beacon" />
                  </div>
                ) : isFailed ? (
                  <div className="w-7 h-7 rounded-full bg-red-600 text-white flex items-center justify-center flex-shrink-0">
                    ✕
                  </div>
                ) : (
                  <div className="w-7 h-7 rounded-full bg-[#121110] border-2 border-[#78716c] flex items-center justify-center flex-shrink-0" />
                )}

                <div className="flex-1 space-y-1 min-w-0">
                  <div className="flex items-center justify-between">
                    <h3 className={`text-[13px] font-bold ${isActive ? 'text-[#ffbd58]' : isDone ? 'text-[#faf8f5]' : 'text-[#78716c]'}`}>
                      {step.title}
                    </h3>
                    <span className="text-[11px] font-mono-code text-[#78716c]">
                      {step.status}
                    </span>
                  </div>
                  <p className="text-[12px] text-[#a8a29e] leading-snug">
                    {step.description}
                  </p>
                  {step.details && step.details.length > 0 && (
                    <div className="flex items-center gap-2 pt-1 flex-wrap">
                      {step.details.map((d, i) => (
                        <span key={i} className="px-2 py-0.5 rounded bg-[#121110] text-[10px] font-mono-code text-[#e2bfb0] border border-[#292524]">
                          {d.label}: {d.value}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Terminal Logs Drawer */}
      <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] overflow-hidden shadow-md">
        <button
          onClick={() => setIsLogsExpanded(!isLogsExpanded)}
          className="w-full px-4 py-3 bg-[#211f1d] border-b border-[#292524] flex items-center justify-between text-[12px] font-mono-code text-[#faf8f5] hover:bg-[#2c2927] transition-colors"
        >
          <div className="flex items-center gap-2">
            <Terminal className="w-4 h-4 text-[#ff6b00]" />
            <span className="font-bold">Terminal Logs</span>
          </div>
          <div className="flex items-center gap-2 text-[#78716c]">
            <span className="text-[11px]">stdout.trace</span>
            {isLogsExpanded ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
          </div>
        </button>

        <div className={`p-3.5 bg-[#121110] font-mono-code text-[11px] text-[#e2bfb0] space-y-1 overflow-x-auto leading-relaxed ${isLogsExpanded ? 'max-h-72' : 'max-h-36'} overflow-y-auto`}>
          {logs.length > 0 ? (
            logs.map((log, index) => (
              <div
                key={index}
                className={log.startsWith('>>') ? 'text-[#ff6b00] font-bold pt-1' : ''}
              >
                {log}
              </div>
            ))
          ) : (
            <div className="text-[#78716c]">Awaiting plan approval to execute pipeline operations.</div>
          )}
        </div>
      </div>

      {/* Explicit User Approval Gate (Mandatory: Never auto-runs on screen open) */}
      {!isApproved && !isExecuting && (
        <div className="p-3.5 rounded-2xl bg-[#241a14] border border-[#ff6b00]/40 space-y-3">
          <div className="flex items-center gap-2 text-[#ffb693] text-[12px] font-bold">
            <ShieldAlert className="w-4 h-4 text-[#ff6b00]" />
            <span>Safety Gate: Operator Approval Required</span>
          </div>
          <p className="text-[12px] text-[#a8a29e] leading-snug">
            Automated environment execution requires explicit confirmation before creating directories or running setup commands.
          </p>
          <button
            onClick={handleApproveAndExecute}
            className="w-full h-11 rounded-xl bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] font-bold text-[13px] flex items-center justify-center gap-2 shadow-[0_0_16px_rgba(255,107,0,0.3)] active:scale-95 transition-all cursor-pointer"
          >
            <Play className="w-4 h-4 fill-current" />
            <span>Approve &amp; Execute Setup Plan</span>
          </button>
        </div>
      )}

      {/* Execution in progress controls */}
      {isExecuting && (
        <div className="flex items-center gap-2.5 pt-1">
          <button
            onClick={handleCancelSetup}
            className="flex-1 h-11 rounded-xl bg-red-950/40 hover:bg-red-900/50 border border-red-800 text-red-200 font-semibold text-[13px] flex items-center justify-center gap-2 active:scale-95 transition-all cursor-pointer"
          >
            <Square className="w-4 h-4" />
            <span>Cancel Active Setup</span>
          </button>
        </div>
      )}
    </div>
  );
};
