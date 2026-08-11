import React from 'react';
import { render, act } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { useAutoScroll } from '../useAutoScroll';

const VIEWPORT = 500;
const MESSAGE_HEIGHT = 100;
const ME = 'me';

const Harness = ({ messages, loading }) => {
  const { containerRef } = useAutoScroll(messages, ME, loading, 100);
  return <div ref={containerRef} data-testid="scroll-container" />;
};

const message = (id, sender = 'other') => ({ _id: id, sender: { _id: sender } });

const initialMessages = (count, lastSender = 'other') =>
  Array.from({ length: count }, (_, i) =>
    message(`m${i}`, i === count - 1 ? lastSender : 'other')
  );

const prepend = (messages, count) => [
  ...Array.from({ length: count }, (_, i) => message(`older${i}`)),
  ...messages,
];

const append = (messages, sender = 'other') => [
  ...messages,
  message(`new${messages.length}`, sender),
];

const equipContainer = (container) => {
  let messageCount = 0;
  let scrollTop = 0;

  Object.defineProperty(container, 'scrollHeight', {
    get: () => messageCount * MESSAGE_HEIGHT,
    configurable: true,
  });
  Object.defineProperty(container, 'clientHeight', {
    get: () => VIEWPORT,
    configurable: true,
  });
  Object.defineProperty(container, 'scrollTop', {
    get: () => scrollTop,
    set: (value) => {
      scrollTop = value;
      setTimeout(() => container.dispatchEvent(new Event('scroll')), 0);
    },
    configurable: true,
  });

  container.scrollTo = vi.fn(({ top }) => {
    container.scrollTop = top;
  });
  container.setMessageCount = (count) => {
    messageCount = count;
  };

  return container;
};

describe('useAutoScroll', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  const mountWith = (messages, { settle = true } = {}) => {
    const view = render(<Harness messages={[]} loading={true} />);
    const container = equipContainer(view.getByTestId('scroll-container'));

    container.setMessageCount(messages.length);
    view.rerender(<Harness messages={messages} loading={false} />);

    if (settle) {
      act(() => {
        vi.advanceTimersByTime(500);
      });
    }

    return { view, container };
  };

  const loadOlder = (view, container, messages, count) => {
    view.rerender(<Harness messages={messages} loading={true} />);

    const older = prepend(messages, count);
    container.setMessageCount(older.length);
    view.rerender(<Harness messages={older} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    return older;
  };

  it('scrolls to the bottom after the initial load', () => {
    const { container } = mountWith(initialMessages(30));

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });

  it('still follows a new message while the user sits at the bottom', () => {
    const messages = initialMessages(30);
    const { view, container } = mountWith(messages);

    const grown = append(messages);
    container.setMessageCount(grown.length);
    view.rerender(<Harness messages={grown} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(container.scrollTop).toBe(31 * MESSAGE_HEIGHT);
  });

  it('keeps the reading position when older messages are prepended', () => {
    const messages = initialMessages(30);
    const { view, container } = mountWith(messages);

    container.scrollTop = 0;
    act(() => {
      vi.advanceTimersByTime(10);
    });

    loadOlder(view, container, messages, 30);

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });

  it('keeps the reading position when my own message is the newest one', () => {
    const messages = initialMessages(30, ME);
    const { view, container } = mountWith(messages);

    container.scrollTop = 0;
    act(() => {
      vi.advanceTimersByTime(10);
    });

    loadOlder(view, container, messages, 30);

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });

  it('keeps the reading position when the user scrolls up during the auto-scroll window', () => {
    const messages = initialMessages(30);
    const { view, container } = mountWith(messages, { settle: false });

    act(() => {
      vi.advanceTimersByTime(100);
    });
    container.scrollTop = 0;
    act(() => {
      vi.advanceTimersByTime(400);
    });

    loadOlder(view, container, messages, 30);

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });
});
