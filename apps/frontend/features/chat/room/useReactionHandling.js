import { useCallback } from 'react';
import { Toast } from '@/components/Toast';
import socketClient from '@/lib/socket/socketClient';

export const useReactionHandling = ({ currentUser, setMessages }) => {
  const currentUserId = currentUser?.id;

  const rollbackReactions = useCallback((messageId, previousReactions) => {
    if (previousReactions === null) {
      return;
    }

    setMessages(prevMessages =>
      prevMessages.map(msg =>
        msg._id === messageId ? { ...msg, reactions: previousReactions } : msg
      )
    );
  }, [setMessages]);

  const handleReactionAdd = useCallback(async (messageId, reaction) => {
    let previousReactions = null;

    try {
      if (!socketClient.canSend()) {
        throw new Error('Socket not connected');
      }

      // 낙관적 업데이트
      setMessages(prevMessages =>
        prevMessages.map(msg => {
          if (msg._id !== messageId) {
            return msg;
          }

          const currentReactions = msg.reactions || {};
          previousReactions = currentReactions;
          const currentUsers = currentReactions[reaction] || [];

          // 중복 추가 방지
          if (currentUsers.includes(currentUserId)) {
            return msg;
          }

          return {
            ...msg,
            reactions: {
              ...currentReactions,
              [reaction]: [...currentUsers, currentUserId]
            }
          };
        })
      );

      await socketClient.sendMessageReaction(messageId, reaction, 'add');

    } catch (error) {
      console.error('Add reaction error:', error);
      Toast.error('리액션 추가에 실패했습니다.');

      // 실패 시 롤백
      rollbackReactions(messageId, previousReactions);
    }
  }, [currentUserId, setMessages, rollbackReactions]);

  const handleReactionRemove = useCallback(async (messageId, reaction) => {
    let previousReactions = null;

    try {
      if (!socketClient.canSend()) {
        throw new Error('Socket not connected');
      }

      // 낙관적 업데이트
      setMessages(prevMessages =>
        prevMessages.map(msg => {
          if (msg._id !== messageId) {
            return msg;
          }

          const currentReactions = msg.reactions || {};
          previousReactions = currentReactions;
          const currentUsers = currentReactions[reaction] || [];

          return {
            ...msg,
            reactions: {
              ...currentReactions,
              [reaction]: currentUsers.filter(id => id !== currentUserId)
            }
          };
        })
      );

      await socketClient.sendMessageReaction(messageId, reaction, 'remove');

    } catch (error) {
      console.error('Remove reaction error:', error);
      Toast.error('리액션 제거에 실패했습니다.');

      // 실패 시 롤백
      rollbackReactions(messageId, previousReactions);
    }
  }, [currentUserId, setMessages, rollbackReactions]);

  const handleReactionUpdate = useCallback(({ messageId, reactions }) => {
    setMessages(prevMessages =>
      prevMessages.map(msg =>
        msg._id === messageId ? { ...msg, reactions } : msg
      )
    );
  }, [setMessages]);

  return {
    handleReactionAdd,
    handleReactionRemove,
    handleReactionUpdate
  };
};

export default useReactionHandling;
