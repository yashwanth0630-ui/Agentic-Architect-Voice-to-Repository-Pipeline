import React, { useEffect, useRef } from 'react';

export interface RoseOrbitProps {
  size?: number | string;
  className?: string;
  color?: string;
  particleCount?: number;
}

export const RoseOrbit: React.FC<RoseOrbitProps> = ({
  size = 180,
  className = '',
  color = '#ff5722',
  particleCount = 72
}) => {
  const containerRef = useRef<SVGSVGElement | null>(null);

  useEffect(() => {
    const svg = containerRef.current;
    if (!svg) return;

    const group = svg.querySelector<SVGGElement>('#rose-orbit-group');
    const path = svg.querySelector<SVGPathElement>('#rose-orbit-path');
    if (!group || !path) return;

    const config = {
      rotate: true,
      particleCount,
      trailSpan: 0.42,
      durationMs: 5200,
      rotationDurationMs: 28000,
      pulseDurationMs: 4600,
      strokeWidth: 4.8,
      orbitRadius: 7.0,
      detailAmplitude: 2.7,
      petalCount: 7,
      curveScale: 3.9,
      point(progress: number, detailScale: number) {
        const t = progress * Math.PI * 2;
        const k = Math.round(config.petalCount);
        const r = config.orbitRadius - config.detailAmplitude * detailScale * Math.cos(k * t);
        return {
          x: 50 + Math.cos(t) * r * config.curveScale,
          y: 50 + Math.sin(t) * r * config.curveScale,
        };
      },
    };

    path.setAttribute('stroke-width', String(config.strokeWidth));

    // Clear previous particles
    const existingParticles = group.querySelectorAll('.rose-particle');
    existingParticles.forEach(p => p.remove());

    const particles: SVGCircleElement[] = [];
    for (let i = 0; i < config.particleCount; i++) {
      const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
      circle.setAttribute('class', 'rose-particle');
      circle.setAttribute('fill', color);
      group.appendChild(circle);
      particles.push(circle);
    }

    function normalizeProgress(progress: number): number {
      return ((progress % 1) + 1) % 1;
    }

    function getDetailScale(time: number): number {
      const pulseProgress = (time % config.pulseDurationMs) / config.pulseDurationMs;
      const pulseAngle = pulseProgress * Math.PI * 2;
      return 0.52 + ((Math.sin(pulseAngle + 0.55) + 1) / 2) * 0.48;
    }

    function getRotation(time: number): number {
      if (!config.rotate) return 0;
      return -((time % config.rotationDurationMs) / config.rotationDurationMs) * 360;
    }

    function buildPath(detailScale: number, steps = 360): string {
      return Array.from({ length: steps + 1 }, (_, index) => {
        const pt = config.point(index / steps, detailScale);
        return `${index === 0 ? 'M' : 'L'} ${pt.x.toFixed(2)} ${pt.y.toFixed(2)}`;
      }).join(' ');
    }

    function getParticle(index: number, progress: number, detailScale: number) {
      const tailOffset = index / (config.particleCount - 1);
      const pt = config.point(normalizeProgress(progress - tailOffset * config.trailSpan), detailScale);
      const fade = Math.pow(1 - tailOffset, 0.56);
      return {
        x: pt.x,
        y: pt.y,
        radius: 0.8 + fade * 2.6,
        opacity: 0.04 + fade * 0.96,
      };
    }

    let animationFrameId: number;
    const startedAt = performance.now();

    function render(now: number) {
      const time = now - startedAt;
      const progress = (time % config.durationMs) / config.durationMs;
      const detailScale = getDetailScale(time);

      group?.setAttribute('transform', `rotate(${getRotation(time)} 50 50)`);
      path?.setAttribute('d', buildPath(detailScale));

      particles.forEach((node, index) => {
        const particle = getParticle(index, progress, detailScale);
        node.setAttribute('cx', particle.x.toFixed(2));
        node.setAttribute('cy', particle.y.toFixed(2));
        node.setAttribute('r', particle.radius.toFixed(2));
        node.setAttribute('opacity', particle.opacity.toFixed(3));
      });

      animationFrameId = requestAnimationFrame(render);
    }

    animationFrameId = requestAnimationFrame(render);

    return () => {
      cancelAnimationFrame(animationFrameId);
    };
  }, [color, particleCount]);

  return (
    <div
      className={`relative inline-flex items-center justify-center ${className}`}
      style={{ width: size, height: size }}
    >
      <svg
        ref={containerRef}
        viewBox="0 0 100 100"
        fill="none"
        aria-hidden="true"
        className="w-full h-full overflow-visible"
      >
        <g id="rose-orbit-group">
          <path
            id="rose-orbit-path"
            stroke={color}
            strokeLinecap="round"
            strokeLinejoin="round"
            opacity="0.12"
          />
        </g>
      </svg>
    </div>
  );
};

export default RoseOrbit;
