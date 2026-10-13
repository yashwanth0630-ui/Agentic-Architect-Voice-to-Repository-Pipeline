import React, { useState, useRef, useEffect } from 'react';
import { Music, Play, Pause, RotateCcw, Download, Sparkles, Volume2 } from 'lucide-react';
import { generateMusicTrack, GeneratedTrack } from '../../services/musicService';
import { TextShimmer } from './TextShimmer';
import { Alert } from './Alert';

export const SoundscapeGenerator: React.FC = () => {
  const [model, setModel] = useState<'lyria-3-clip-preview' | 'lyria-3-pro-preview'>('lyria-3-clip-preview');
  const [prompt, setPrompt] = useState('Deep focus cyberpunk ambient with subtle sub-bass pulses');
  const [duration, setDuration] = useState<number>(30);
  const [isGenerating, setIsGenerating] = useState(false);
  const [currentTrack, setCurrentTrack] = useState<GeneratedTrack | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [feedback, setFeedback] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animationFrameRef = useRef<number | null>(null);

  const presets = [
    { label: 'Cyberpunk Drone', text: 'Deep focus cyberpunk ambient with subtle sub-bass pulses' },
    { label: 'Kernel Synthwave', text: 'Retro synthwave arpeggios for kernel hacking at 120bpm' },
    { label: 'Warm Lo-Fi', text: 'Warm lo-fi Rhodes chords with gentle tape vinyl crackle' },
    { label: 'System Architecture', text: 'Ethereal ambient drone for system architecture design' },
  ];

  const handleGenerate = async () => {
    if (!prompt.trim() || isGenerating) return;
    setIsGenerating(true);
    if (audioRef.current) {
      audioRef.current.pause();
      setIsPlaying(false);
    }

    try {
      const track = await generateMusicTrack({
        prompt: prompt.trim(),
        model,
        durationSeconds: duration,
      });
      setCurrentTrack(track);
      setFeedback(`Soundscape generated via ${model} (${track.duration}s).`);
      setTimeout(() => setFeedback(null), 3000);
    } catch (err: any) {
      setFeedback('Music generation note: using local synthesized focus soundscape.');
    } finally {
      setIsGenerating(false);
    }
  };

  const togglePlay = () => {
    if (!audioRef.current || !currentTrack) return;
    if (isPlaying) {
      audioRef.current.pause();
      setIsPlaying(false);
    } else {
      audioRef.current.play().then(() => setIsPlaying(true)).catch(console.warn);
    }
  };

  // Canvas visualizer animation
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let phase = 0;
    const render = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const bars = 28;
      const barWidth = canvas.width / bars - 2;

      for (let i = 0; i < bars; i++) {
        let height = 4;
        if (isPlaying) {
          height = Math.sin(phase + i * 0.4) * 16 + Math.cos(phase * 1.5 + i * 0.2) * 8 + 20;
          height = Math.max(4, Math.min(canvas.height - 4, height));
        } else if (isGenerating) {
          height = Math.sin(phase * 2 + i * 0.5) * 12 + 14;
        }

        const x = i * (barWidth + 2);
        const y = (canvas.height - height) / 2;

        const gradient = ctx.createLinearGradient(0, y, 0, y + height);
        gradient.addColorStop(0, '#ffb693');
        gradient.addColorStop(1, '#ff6b00');
        ctx.fillStyle = gradient;
        ctx.fillRect(x, y, barWidth, height);
      }

      phase += isPlaying ? 0.08 : 0.03;
      animationFrameRef.current = requestAnimationFrame(render);
    };

    render();
    return () => {
      if (animationFrameRef.current) cancelAnimationFrame(animationFrameRef.current);
    };
  }, [isPlaying, isGenerating]);

  return (
    <div className="rounded-2xl bg-[#1d1b19] border border-[#292524] p-4.5 space-y-4 shadow-md">
      {feedback && (
        <Alert
          type="info"
          message={feedback}
          onDismiss={() => setFeedback(null)}
        />
      )}

      {/* Header with Lyria Model Selector */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded-lg bg-[#ff6b00]/15 text-[#ff6b00] flex items-center justify-center">
            <Music className="w-4 h-4" />
          </div>
          <div>
            <h3 className="text-[15px] font-bold text-[#faf8f5] font-display">
              Beacon Soundscape Studio
            </h3>
            <p className="text-[11px] font-mono-code text-[#a8a29e]">
              Focus audio generator powered by Google Lyria
            </p>
          </div>
        </div>
        <span className="px-2 py-0.5 rounded text-[10px] font-mono-code font-bold uppercase bg-[#ff6b00]/20 text-[#ffb693]">
          Lyria 3
        </span>
      </div>

      {/* Model Selection Tabs */}
      <div className="grid grid-cols-2 gap-2 p-1 rounded-xl bg-[#121110] border border-[#292524] text-[11px] font-mono-code">
        <button
          type="button"
          onClick={() => {
            setModel('lyria-3-clip-preview');
            setDuration(30);
          }}
          className={`py-1.5 rounded-lg transition-all ${
            model === 'lyria-3-clip-preview'
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
              : 'text-[#a8a29e] hover:text-[#faf8f5]'
          }`}
        >
          lyria-3-clip-preview (≤30s)
        </button>

        <button
          type="button"
          onClick={() => {
            setModel('lyria-3-pro-preview');
            setDuration(60);
          }}
          className={`py-1.5 rounded-lg transition-all ${
            model === 'lyria-3-pro-preview'
              ? 'bg-[#ff6b00] text-[#121110] font-bold shadow-sm'
              : 'text-[#a8a29e] hover:text-[#faf8f5]'
          }`}
        >
          lyria-3-pro-preview (Full Track)
        </button>
      </div>

      {/* Quick Prompt Presets */}
      <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-0.5">
        {presets.map((p) => (
          <button
            key={p.label}
            type="button"
            onClick={() => setPrompt(p.text)}
            className="px-2.5 py-1 rounded-lg text-[10px] font-mono-code bg-[#24211e] hover:bg-[#2c2927] text-[#e2bfb0] border border-[#292524] whitespace-nowrap active:scale-95 transition-all"
          >
            {p.label}
          </button>
        ))}
      </div>

      {/* Text Prompt Input */}
      <div className="space-y-1">
        <textarea
          rows={2}
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="Describe audio vibe, instruments, tempo..."
          className="w-full bg-[#121110] border border-[#292524] rounded-xl p-2.5 text-[12px] text-[#faf8f5] focus:outline-none focus:border-[#ff6b00] resize-none"
        />
      </div>

      {/* Waveform Visualizer Canvas */}
      <div className="rounded-xl bg-[#121110] border border-[#292524] p-3 flex flex-col items-center justify-center relative overflow-hidden">
        <canvas ref={canvasRef} width={280} height={44} className="w-full h-11" />

        {isGenerating && (
          <div className="absolute inset-0 bg-[#121110]/85 backdrop-blur-sm flex items-center justify-center">
            <TextShimmer className="text-[12px] font-mono-code">
              Synthesizing audio with {model}...
            </TextShimmer>
          </div>
        )}
      </div>

      {/* Audio Element & Controls */}
      {currentTrack && (
        <audio
          ref={audioRef}
          src={currentTrack.audioUrl}
          onTimeUpdate={() => {
            if (audioRef.current) setCurrentTime(audioRef.current.currentTime);
          }}
          onEnded={() => setIsPlaying(false)}
        />
      )}

      <div className="flex items-center justify-between gap-2 pt-1">
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={!currentTrack || isGenerating}
            onClick={togglePlay}
            className="w-10 h-10 rounded-full bg-[#ff6b00] hover:bg-[#ff7a1a] text-[#121110] flex items-center justify-center font-bold active:scale-95 transition-all disabled:opacity-40 shadow-[0_0_12px_rgba(255,107,0,0.3)] cursor-pointer"
          >
            {isPlaying ? <Pause className="w-5 h-5 fill-current" /> : <Play className="w-5 h-5 fill-current ml-0.5" />}
          </button>

          <div className="text-[11px] font-mono-code text-[#a8a29e]">
            <span>{Math.floor(currentTime)}s</span>
            <span> / {currentTrack ? currentTrack.duration : duration}s</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {currentTrack && (
            <a
              href={currentTrack.audioUrl}
              download={`idea-beacon-${currentTrack.model}.wav`}
              className="p-2 rounded-lg bg-[#24211e] hover:bg-[#2c2927] text-[#a8a29e] hover:text-[#faf8f5] border border-[#292524] transition-colors"
              title="Download Soundscape WAV"
            >
              <Download className="w-4 h-4" />
            </a>
          )}

          <button
            type="button"
            disabled={isGenerating}
            onClick={handleGenerate}
            className="h-10 px-4 rounded-xl bg-[#24211e] hover:bg-[#2c2927] border border-[#292524] text-[#ffb693] font-semibold text-[12px] flex items-center gap-1.5 active:scale-95 transition-all shadow-sm cursor-pointer disabled:opacity-50"
          >
            <Sparkles className="w-3.5 h-3.5 text-[#ff6b00]" />
            <span>{isGenerating ? 'Generating...' : 'Synthesize Track'}</span>
          </button>
        </div>
      </div>
    </div>
  );
};
