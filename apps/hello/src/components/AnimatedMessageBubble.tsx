/**
 * AnimatedMessageBubble
 * Message bubble with smooth entrance and exit animations
 */

import React from 'react';
import { motion } from 'motion/react';
import { animations, shouldReduceMotion } from '../design/animations';
import type { Message } from '../types';

interface AnimatedMessageBubbleProps {
  message: Message & { optimistic?: boolean; status?: string };
  children: React.ReactNode;
  isOwn?: boolean;
  index?: number;
  onDelete?: () => void;
}

export const AnimatedMessageBubble = React.memo(
  ({ message, children, isOwn, index = 0, onDelete }: AnimatedMessageBubbleProps) => {
    const reducedMotion = shouldReduceMotion();

    // Custom animation for optimistic messages
    const optimisticAnimation = {
      initial: { opacity: 0.7, scale: 0.95 },
      animate: { opacity: 1, scale: 1 },
      transition: { duration: 0.15 },
    };

    // Failed message animation
    const failedAnimation = {
      initial: { opacity: 0 },
      animate: { opacity: 0.6 },
      transition: { duration: 0.2 },
    };

    const getAnimation = () => {
      if (message.optimistic) return optimisticAnimation;
      if (message.status === 'failed') return failedAnimation;
      return animations.messageBubble;
    };

    const motionAnimation = getAnimation();

    if (reducedMotion) {
      return <>{children}</>;
    }

    return (
      <motion.div
        key={message.id}
        initial={motionAnimation.initial}
        animate={motionAnimation.animate}
        exit={motionAnimation.exit}
        transition={motionAnimation.transition}
        layout
        className="w-full"
      >
        {children}
      </motion.div>
    );
  }
);

AnimatedMessageBubble.displayName = 'AnimatedMessageBubble';
