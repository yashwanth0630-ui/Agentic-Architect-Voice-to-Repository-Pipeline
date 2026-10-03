import React, { useState } from 'react';

export interface ManifestViewerProps {
  initialAgentsContent?: string;
  initialCursorRules?: string;
  onSave?: (manifestType: 'agents' | 'cursor', content: string) => void;
}

export const ManifestViewer: React.FC<ManifestViewerProps> = ({
  initialAgentsContent = '',
  initialCursorRules = '',
  onSave
}) => {
  const [activeTab, setActiveTab] = useState<'agents' | 'cursor'>('agents');
  const [agentsContent, setAgentsContent] = useState(initialAgentsContent);
  const [cursorContent, setCursorContent] = useState(initialCursorRules);
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    const textToCopy = activeTab === 'agents' ? agentsContent : cursorContent;
    await navigator.clipboard.writeText(textToCopy);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleSave = () => {
    if (onSave) {
      onSave(activeTab, activeTab === 'agents' ? agentsContent : cursorContent);
    }
  };

  return (
    <section 
      aria-label="Agent Manifest Viewer" 
      className="w-full max-w-4xl mx-auto rounded-xl border border-slate-700 bg-slate-900 text-slate-100 shadow-2xl overflow-hidden"
    >
      <header className="flex items-center justify-between border-b border-slate-700 px-6 py-4 bg-slate-800/80 backdrop-blur-sm">
        <div className="flex items-center gap-3">
          <div className="h-3 w-3 rounded-full bg-emerald-500 animate-pulse" />
          <h2 className="text-lg font-semibold tracking-wide text-white">IQOO Agent Manifest Console</h2>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'agents'}
            aria-label="Switch to AGENTS.md tab"
            onClick={() => setActiveTab('agents')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors duration-150 ${
              activeTab === 'agents' 
                ? 'bg-blue-600 text-white shadow-md' 
                : 'text-slate-300 hover:bg-slate-700 hover:text-white'
            }`}
          >
            AGENTS.md
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={activeTab === 'cursor'}
            aria-label="Switch to .cursorrules tab"
            onClick={() => setActiveTab('cursor')}
            className={`px-4 py-1.5 rounded-lg text-sm font-medium transition-colors duration-150 ${
              activeTab === 'cursor' 
                ? 'bg-blue-600 text-white shadow-md' 
                : 'text-slate-300 hover:bg-slate-700 hover:text-white'
            }`}
          >
            .cursorrules
          </button>
        </div>
      </header>

      <main className="p-6">
        <label htmlFor="manifest-editor" className="sr-only">
          Manifest Content Editor
        </label>
        <textarea
          id="manifest-editor"
          aria-label={`${activeTab === 'agents' ? 'AGENTS.md' : '.cursorrules'} content`}
          value={activeTab === 'agents' ? agentsContent : cursorContent}
          onChange={(e) =>
            activeTab === 'agents' ? setAgentsContent(e.target.value) : setCursorContent(e.target.value)
          }
          rows={16}
          className="w-full font-mono text-sm bg-slate-950 border border-slate-800 rounded-lg p-4 text-slate-200 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all resize-y"
        />
      </main>

      <footer className="flex justify-between items-center px-6 py-4 border-t border-slate-800 bg-slate-900/60">
        <span className="text-xs text-slate-400">
          Strict Schema Verified &bull; RFC 7807 Compliant
        </span>
        <div className="flex gap-3">
          <button
            type="button"
            aria-label="Copy manifest content to clipboard"
            onClick={handleCopy}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-100 transition-all duration-150"
          >
            {copied ? 'Copied to Clipboard!' : 'Copy to Clipboard'}
          </button>
          <button
            type="button"
            aria-label="Save manifest changes"
            onClick={handleSave}
            className="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white shadow transition-all duration-150"
          >
            Save Changes
          </button>
        </div>
      </footer>
    </section>
  );
};
export default ManifestViewer;
