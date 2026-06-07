/**
 * useOptimisticMessage
 * Hook for optimistic message sending with temporary IDs
 * Provides instant UI feedback while the real message is being sent
 */

import { useState, useCallback, useRef } from 'react';
import { Message } from '../types';

type OptimisticMessage = Omit<Message, 'status'> & {
  optimistic?: boolean;
  tempId?: string;
  status?: Message['status'] | 'sending' | 'failed';
};

interface UseOptimisticMessageReturn {
  messages: OptimisticMessage[];
  setMessages: React.Dispatch<React.SetStateAction<OptimisticMessage[]>>;
  addOptimisticMessage: (message: Partial<Message>, chatId: string) => string;
  confirmMessage: (tempId: string, realMessage: Message) => void;
  failMessage: (tempId: string) => void;
  removeMessage: (tempId: string) => void;
}

export function useOptimisticMessage(
  initialMessages: OptimisticMessage[] = []
): UseOptimisticMessageReturn {
  const [messages, setMessages] = useState<OptimisticMessage[]>(initialMessages);
  const tempIdRef = useRef(0);

  const generateTempId = useCallback(() => {
    return `temp_${Date.now()}_${tempIdRef.current++}`;
  }, []);

  const addOptimisticMessage = useCallback(
    (message: Partial<Message>, chatId: string): string => {
      const tempId = generateTempId();
      const optimisticMsg: OptimisticMessage = {
        id: tempId,
        chatId,
        senderId: message.senderId || '',
        senderName: message.senderName || 'You',
        senderAvatar: message.senderAvatar,
        text: message.text || '',
        timestamp: Date.now(),
        attachmentUrl: message.attachmentUrl,
        attachmentType: message.attachmentType,
        attachmentName: message.attachmentName,
        attachmentSize: message.attachmentSize,
        status: 'sending',
        optimistic: true,
        tempId,
        isDeleted: false,
        reactions: [],
        starredBy: [],
        deletedFor: [],
        pinnedUntil: undefined,
        replyTo: message.replyTo,
      };

      setMessages((prev) => [...prev, optimisticMsg]);
      return tempId;
    },
    [generateTempId]
  );

  const confirmMessage = useCallback((tempId: string, realMessage: Message) => {
    setMessages((prev) =>
      prev.map((msg) =>
        msg.tempId === tempId
          ? {
              ...realMessage,
              optimistic: false,
              status: realMessage.status || 'sent',
              tempId,
            }
          : msg
      )
    );
  }, []);

  const failMessage = useCallback((tempId: string) => {
    setMessages((prev) =>
      prev.map((msg) =>
        msg.tempId === tempId
          ? {
              ...msg,
              status: 'failed',
            }
          : msg
      )
    );
  }, []);

  const removeMessage = useCallback((tempId: string) => {
    setMessages((prev) => prev.filter((msg) => msg.tempId !== tempId));
  }, []);

  return {
    messages,
    setMessages,
    addOptimisticMessage,
    confirmMessage,
    failMessage,
    removeMessage,
  };
}
