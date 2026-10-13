import React, { useState, useRef, useEffect, useMemo } from 'react';
import { IdeaEntity } from '../../db/schemas';
import { BeaconDatabase } from '../../db/BeaconDatabase';
import { ScreenType } from '../../types';
import {
  Search,
  Plus,
  RefreshCw,
  UploadCloud,
  Edit3,
  Copy,
  Trash2,
  FileText,
  AlertTriangle,
  Hash,
  Type,
  AlignLeft,
  Layers,
  X,
} from 'lucide-react';
import { Alert } from '../common/Alert';
import { DropdownMenu } from '../common/DropdownMenu';
import { HoldToConfirm } from '../common/HoldToConfirm';

interface VaultScreenProps {
  ideas: IdeaEntity[];
  onNavigate: (screen: ScreenType) => void;
  onSelectIdea: (idea: IdeaEntity) => void;
  onEditIdea: (idea: IdeaEntity) => void;
}

type SearchScope = 'all' | 'title' | 'tag' | 'content';

interface IdeaMatchMeta {
  idea: IdeaEntity;
  matchesTitle: boolean;
  matchedTag?: string;
  matchesContent: boolean;
  contentSnippet?: string;
}

export const VaultScreen: React.FC<VaultScreenProps> = ({
  ideas,
  onNavigate,
  onSelectIdea,
  onEditIdea,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchScope, setSearchScope] = useState<SearchScope>('all');
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [deletingIdeaId, setDeletingIdeaId] = useState<string | null>(null);
  const [feedbackToast, setFeedbackToast] = useState<{ type: 'success' | 'info' | 'warning' | 'error'; message: string } | null>(null);

  const searchInputRef = useRef<HTMLInputElement | null>(null);

  // Keyboard shortcut listener for Cmd+K / Ctrl+K and Escape
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
        searchInputRef.current?.select();
      } else if (e.key === 'Escape' && document.activeElement === searchInputRef.current) {
        setSearchQuery('');
        searchInputRef.current?.blur();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Check for sync errors
  const syncErrorIdeas = ideas.filter((i) => i.status === 'error');
  const hasSyncErrors = syncErrorIdeas.length > 0;

  // Auto-switch search scope if query starts with #
  const effectiveScope: SearchScope = useMemo(() => {
    if (searchQuery.trim().startsWith('#')) {
      return 'tag';
    }
    return searchScope;
  }, [searchQuery, searchScope]);

  // Extract snippet around matched query in content
  const extractContentSnippet = (content: string, queryStr: string): string | undefined => {
    if (!content || !queryStr) return undefined;
    const lowerContent = content.toLowerCase();
    const lowerQuery = queryStr.toLowerCase();
    const matchIndex = lowerContent.indexOf(lowerQuery);
    if (matchIndex === -1) return undefined;

    const start = Math.max(0, matchIndex - 35);
    const end = Math.min(content.length, matchIndex + queryStr.length + 45);
    let snippet = content.slice(start, end).replace(/\n/g, ' ').trim();
    if (start > 0) snippet = '...' + snippet;
    if (end < content.length) snippet = snippet + '...';
    return snippet;
  };

  // Filter ideas with deep matching against title, tag, or content
  const searchResults: IdeaMatchMeta[] = useMemo(() => {
    const queryTrim = searchQuery.trim().toLowerCase();
    const cleanQuery = queryTrim.replace(/^#/, '');

    return ideas
      .filter((idea) => {
        // Category filter
        if (selectedCategory !== 'all' && idea.category.toLowerCase() !== selectedCategory.toLowerCase()) {
          return false;
        }
        return true;
      })
      .map((idea) => {
        if (!queryTrim) {
          return {
            idea,
            matchesTitle: false,
            matchesContent: false,
          };
        }

        const matchesTitle = idea.title.toLowerCase().includes(queryTrim);

        const matchedTag = idea.tags.find((t) => {
          const tLower = t.toLowerCase();
          return tLower.includes(queryTrim) || tLower.replace(/^#/, '').includes(cleanQuery);
        });

        const fullContent = `${idea.preview} ${idea.content || ''}`.toLowerCase();
        const matchesContent = fullContent.includes(cleanQuery);
        const snippet = matchesContent
          ? extractContentSnippet(idea.content || idea.preview, cleanQuery)
          : undefined;

        let isMatch = false;
        switch (effectiveScope) {
          case 'title':
            isMatch = matchesTitle;
            break;
          case 'tag':
            isMatch = Boolean(matchedTag);
            break;
          case 'content':
            isMatch = matchesContent;
            break;
          case 'all':
          default:
            isMatch = matchesTitle || Boolean(matchedTag) || matchesContent;
            break;
        }

        return {
          idea,
          matchesTitle,
          matchedTag,
          matchesContent,
          contentSnippet: snippet,
          isMatch,
        };
      })
      .filter((item) => (queryTrim ? (item as any).isMatch : true));
  }, [ideas, searchQuery, effectiveScope, selectedCategory]);

  const handleResolveFailures = async () => {
    const count = await BeaconDatabase.ideaDao.resolveAllErrors();
    setFeedbackToast({
      type: count > 0 ? 'success' : 'info',
      message: count > 0 ? `Reconciled ${count} offline ideas with laptop bridge.` : 'No pending offline ideas to reconcile.',
    });
    setTimeout(() => setFeedbackToast(null), 3000);
  };

  const handleRetrySync = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const res = await BeaconDatabase.ideaDao.retrySync(id);
    if (res && res.status === 'synced') {
      setFeedbackToast({
        type: 'success',
        message: 'Idea synchronized successfully with laptop bridge.',
      });
    } else {
      setFeedbackToast({
        type: 'error',
        message: res?.syncErrorMessage || 'Laptop bridge unreachable. Retained in offline queue.',
      });
    }
    setTimeout(() => setFeedbackToast(null), 3000);
  };

  const handlePushDraft = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    const res = await BeaconDatabase.ideaDao.pushDraft(id);
    if (res && res.status === 'synced') {
      setFeedbackToast({
        type: 'success',
        message: 'Draft pushed and synchronized with laptop bridge.',
      });
    } else {
      setFeedbackToast({
        type: 'info',
        message: 'Draft saved locally. Will sync when bridge connects.',
      });
    }
    setTimeout(() => setFeedbackToast(null), 2500);
  };

  const handleDuplicate = (idea: IdeaEntity) => {
    BeaconDatabase.ideaDao.insert({
      title: `${idea.title} (Copy)`,
      category: idea.category,
      preview: idea.preview,
      content: idea.content,
      tags: [...idea.tags],
      status: 'unsynced',
      isDemo: false,
      relativeTime: 'Edited just now',
    });
    setFeedbackToast({
      type: 'success',
      message: 'Idea duplicated in local vault cache.',
    });
    setTimeout(() => setFeedbackToast(null), 2500);
  };

  const handleDeleteConfirmed = (id: string) => {
    BeaconDatabase.ideaDao.delete(id);
    setDeletingIdeaId(null);
    setFeedbackToast({
      type: 'info',
      message: 'Idea permanently deleted from device storage.',
    });
    setTimeout(() => setFeedbackToast(null), 2500);
  };

  const handleQuickTagClick = (tag: string) => {
    setSearchQuery(tag);
    setSearchScope('tag');
    searchInputRef.current?.focus();
  };

  return (
    <div className="flex flex-col w-full px-5 pt-3 pb-28 max-w-lg mx-auto space-y-4">
      {/* Toast Alert Feedback (Pattern 1) */}
      {feedbackToast && (
        <Alert
          type={feedbackToast.type}
          message={feedbackToast.message}
          onDismiss={() => setFeedbackToast(null)}
        />
      )}

      {/* Sync Error Alert Banner (Matches Image 16.jpeg) */}
      {hasSyncErrors && (
        <div className="flex items-center justify-between px-3 py-2.5 rounded-xl bg-[#271e16] border border-[#4a2e18] shadow-sm">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#ea9f00] animate-pulse" />
            <span className="text-[12px] font-mono-code text-[#ffddb1]">
              {syncErrorIdeas.length} offline sync error pending
            </span>
          </div>
          <button
            onClick={handleResolveFailures}
            className="flex items-center gap-1 text-[12px] font-mono-code font-bold text-[#ffb693] hover:text-[#ff7a1a] transition-colors cursor-pointer"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            <span>Resolve Failure</span>
          </button>
        </div>
      )}

      {/* Global Search Header Box */}
      <div className="space-y-2">
        {/* Search Input Bar (Matches Image 16.jpeg) */}
        <div className="relative">
          <div className="w-full bg-[#121110] rounded-xl px-3.5 py-2.5 border border-[#292524] flex items-center justify-between gap-2 shadow-inner focus-within:border-[#ff6b00]/70 focus-within:shadow-[0_0_14px_rgba(255,107,0,0.15)] transition-all">
            <div className="flex items-center gap-2.5 flex-1 min-w-0">
              <Search className="w-4 h-4 text-[#ff6b00] flex-shrink-0" />
              <input
                ref={searchInputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search ideas by title, tag, or content..."
                className="w-full bg-transparent text-[13px] text-[#faf8f5] placeholder:text-[#78716c] focus:outline-none"
              />
            </div>
            <div className="flex items-center gap-1.5 flex-shrink-0">
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery('');
                    searchInputRef.current?.focus();
                  }}
                  className="p-1 rounded-md text-[#78716c] hover:text-[#faf8f5] hover:bg-[#24211e] transition-colors"
                  title="Clear Search"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
              <kbd className="px-1.5 py-0.5 rounded text-[10px] font-mono-code bg-[#211f1d] text-[#78716c] border border-[#292524] select-none">
                ⌘K
              </kbd>
            </div>
          </div>
        </div>

        {/* Global Search Scope Filter Toolbar (Title, Tag, Content, All) */}
        <div className="flex items-center justify-between gap-1 px-1">
          <div className="flex items-center gap-1 p-0.5 rounded-lg bg-[#121110] border border-[#292524] text-[11px] font-mono-code">
            <button
              type="button"
              onClick={() => setSearchScope('all')}
              className={`px-2 py-0.5 rounded-md flex items-center gap-1 transition-all ${
                searchScope === 'all'
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
            >
              <Layers className="w-3 h-3" />
              <span>All</span>
            </button>
            <button
              type="button"
              onClick={() => setSearchScope('title')}
              className={`px-2 py-0.5 rounded-md flex items-center gap-1 transition-all ${
                searchScope === 'title'
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
            >
              <Type className="w-3 h-3" />
              <span>Title</span>
            </button>
            <button
              type="button"
              onClick={() => setSearchScope('tag')}
              className={`px-2 py-0.5 rounded-md flex items-center gap-1 transition-all ${
                searchScope === 'tag'
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
            >
              <Hash className="w-3 h-3" />
              <span>Tag</span>
            </button>
            <button
              type="button"
              onClick={() => setSearchScope('content')}
              className={`px-2 py-0.5 rounded-md flex items-center gap-1 transition-all ${
                searchScope === 'content'
                  ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
                  : 'text-[#a8a29e] hover:text-[#faf8f5]'
              }`}
            >
              <AlignLeft className="w-3 h-3" />
              <span>Content</span>
            </button>
          </div>

          {searchQuery && (
            <span className="text-[11px] font-mono-code text-[#ffb693]">
              {searchResults.length} match{searchResults.length === 1 ? '' : 'es'}
            </span>
          )}
        </div>

        {/* Quick Tag Pills for Rapid Exploration */}
        {!searchQuery && (
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar pt-0.5">
            <span className="text-[10px] font-mono-code text-[#78716c] uppercase">Quick:</span>
            {['#wasm', '#sqlite', '#ebpf', '#distributed', '#runtime', '#systems'].map((tag) => (
              <button
                key={tag}
                type="button"
                onClick={() => handleQuickTagClick(tag)}
                className="px-2 py-0.5 rounded-md text-[10px] font-mono-code bg-[#1a1715] hover:bg-[#24211e] text-[#a8a29e] hover:text-[#ffb693] border border-[#292524] transition-colors cursor-pointer"
              >
                {tag}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Filter Categories */}
      <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
        {['all', 'runtime', 'database', 'architecture', 'systems', 'infra', 'wasm'].map((cat) => (
          <button
            key={cat}
            onClick={() => setSelectedCategory(cat)}
            className={`px-3 py-1 rounded-lg text-[12px] font-mono-code capitalize whitespace-nowrap transition-all cursor-pointer ${
              selectedCategory === cat
                ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-[0_0_12px_rgba(255,107,0,0.25)]'
                : 'bg-[#211f1d] text-[#a8a29e] hover:bg-[#2c2927]'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Hold-to-confirm Delete Modal Overlay (Pattern 2) */}
      {deletingIdeaId && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="w-full max-w-sm">
            <HoldToConfirm
              label="Hold to Delete Idea"
              sublabel="Permanent removal from local encrypted database. Requires 1.5s hold."
              onConfirm={() => handleDeleteConfirmed(deletingIdeaId)}
              icon={<Trash2 className="w-5 h-5" />}
            />
            <button
              onClick={() => setDeletingIdeaId(null)}
              className="w-full mt-2.5 py-2 rounded-xl bg-[#24211e] hover:bg-[#2c2927] text-[13px] text-[#a8a29e] font-medium transition-colors cursor-pointer"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {/* Idea Cards List (Matches Image 16.jpeg) */}
      <div className="space-y-2.5 pt-1">
        {searchResults.length === 0 ? (
          <div className="py-12 text-center rounded-2xl bg-[#1d1b19] border border-[#292524] p-6 space-y-3">
            <AlertTriangle className="w-8 h-8 text-[#ea9f00] mx-auto opacity-70" />
            <h4 className="text-[15px] font-semibold text-[#faf8f5]">No ideas matched your search</h4>
            <p className="text-[12px] text-[#a8a29e] max-w-xs mx-auto">
              No results found for &ldquo;{searchQuery}&rdquo; in {searchScope === 'all' ? 'title, tag, or content' : searchScope}. Try clearing filters or refining terms.
            </p>
            <div className="flex items-center justify-center gap-2 pt-2">
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="px-3.5 py-1.5 rounded-xl bg-[#24211e] hover:bg-[#2c2927] text-[#a8a29e] hover:text-[#faf8f5] text-[12px] font-mono-code transition-colors"
                >
                  Clear search
                </button>
              )}
              <button
                onClick={() => onNavigate('capture')}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#ff6b00] text-[#121110] font-bold text-[12px]"
              >
                <Plus className="w-4 h-4 stroke-[3]" />
                <span>Capture new idea</span>
              </button>
            </div>
          </div>
        ) : (
          searchResults.map(({ idea, matchesTitle, matchedTag, matchesContent, contentSnippet }) => {
            const isError = idea.status === 'error';
            const isDraft = idea.status === 'draft';
            const isSynced = idea.status === 'synced';

            return (
              <div
                key={idea.id}
                onClick={() => onSelectIdea(idea)}
                className="rounded-xl bg-[#1d1b19] hover:bg-[#24211e] border border-[#292524] p-4 flex flex-col justify-between shadow-sm transition-all cursor-pointer group"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="space-y-1 min-w-0 flex-1">
                    {/* Status & Category metadata */}
                    <div className="flex items-center gap-2 text-[11px] font-mono-code">
                      <span className="flex items-center gap-1.5">
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            isError
                              ? 'bg-[#ff6b00]'
                              : isDraft
                              ? 'bg-[#a8a29e]'
                              : 'bg-[#ff6b00]'
                          }`}
                        />
                        <span
                          className={
                            isError
                              ? 'text-[#ff6b00]'
                              : isDraft
                              ? 'text-[#a8a29e]'
                              : 'text-[#e2bfb0]'
                          }
                        >
                          {idea.relativeTime}
                        </span>
                      </span>

                      {idea.isDemo ? (
                        <span className="text-[10px] text-[#78716c] px-1 rounded bg-[#211f1d]">
                          demo
                        </span>
                      ) : (
                        <span className="text-[10px] text-[#ffb693] px-1 rounded bg-[#ff6b00]/15">
                          local
                        </span>
                      )}

                      {/* Search Match Badge */}
                      {searchQuery && (
                        <span className="text-[10px] text-[#ffb693] px-1.5 py-0.2 rounded bg-[#ff6b00]/20 font-bold">
                          {matchesTitle
                            ? 'Title match'
                            : matchedTag
                            ? `Tag: ${matchedTag}`
                            : matchesContent
                            ? 'Content match'
                            : 'Match'}
                        </span>
                      )}
                    </div>

                    {/* Title */}
                    <h3 className="text-[15px] font-semibold text-[#faf8f5] group-hover:text-[#ffb693] transition-colors pt-0.5">
                      {idea.title}
                    </h3>

                    {/* Preview or Content Match Snippet */}
                    {contentSnippet ? (
                      <div className="p-2 rounded-lg bg-[#121110] border border-[#292524] mt-1">
                        <span className="text-[10px] font-mono-code text-[#ff6b00] block mb-0.5">
                          Matched in content:
                        </span>
                        <p className="text-[12px] font-mono-code text-[#e2bfb0] line-clamp-2">
                          {contentSnippet}
                        </p>
                      </div>
                    ) : (
                      <p className="text-[13px] text-[#a8a29e] line-clamp-2 leading-relaxed">
                        {idea.preview}
                      </p>
                    )}

                    {/* Tags List */}
                    {idea.tags && idea.tags.length > 0 && (
                      <div className="flex items-center gap-1.5 pt-1.5 overflow-x-auto no-scrollbar">
                        {idea.tags.map((tag) => (
                          <span
                            key={tag}
                            onClick={(e) => {
                              e.stopPropagation();
                              handleQuickTagClick(tag);
                            }}
                            className={`px-1.5 py-0.5 rounded text-[10px] font-mono-code transition-colors ${
                              matchedTag === tag
                                ? 'bg-[#ff6b00] text-[#121110] font-bold'
                                : 'bg-[#121110] text-[#a8a29e] hover:text-[#ffb693]'
                            }`}
                          >
                            {tag}
                          </span>
                        ))}
                      </div>
                    )}
                  </div>

                  {/* Dropdown Menu (Pattern 3) */}
                  <div className="flex-shrink-0" onClick={(e) => e.stopPropagation()}>
                    <DropdownMenu
                      items={[
                        {
                          id: 'details',
                          label: 'View Spec',
                          icon: <FileText className="w-3.5 h-3.5" />,
                          onClick: () => onSelectIdea(idea),
                        },
                        {
                          id: 'edit',
                          label: 'Edit Spark',
                          icon: <Edit3 className="w-3.5 h-3.5" />,
                          onClick: () => onEditIdea(idea),
                        },
                        {
                          id: 'duplicate',
                          label: 'Duplicate',
                          icon: <Copy className="w-3.5 h-3.5" />,
                          onClick: () => handleDuplicate(idea),
                        },
                        {
                          id: 'delete',
                          label: 'Delete Idea...',
                          isDestructive: true,
                          icon: <Trash2 className="w-3.5 h-3.5" />,
                          onClick: () => setDeletingIdeaId(idea.id),
                        },
                      ]}
                    />
                  </div>
                </div>

                {/* Bottom Contextual Actions for Error / Draft */}
                {isError && (
                  <div className="mt-3 pt-2.5 border-t border-[#292524] flex items-center justify-between text-[11px] font-mono-code text-[#ff6b00]">
                    <span className="truncate pr-2">
                      {idea.syncErrorMessage || 'Daemon bridge unreachable'}
                    </span>
                    <button
                      onClick={(e) => handleRetrySync(idea.id, e)}
                      className="flex items-center gap-1 px-2.5 py-1 rounded bg-[#ff6b00]/15 hover:bg-[#ff6b00]/25 text-[#ff6b00] font-bold active:scale-95 transition-all flex-shrink-0"
                    >
                      <RefreshCw className="w-3 h-3" />
                      <span>Retry</span>
                    </button>
                  </div>
                )}

                {isDraft && (
                  <div className="mt-3 pt-2.5 border-t border-[#292524] flex items-center justify-between text-[11px] font-mono-code text-[#a8a29e]">
                    <span>Offline draft on device</span>
                    <button
                      onClick={(e) => handlePushDraft(idea.id, e)}
                      className="flex items-center gap-1 px-2.5 py-1 rounded bg-[#24211e] hover:bg-[#2c2927] text-[#ffb693] font-bold active:scale-95 transition-all flex-shrink-0"
                    >
                      <UploadCloud className="w-3 h-3" />
                      <span>Push ↑</span>
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      {/* Floating Action / Bottom status info */}
      <div className="pt-2 flex items-center justify-between text-[11px] font-mono-code text-[#78716c]">
        <span>Total Vault Sparks: {ideas.length}</span>
        <button
          onClick={() => BeaconDatabase.ideaDao.resetToDemoSeed()}
          className="text-[#a8a29e] hover:text-[#faf8f5] underline cursor-pointer"
        >
          Reset Demo Seed
        </button>
      </div>
    </div>
  );
};
