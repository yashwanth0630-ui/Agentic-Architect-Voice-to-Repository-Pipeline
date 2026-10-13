import React, { useState, useEffect } from 'react';
import { bridgeService, BridgeDiagnostics } from '../../services/bridgeService';
import { Alert } from './Alert';
import { TextShimmer } from './TextShimmer';
import { HoldToConfirm } from './HoldToConfirm';
import {
  Activity,
  Wifi,
  WifiOff,
  Server,
  RefreshCw,
  X,
  ExternalLink,
  ShieldCheck,
  CheckCircle2,
  AlertTriangle,
  Cpu,
} from 'lucide-react';

interface BridgeStatusModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSyncComplete?: () => void;
}

export const BridgeStatusModal: React.FC<BridgeStatusModalProps> = ({
  isOpen,
  onClose,
  onSyncComplete,
}) => {
  const [diagnostics, setDiagnostics] = useState<BridgeDiagnostics | null>(null);
  const [inputUrl, setInputUrl] = useState(bridgeService.getBridgeUrl());
  const [isTesting, setIsTesting] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncFeedback, setSyncFeedback] = useState<{ type: 'success' | 'info' | 'error'; message: string } | null>(null);
  const [ollamaStatus, setOllamaStatus] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = bridgeService.subscribe((diag) => {
      setDiagnostics(diag);
      setInputUrl(diag.url);
    });
    return unsubscribe;
  }, []);

  if (!isOpen) return null;

  const handleTestConnection = async () => {
    setIsTesting(true);
    setSyncFeedback(null);
    setOllamaStatus(null);
    try {
      const diag = await bridgeService.setBridgeUrl(inputUrl);
      setDiagnostics(diag);
    } finally {
      setIsTesting(false);
    }
  };

  const handleManualSync = async () => {
    setIsSyncing(true);
    setSyncFeedback(null);
    try {
      const result = await bridgeService.syncVault();
      if (result.success) {
        setSyncFeedback({
          type: 'success',
          message: `Idempotent delta sync complete. ${result.syncedCount} item(s) reconciled with bridge.`,
        });
        onSyncComplete?.();
      } else {
        setSyncFeedback({
          type: 'error',
          message: `Bridge sync failed: ${result.error || 'Connection offline'}. Pending items stored in IndexedDB.`,
        });
      }
    } finally {
      setIsSyncing(false);
    }
  };

  const handleTestOllama = async () => {
    setOllamaStatus('Probing Ollama daemon through bridge...');
    try {
      const res = await fetch('/api/bridge/ollama', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          bridgeUrl: inputUrl,
          prompt: 'ping',
          model: 'qwen2.5-coder:7b',
        }),
      });
      const data = await res.json();
      if (res.ok && data.success) {
        setOllamaStatus(`Ollama Active (Model: ${data.model})`);
      } else {
        setOllamaStatus(`Ollama Unreachable: ${data.error || 'Daemon not running on bridge'}`);
      }
    } catch (e: any) {
      setOllamaStatus(`Connection error: ${e.message}`);
    }
  };

  const isConnected = diagnostics?.status === 'CONNECTED';

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-150">
      <div
        className="w-full max-w-md rounded-2xl bg-[#1d1b19] border border-[#292524] p-5 shadow-2xl space-y-4 text-left animate-in zoom-in-95 duration-150"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#292524] pb-3">
          <div className="flex items-center gap-2">
            <div className={`p-1.5 rounded-lg ${isConnected ? 'bg-[#ff6b00]/20 text-[#ff6b00]' : 'bg-[#291717] text-[#ffdad6]'}`}>
              <Server className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-[15px] font-bold text-[#faf8f5] leading-tight font-display">
                FastAPI Laptop Bridge
              </h3>
              <p className="text-[11px] font-mono-code text-[#a8a29e]">
                SQLite Vault & Local AI Connectivity
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-7 h-7 rounded-lg flex items-center justify-center text-[#a8a29e] hover:text-[#faf8f5] hover:bg-[#292524] transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Current Status Pill */}
        <div className="flex items-center justify-between p-3 rounded-xl bg-[#151311] border border-[#292524]">
          <div className="flex items-center gap-2.5">
            <span
              className={`w-2.5 h-2.5 rounded-full ${
                isConnected
                  ? 'bg-[#ff6b00] shadow-[0_0_8px_#ff6b00]'
                  : diagnostics?.status === 'CONNECTING'
                  ? 'bg-[#ea9f00] animate-pulse'
                  : 'bg-[#78716c]'
              }`}
            />
            <div>
              <div className="text-xs font-semibold text-[#faf8f5] flex items-center gap-1.5">
                <span>{diagnostics?.status || 'DISCONNECTED'}</span>
                {diagnostics?.latencyMs !== undefined && (
                  <span className="text-[10px] font-mono-code text-[#ffb693]">
                    ({diagnostics.latencyMs}ms)
                  </span>
                )}
              </div>
              <div className="text-[11px] text-[#a8a29e] font-mono-code truncate max-w-[240px]">
                {diagnostics?.message || 'Bridge not connected.'}
              </div>
            </div>
          </div>

          <button
            onClick={handleTestConnection}
            disabled={isTesting}
            className="px-2.5 py-1.5 rounded-lg bg-[#271e16] hover:bg-[#34261b] border border-[#ff6b00]/30 text-[#ffb693] text-xs font-mono-code font-bold flex items-center gap-1 disabled:opacity-50 transition-all cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isTesting ? 'animate-spin' : ''}`} />
            <span>Ping</span>
          </button>
        </div>

        {/* URL Configuration Input */}
        <div className="space-y-1.5">
          <label className="text-[11px] font-mono-code text-[#a8a29e] uppercase tracking-wider block">
            Laptop Bridge Address
          </label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={inputUrl}
              onChange={(e) => setInputUrl(e.target.value)}
              placeholder="http://localhost:8000"
              className="flex-1 bg-[#151311] border border-[#292524] rounded-lg px-3 py-2 text-xs font-mono-code text-[#faf8f5] focus:outline-none focus:border-[#ff6b00]/60 placeholder-[#78716c]"
            />
            <button
              onClick={handleTestConnection}
              disabled={isTesting}
              className="px-3 py-2 rounded-lg bg-[#ff6b00] hover:bg-[#ff8533] text-[#121110] text-xs font-semibold disabled:opacity-50 transition-all cursor-pointer"
            >
              Apply
            </button>
          </div>
          <p className="text-[10px] text-[#78716c] font-sans">
            Examples: <code className="text-[#ffb693]">http://localhost:8000</code>, LAN IP, or Tailscale address.
          </p>
        </div>

        {/* Sync Feedback Alert */}
        {syncFeedback && (
          <Alert
            type={syncFeedback.type}
            message={syncFeedback.message}
            onDismiss={() => setSyncFeedback(null)}
          />
        )}

        {/* Ollama Probe */}
        <div className="p-3 rounded-xl bg-[#151311] border border-[#292524] space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-xs font-medium text-[#e7e1de]">
              <Cpu className="w-3.5 h-3.5 text-[#ff6b00]" />
              <span>Local Ollama Probe (Qwen 2.5 Coder)</span>
            </div>
            <button
              onClick={handleTestOllama}
              className="text-[11px] font-mono-code text-[#ffb693] hover:underline"
            >
              Check Daemon
            </button>
          </div>
          {ollamaStatus && (
            <div className="text-[11px] font-mono-code text-[#a8a29e] bg-[#1d1b19] p-2 rounded border border-[#292524]">
              {ollamaStatus}
            </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex items-center justify-between pt-2 border-t border-[#292524]">
          <button
            onClick={handleManualSync}
            disabled={isSyncing}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#24211e] hover:bg-[#2c2927] border border-[#373432] text-[#faf8f5] text-xs font-mono-code transition-all cursor-pointer disabled:opacity-50"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
            <span>{isSyncing ? 'Syncing...' : 'Sync Now'}</span>
          </button>

          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-[#271e16] hover:bg-[#34261b] text-[#ffb693] border border-[#ff6b00]/30 text-xs font-semibold transition-all cursor-pointer"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
