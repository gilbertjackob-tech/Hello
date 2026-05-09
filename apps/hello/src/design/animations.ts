/**
 * Animation utilities for Hello design system
 * Reusable motion presets for web components
 */

import { tokens } from './tokens';

export const animations = {
  // Message bubble entrance
  messageBubble: {
    initial: { opacity: 0, scale: 0.9, y: 8 },
    animate: { opacity: 1, scale: 1, y: 0 },
    exit: { opacity: 0, scale: 0.95, x: -100 },
    transition: {
      type: 'spring',
      stiffness: 300,
      damping: 30,
      mass: 1,
      duration: tokens.animation.duration.base / 1000,
    },
  },

  // Send button morph animation
  sendButtonPress: {
    tap: { scale: 0.92 },
    transition: { duration: tokens.animation.duration.fast / 1000 },
  },

  // Reaction animation
  reaction: {
    initial: { scale: 0, rotate: -20 },
    animate: { scale: 1, rotate: 0 },
    exit: { scale: 0, rotate: 20 },
    transition: { ...tokens.animation.spring.bouncy },
  },

  // Bottom sheet slide in
  bottomSheet: {
    initial: { y: 400, opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: 400, opacity: 0 },
    transition: {
      type: 'spring',
      stiffness: 300,
      damping: 35,
      mass: 1,
    },
  },

  // Fade transition
  fadeIn: {
    initial: { opacity: 0 },
    animate: { opacity: 1 },
    exit: { opacity: 0 },
    transition: { duration: tokens.animation.duration.base / 1000 },
  },

  // Slide from right
  slideInRight: {
    initial: { x: 100, opacity: 0 },
    animate: { x: 0, opacity: 1 },
    exit: { x: 100, opacity: 0 },
    transition: { duration: tokens.animation.duration.slow / 1000 },
  },

  // Slide from left
  slideInLeft: {
    initial: { x: -100, opacity: 0 },
    animate: { x: 0, opacity: 1 },
    exit: { x: -100, opacity: 0 },
    transition: { duration: tokens.animation.duration.slow / 1000 },
  },

  // Scale bounce
  scaleBounce: {
    animate: { scale: [1, 1.05, 1] },
    transition: { duration: 0.4, repeat: Infinity, repeatDelay: 1 },
  },

  // Typing dots animation
  typingDot: (delay: number) => ({
    animate: { y: [0, -4, 0] },
    transition: {
      duration: 0.6,
      repeat: Infinity,
      delay,
    },
  }),

  // New message notification slide
  newMessagePill: {
    initial: { y: 20, opacity: 0 },
    animate: { y: 0, opacity: 1 },
    exit: { y: -20, opacity: 0 },
    transition: { duration: tokens.animation.duration.base / 1000 },
  },

  // Auto scroll to bottom
  autoScroll: {
    transition: {
      type: 'spring',
      stiffness: 300,
      damping: 35,
      mass: 1,
    },
  },

  // Attachment upload progress
  uploadProgress: {
    initial: { scaleX: 0 },
    animate: { scaleX: 1 },
    transition: { duration: 0.3 },
  },

  // Stagger children animation
  staggerContainer: {
    animate: {
      transition: {
        staggerChildren: 0.05,
        delayChildren: 0.1,
      },
    },
  },

  staggerItem: {
    initial: { opacity: 0, y: 10 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: tokens.animation.duration.base / 1000 },
  },

  // Skeleton loader shimmer
  shimmer: {
    animate: { backgroundPosition: ['200% center', '-200% center'] },
    transition: {
      duration: 2,
      repeat: Infinity,
      ease: 'linear',
    },
  },
};

/**
 * Utility to respect prefers-reduced-motion
 */
export function shouldReduceMotion(): boolean {
  if (typeof window === 'undefined') return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/**
 * Get animation with reduced motion fallback
 */
export function getAnimation(
  fullAnimation: any,
  reducedAnimation: any = { transition: { duration: 0 } }
) {
  return shouldReduceMotion() ? reducedAnimation : fullAnimation;
}

/**
 * Common transition values
 */
export const transitions = {
  fast: `${tokens.animation.duration.fast}ms ${tokens.animation.easing.easeOut}`,
  base: `${tokens.animation.duration.base}ms ${tokens.animation.easing.easeOut}`,
  slow: `${tokens.animation.duration.slow}ms ${tokens.animation.easing.easeOut}`,
  slower: `${tokens.animation.duration.slower}ms ${tokens.animation.easing.easeOut}`,
};
