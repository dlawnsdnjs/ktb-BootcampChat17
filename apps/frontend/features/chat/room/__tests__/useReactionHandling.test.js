import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Toast } from '@/components/Toast';
import socketClient from '@/lib/socket/socketClient';
import { useReactionHandling } from '../useReactionHandling';

vi.mock('@/lib/socket/socketClient', () => ({
  default: {
    canSend: vi.fn(() => true),
    sendMessageReaction: vi.fn(),
  },
}));

vi.mock('@/components/Toast', () => ({
  Toast: { error: vi.fn() },
}));

const currentUser = { id: 'user-1' };
const messages = [{ _id: 'message-1', reactions: { '👍': ['user-1'] } }];

describe('useReactionHandling', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    socketClient.canSend.mockReturnValue(true);
  });

  it('delegates reaction add to socketClient', async () => {
    const setMessages = vi.fn();
    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, messages, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionAdd('message-1', '👍');
    });

    expect(socketClient.sendMessageReaction).toHaveBeenCalledWith(
      'message-1',
      '👍',
      'add',
    );
  });

  it('delegates reaction remove to socketClient', async () => {
    const setMessages = vi.fn();
    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, messages, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionRemove('message-1', '👍');
    });

    expect(socketClient.sendMessageReaction).toHaveBeenCalledWith(
      'message-1',
      '👍',
      'remove',
    );
  });

  it('does not send reaction add when the socket client cannot send', async () => {
    const setMessages = vi.fn();
    socketClient.canSend.mockReturnValue(false);
    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, messages, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionAdd('message-1', '👍');
    });

    expect(socketClient.sendMessageReaction).not.toHaveBeenCalled();
    expect(Toast.error).toHaveBeenCalledWith('리액션 추가에 실패했습니다.');
  });

  it('does not send reaction remove when the socket client cannot send', async () => {
    const setMessages = vi.fn();
    socketClient.canSend.mockReturnValue(false);
    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, messages, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionRemove('message-1', '👍');
    });

    expect(socketClient.sendMessageReaction).not.toHaveBeenCalled();
    expect(Toast.error).toHaveBeenCalledWith('리액션 제거에 실패했습니다.');
  });

  it('keeps handler identity stable when messages change', () => {
    const setMessages = vi.fn();
    const { result, rerender } = renderHook(
      ({ msgs }) => useReactionHandling({ currentUser, messages: msgs, setMessages }),
      { initialProps: { msgs: messages } }
    );

    const firstAdd = result.current.handleReactionAdd;
    const firstRemove = result.current.handleReactionRemove;

    rerender({ msgs: [...messages, { _id: 'message-2', reactions: {} }] });

    expect(result.current.handleReactionAdd).toBe(firstAdd);
    expect(result.current.handleReactionRemove).toBe(firstRemove);
  });

  it('keeps handler identity stable when currentUser object identity changes', () => {
    const setMessages = vi.fn();
    const { result, rerender } = renderHook(
      ({ user }) => useReactionHandling({ currentUser: user, setMessages }),
      { initialProps: { user: { id: 'user-1' } } }
    );

    const firstAdd = result.current.handleReactionAdd;

    rerender({ user: { id: 'user-1' } });

    expect(result.current.handleReactionAdd).toBe(firstAdd);
  });

  it('rolls back to the reactions captured before the optimistic update', async () => {
    const stored = [{ _id: 'message-1', reactions: { '👍': ['user-2'] } }];
    const setMessages = vi.fn((updater) => {
      const next = updater(stored);
      stored.splice(0, stored.length, ...next);
    });
    socketClient.sendMessageReaction.mockRejectedValueOnce(new Error('boom'));

    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionAdd('message-1', '👍');
    });

    expect(stored[0].reactions).toEqual({ '👍': ['user-2'] });
  });

  it('does not wipe reactions when the optimistic update never ran', async () => {
    const stored = [{ _id: 'message-1', reactions: { '👍': ['user-2'] } }];
    const setMessages = vi.fn((updater) => {
      const next = updater(stored);
      stored.splice(0, stored.length, ...next);
    });
    socketClient.canSend.mockReturnValue(false);

    const { result } = renderHook(() =>
      useReactionHandling({ currentUser, setMessages })
    );

    await act(async () => {
      await result.current.handleReactionAdd('message-1', '👍');
    });

    expect(setMessages).not.toHaveBeenCalled();
    expect(stored[0].reactions).toEqual({ '👍': ['user-2'] });
  });
});
