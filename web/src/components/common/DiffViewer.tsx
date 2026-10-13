import React, { useState } from 'react';
import { CodeDiffFile } from '../../types';
import { FileCode, ChevronDown, ChevronRight, Plus, Minus, Check } from 'lucide-react';

interface DiffViewerProps {
  diffs: CodeDiffFile[];
  activeFilePath?: string;
  onSelectFile?: (path: string) => void;
  className?: string;
}

export const DiffViewer: React.FC<DiffViewerProps> = ({
  diffs,
  activeFilePath,
  onSelectFile,
  className = '',
}) => {
  const [selectedFile, setSelectedFile] = useState<string>(
    activeFilePath || (diffs[0]?.path ?? '')
  );
  const [collapsedFiles, setCollapsedFiles] = useState<Record<string, boolean>>({});

  const currentDiff = diffs.find((d) => d.path === (activeFilePath || selectedFile)) || diffs[0];

  const toggleCollapse = (path: string) => {
    setCollapsedFiles((prev) => ({
      ...prev,
      [path]: !prev[path],
    }));
  };

  const handleFileClick = (path: string) => {
    setSelectedFile(path);
    if (onSelectFile) onSelectFile(path);
  };

  if (!diffs || diffs.length === 0) {
    return (
      <div className="p-4 rounded-xl bg-[#121110] border border-[#292524] text-center text-[#78716c] font-mono-code text-[12px]">
        No pending diffs detected for this repository.
      </div>
    );
  }

  const totalAdditions = diffs.reduce((acc, d) => acc + d.additions, 0);
  const totalDeletions = diffs.reduce((acc, d) => acc + d.deletions, 0);

  return (
    <div className={`space-y-3 ${className}`}>
      {/* File Switcher Tabs */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 overflow-x-auto py-1 scrollbar-none max-w-full">
          {diffs.map((diff) => {
            const isSelected = diff.path === (activeFilePath || selectedFile);
            return (
              <button
                key={diff.path}
                onClick={() => handleFileClick(diff.path)}
                className={`px-3 py-1.5 rounded-lg text-[11px] font-mono-code flex items-center gap-2 border transition-all cursor-pointer whitespace-nowrap ${
                  isSelected
                    ? 'bg-[#29221d] border-[#ff6b00]/60 text-[#faf8f5] shadow-[0_0_10px_rgba(255,107,0,0.15)]'
                    : 'bg-[#181615] border-[#292524] text-[#a8a29e] hover:text-[#faf8f5] hover:border-[#3a3532]'
                }`}
              >
                <FileCode className={`w-3.5 h-3.5 ${isSelected ? 'text-[#ff6b00]' : 'text-[#78716c]'}`} />
                <span className="truncate max-w-[140px]">{diff.path.split('/').pop()}</span>
                <span className="flex items-center text-[10px] font-bold">
                  <span className="text-[#4ade80]">+{diff.additions}</span>
                  {diff.deletions > 0 && <span className="text-[#f87171] ml-1">-{diff.deletions}</span>}
                </span>
              </button>
            );
          })}
        </div>

        <div className="text-[11px] font-mono-code text-[#a8a29e] flex-shrink-0 pl-2">
          <span className="text-[#4ade80]">+{totalAdditions}</span> /{' '}
          <span className="text-[#f87171]">-{totalDeletions}</span>
        </div>
      </div>

      {/* Main Diff File Container */}
      {currentDiff && (
        <div className="rounded-xl bg-[#121110] border border-[#292524] overflow-hidden shadow-sm">
          {/* File Header */}
          <div className="px-3 py-2 bg-[#191715] border-b border-[#292524] flex items-center justify-between text-[11px] font-mono-code">
            <div
              className="flex items-center gap-2 cursor-pointer select-none"
              onClick={() => toggleCollapse(currentDiff.path)}
            >
              {collapsedFiles[currentDiff.path] ? (
                <ChevronRight className="w-3.5 h-3.5 text-[#78716c]" />
              ) : (
                <ChevronDown className="w-3.5 h-3.5 text-[#ff6b00]" />
              )}
              <span className="text-[#faf8f5] font-semibold">{currentDiff.path}</span>
              <span
                className={`px-1.5 py-0.2 rounded text-[10px] uppercase font-bold ${
                  currentDiff.status === 'added'
                    ? 'bg-[#152a18] text-[#4ade80]'
                    : currentDiff.status === 'deleted'
                    ? 'bg-[#2b1717] text-[#f87171]'
                    : 'bg-[#ff6b00]/15 text-[#ffb693]'
                }`}
              >
                {currentDiff.status}
              </span>
            </div>

            <div className="text-[10px] text-[#78716c]">
              {currentDiff.hunks.length} hunk{currentDiff.hunks.length !== 1 ? 's' : ''}
            </div>
          </div>

          {/* Hunk Lines */}
          {!collapsedFiles[currentDiff.path] && (
            <div className="overflow-x-auto text-[11px] font-mono-code">
              {currentDiff.hunks.map((hunk, hIdx) => (
                <div key={hIdx} className="border-b border-[#24211e] last:border-b-0">
                  {/* Hunk Header */}
                  <div className="px-3 py-1 bg-[#1a1715] text-[#ea9f00] text-[10px] select-none border-b border-[#221f1d]">
                    @@ -{hunk.oldStart},{hunk.oldLines} +{hunk.newStart},{hunk.newLines} @@
                  </div>

                  {/* Lines */}
                  <div className="divide-y divide-transparent">
                    {hunk.lines.map((line, lIdx) => {
                      const isAdd = line.type === 'add';
                      const isDel = line.type === 'del';

                      return (
                        <div
                          key={lIdx}
                          className={`flex items-start leading-5 py-0.5 px-1 hover:brightness-110 transition-colors ${
                            isAdd
                              ? 'bg-[#132817]/70 text-[#86efac]'
                              : isDel
                              ? 'bg-[#2e1515]/70 text-[#fca5a5]'
                              : 'text-[#d6d3d1]'
                          }`}
                        >
                          {/* Line numbers */}
                          <div className="flex select-none text-[10px] text-[#57534e] w-14 flex-shrink-0 text-right pr-2 space-x-1">
                            <span className="w-6 text-right">
                              {line.oldLineNum ?? ''}
                            </span>
                            <span className="w-6 text-right">
                              {line.newLineNum ?? ''}
                            </span>
                          </div>

                          {/* Line Type Marker */}
                          <span
                            className={`w-4 text-center select-none font-bold ${
                              isAdd
                                ? 'text-[#4ade80]'
                                : isDel
                                ? 'text-[#f87171]'
                                : 'text-transparent'
                            }`}
                          >
                            {isAdd ? '+' : isDel ? '-' : ' '}
                          </span>

                          {/* Content */}
                          <pre className="font-mono-code text-[11px] whitespace-pre overflow-x-auto flex-1 pl-1">
                            {line.content.replace(/^[+-]/, '')}
                          </pre>
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
