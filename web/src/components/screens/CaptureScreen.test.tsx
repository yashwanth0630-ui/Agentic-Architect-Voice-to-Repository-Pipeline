import React, { act } from 'react';
import { createRoot, Root } from 'react-dom/client';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { CaptureScreen, generateTitleFromContent } from './CaptureScreen';
import { IdeaEntity } from '../../db/schemas';

(globalThis as any).IS_REACT_ACT_ENVIRONMENT = true;

function setInputValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const prototype = element instanceof HTMLInputElement ? HTMLInputElement.prototype : HTMLTextAreaElement.prototype;
  const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
  descriptor?.set?.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('CaptureScreen — Idea Beacon Save Flow & Validation Regressions', () => {
  let container: HTMLDivElement;
  let root: Root;

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    vi.restoreAllMocks();
    sessionStorage.clear();
  });

  afterEach(async () => {
    await act(async () => {
      root.unmount();
    });
    container.remove();
  });

  describe('generateTitleFromContent helper', () => {
    it('generates readable title from markdown content, stripping headings and code blocks', () => {
      const markdown = `### Architecture Hypothesis
Inject eBPF tracepoints directly at tcp_v4_connect to measure latency.`;
      const title = generateTitleFromContent(markdown);
      expect(title).toContain('Architecture Hypothesis');
      expect(title).not.toContain('###');
    });

    it('truncates titles exceeding maximum length with an ellipsis at clean word boundary', () => {
      const longContent = 'A very detailed architectural investigation of eBPF kernel sockets and ring buffer throughput latency';
      const title = generateTitleFromContent(longContent, 30);
      expect(title.length).toBeLessThanOrEqual(35);
      expect(title.endsWith('...')).toBe(true);
    });

    it('returns empty string for empty or whitespace-only content', () => {
      expect(generateTitleFromContent('')).toBe('');
      expect(generateTitleFromContent('   \n\t  ')).toBe('');
    });

    it('extracts identifier from raw code if content contains only a code block', () => {
      const codeOnly = '```rust\npub fn probe_tcp_connect() {}\n```';
      const title = generateTitleFromContent(codeOnly);
      expect(title).toContain('pub fn probe_tcp_connect');
    });
  });

  describe('CaptureScreen Component Regression Tests', () => {
    it('regression: saves correctly when both title and content are provided', async () => {
      const onSave = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(
          <CaptureScreen
            onSave={onSave}
            onCancel={onCancel}
            saveDelayMs={0}
          />
        );
      });

      const titleInput = container.querySelector('input') as HTMLInputElement;
      const contentInput = container.querySelector('textarea') as HTMLTextAreaElement;
      const saveButton = Array.from(container.querySelectorAll('button')).find(
        (b) => b.getAttribute('aria-label') === 'Save idea'
      ) as HTMLButtonElement;

      // Provide explicit title and content
      await act(async () => {
        setInputValue(titleInput, 'Custom Latency Probe');
        setInputValue(contentInput, 'Custom implementation directives for eBPF probe.');
      });

      // Click save
      await act(async () => {
        saveButton.click();
      });

      expect(onSave).toHaveBeenCalledTimes(1);
      const savedIdea: Partial<IdeaEntity> = onSave.mock.calls[0][0];
      expect(savedIdea.title).toBe('Custom Latency Probe');
      expect(savedIdea.content).toBe('Custom implementation directives for eBPF probe.');
      expect(savedIdea.status).toBe('unsynced');
    });

    it('regression: saves content with no title by automatically generating a title', async () => {
      const onSave = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(
          <CaptureScreen
            onSave={onSave}
            onCancel={onCancel}
            saveDelayMs={0}
          />
        );
      });

      const titleInput = container.querySelector('input') as HTMLInputElement;
      const contentInput = container.querySelector('textarea') as HTMLTextAreaElement;
      const saveButton = Array.from(container.querySelectorAll('button')).find(
        (b) => b.getAttribute('aria-label') === 'Save idea'
      ) as HTMLButtonElement;

      // Leave title blank (""), provide content
      await act(async () => {
        setInputValue(titleInput, '');
        setInputValue(contentInput, 'Automated title generation from user content specifications');
      });

      await act(async () => {
        saveButton.click();
      });

      expect(onSave).toHaveBeenCalledTimes(1);
      const savedIdea = onSave.mock.calls[0][0];
      expect(savedIdea.title).toBeTruthy();
      expect(savedIdea.title).toContain('Automated title generation');
      expect(savedIdea.content).toBe('Automated title generation from user content specifications');
    });

    it('regression: trims whitespace-only titles and auto-generates title from content', async () => {
      const onSave = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(
          <CaptureScreen
            onSave={onSave}
            onCancel={onCancel}
            saveDelayMs={0}
          />
        );
      });

      const titleInput = container.querySelector('input') as HTMLInputElement;
      const contentInput = container.querySelector('textarea') as HTMLTextAreaElement;
      const saveButton = Array.from(container.querySelectorAll('button')).find(
        (b) => b.getAttribute('aria-label') === 'Save idea'
      ) as HTMLButtonElement;

      // Whitespace-only title
      await act(async () => {
        setInputValue(titleInput, '   \t  \n  ');
        setInputValue(contentInput, 'Resilient whitespace-handling content directive');
      });

      await act(async () => {
        saveButton.click();
      });

      expect(onSave).toHaveBeenCalledTimes(1);
      const savedIdea = onSave.mock.calls[0][0];
      expect(savedIdea.title).toBe('Resilient whitespace-handling content...');
    });

    it('regression: displays validation warning and does not save when both title and content are empty', async () => {
      const onSave = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(
          <CaptureScreen
            onSave={onSave}
            onCancel={onCancel}
            saveDelayMs={0}
          />
        );
      });

      const titleInput = container.querySelector('input') as HTMLInputElement;
      const contentInput = container.querySelector('textarea') as HTMLTextAreaElement;
      const saveButton = Array.from(container.querySelectorAll('button')).find(
        (b) => b.getAttribute('aria-label') === 'Save idea'
      ) as HTMLButtonElement;

      // Both empty
      await act(async () => {
        setInputValue(titleInput, '   ');
        setInputValue(contentInput, '   ');
      });

      await act(async () => {
        saveButton.click();
      });

      // MUST NOT call onSave
      expect(onSave).not.toHaveBeenCalled();

      // MUST display validation warning
      expect(container.textContent).toContain('Validation Warning');
      expect(container.textContent).toContain('Please provide an idea title or description');
    });

    it('regression: preserves existing idea ID when editing without creating duplicate', async () => {
      const existingIdea: IdeaEntity = {
        id: 'idea-existing-999',
        title: 'Original Existing Spark',
        category: 'systems',
        preview: 'Original preview',
        content: 'Original content description',
        tags: ['#systems', '#original'],
        status: 'synced',
        createdAt: '2026-10-14T01:00:00.000Z',
        updatedAt: '2026-10-14T01:00:00.000Z',
        relativeTime: '1 hour ago',
        isDemo: false,
      };

      const onSave = vi.fn();
      const onCancel = vi.fn();

      await act(async () => {
        root.render(
          <CaptureScreen
            initialIdea={existingIdea}
            onSave={onSave}
            onCancel={onCancel}
            saveDelayMs={0}
          />
        );
      });

      const titleInput = container.querySelector('input') as HTMLInputElement;
      expect(titleInput.value).toBe('Original Existing Spark');

      // Update title
      await act(async () => {
        setInputValue(titleInput, 'Edited Existing Spark');
      });

      const saveButton = Array.from(container.querySelectorAll('button')).find(
        (b) => b.getAttribute('aria-label') === 'Save idea'
      ) as HTMLButtonElement;

      await act(async () => {
        saveButton.click();
      });

      expect(onSave).toHaveBeenCalledTimes(1);
      const saved = onSave.mock.calls[0][0];
      // Stable ID preserved
      expect(saved.id).toBe('idea-existing-999');
      expect(saved.title).toBe('Edited Existing Spark');
    });
  });
});
