import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ParticipantsProvider, useParticipants } from '../ParticipantsContext';
import ChatMessages from '@/components/ChatMessages';

vi.mock('@/hooks/useInfiniteScroll', () => ({
  useInfiniteScroll: () => ({ sentinelRef: { current: null } }),
}));

vi.mock('@/hooks/useAutoScroll', () => ({
  useAutoScroll: () => ({
    containerRef: { current: null },
    scrollToBottom: vi.fn(),
    isNearBottom: true,
  }),
}));

const userMessageRenders = vi.fn();

vi.mock('@/components/UserMessage', () => {
  const MockUserMessage = ({ msg }) => {
    userMessageRenders(msg._id);
    return React.createElement('div', { 'data-testid': 'message' }, msg.content);
  };
  MockUserMessage.displayName = 'MockUserMessage';

  return { default: React.memo(MockUserMessage) };
});

vi.mock('@/components/SystemMessage', () => ({
  default: ({ msg }) => React.createElement('div', null, msg.content),
}));

vi.mock('@/components/FileMessage', () => ({
  default: ({ msg }) => React.createElement('div', null, msg.content),
}));

const Probe = () => {
  const participants = useParticipants();
  return <span data-testid="count">{participants.length}</span>;
};

const messages = [
  { _id: 'm1', content: 'first', timestamp: '2026-06-20T11:00:00.000Z', sender: { _id: 'other' } },
  { _id: 'm2', content: 'second', timestamp: '2026-06-20T12:00:00.000Z', sender: { _id: 'other' } },
];

describe('ParticipantsContext', () => {
  it('exposes participants to consumers', () => {
    render(
      <ParticipantsProvider participants={[{ _id: 'a' }, { _id: 'b' }]}>
        <Probe />
      </ParticipantsProvider>
    );

    expect(screen.getByTestId('count')).toHaveTextContent('2');
  });

  it('falls back to an empty array when participants is missing', () => {
    render(
      <ParticipantsProvider participants={undefined}>
        <Probe />
      </ParticipantsProvider>
    );

    expect(screen.getByTestId('count')).toHaveTextContent('0');
  });

  it('does not re-render messages when only participants change', () => {
    const currentUser = { id: 'me' };
    const handlers = { onReactionAdd: vi.fn(), onReactionRemove: vi.fn(), onLoadMore: vi.fn() };

    const { rerender } = render(
      <ParticipantsProvider participants={[{ _id: 'a' }]}>
        <ChatMessages messages={messages} currentUser={currentUser} {...handlers} />
      </ParticipantsProvider>
    );

    expect(userMessageRenders).toHaveBeenCalledTimes(2);
    userMessageRenders.mockClear();

    rerender(
      <ParticipantsProvider participants={[{ _id: 'a' }, { _id: 'b' }]}>
        <ChatMessages messages={messages} currentUser={currentUser} {...handlers} />
      </ParticipantsProvider>
    );

    expect(userMessageRenders).not.toHaveBeenCalled();
  });
});
