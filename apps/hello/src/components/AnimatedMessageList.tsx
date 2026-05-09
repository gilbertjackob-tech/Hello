/**
 * AnimatedMessageList
 * Smooth message list with staggered entrance animations
 */

import React, { useMemo } from 'react';
import { AnimatePresence, motion } from 'motion/react';
import { animations, shouldReduceMotion } from '../design/animations';
import type { Message } from '../types';

interface AnimatedMessageListProps {
  messages: (Message & { optimistic?: boolean; status?: string })[];
  renderMessage: (message: Message & { optimistic?: boolean; status?: string }, index: number) => React.ReactNode;
  isLoading?: boolean;
}

export const AnimatedMessageList = React.memo(
  ({ messages, renderMessage, isLoading }: AnimatedMessageListProps) => {
    const reducedMotion = shouldReduceMotion();

    // Group messages by sender to avoid excessive animations
    const groupedMessages = useMemo(() => {
      if (messages.length === 0) return [];
      
      const groups: (Message & { optimistic?: boolean; status?: string })[][] = [];
      let currentGroup: (Message & { optimistic?: boolean; status?: string })[] = [];
      let lastSenderId = '';

      for (const msg of messages) {
        if (msg.senderId === lastSenderId && currentGroup.length > 0) {
          currentGroup.push(msg);
        } else {
          if (currentGroup.length > 0) groups.push([...currentGroup]);
          currentGroup = [msg];
          lastSenderId = msg.senderId;
        }
      }
      if (currentGroup.length > 0) groups.push([...currentGroup]);

      return groups;
    }, [messages]);

    if (isLoading) {
      return (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.2 }}
          className="space-y-4"
        >
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={`skeleton-${i}`} className="animate-pulse">
              <div className="bg-gray-300 dark:bg-gray-700 h-12 rounded-lg" />
            </div>
          ))}
        </motion.div>
      );
    }

    if (reducedMotion) {
      return (
        <>
          {messages.map((msg, idx) => (
            <React.Fragment key={msg.id}>{renderMessage(msg, idx)}</React.Fragment>
          ))}
        </>
      );
    }

    return (
      <AnimatePresence mode="popLayout" initial={false}>
        {messages.length > 0 ? (
          messages.map((msg, index) => (
            <React.Fragment key={msg.id || `temp-${index}`}>
              {renderMessage(msg, index)}
            </React.Fragment>
          ))
        ) : (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="text-center text-gray-500 py-8"
          >
            No messages yet
          </motion.div>
        )}
      </AnimatePresence>
    );
  }
);

AnimatedMessageList.displayName = 'AnimatedMessageList';
