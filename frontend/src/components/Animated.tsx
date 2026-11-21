import type { ReactNode } from 'react';
import { useAutoAnimate } from '@formkit/auto-animate/react';

/* Own file on purpose: only list pages use it, so it ships in their (lazy) chunks. */
/**
 * A list whose children slide in / out / to their new place when they change
 * (auto-animate, ~2 kB). Respects prefers-reduced-motion by itself.
 */
export function Animated({ as = 'div', className, children }: { as?: 'div' | 'ul'; className?: string; children: ReactNode }) {
  const [ref] = useAutoAnimate<HTMLElement>({ duration: 220, easing: 'cubic-bezier(0.2, 0.8, 0.2, 1)' });
  const Tag = as;
  return (
    <Tag ref={ref as never} className={className}>
      {children}
    </Tag>
  );
}

