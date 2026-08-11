import React from 'react';
import { render, act } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { useAutoScroll } from '../useAutoScroll';

const VIEWPORT = 500;
const MESSAGE_HEIGHT = 100;

const Harness = ({ messages, loading }) => {
  const { containerRef } = useAutoScroll(messages, 'me', loading, 100);
  return <div ref={containerRef} data-testid="scroll-container" />;
};

const makeMessages = (count) =>
  Array.from({ length: count }, (_, i) => ({
    _id: `m${i}`,
    sender: { _id: 'other' },
  }));

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

  const mountWithMessages = (count) => {
    const view = render(<Harness messages={[]} loading={true} />);
    const container = equipContainer(view.getByTestId('scroll-container'));

    container.setMessageCount(count);
    view.rerender(<Harness messages={makeMessages(count)} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    return { view, container };
  };

  it('scrolls to the bottom after the initial load', () => {
    const { container } = mountWithMessages(30);

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });

  it('still follows new messages while the user sits at the bottom', () => {
    const { view, container } = mountWithMessages(30);

    container.setMessageCount(31);
    view.rerender(<Harness messages={makeMessages(31)} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(container.scrollTop).toBe(31 * MESSAGE_HEIGHT);
  });

  it('keeps the reading position when the user scrolls up during the auto-scroll window', () => {
    const view = render(<Harness messages={[]} loading={true} />);
    const container = equipContainer(view.getByTestId('scroll-container'));

    container.setMessageCount(30);
    view.rerender(<Harness messages={makeMessages(30)} loading={false} />);

    act(() => {
      vi.advanceTimersByTime(100);
    });
    container.scrollTop = 0;
    act(() => {
      vi.advanceTimersByTime(400);
    });

    view.rerender(<Harness messages={makeMessages(30)} loading={true} />);

    container.setMessageCount(60);
    view.rerender(<Harness messages={makeMessages(60)} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });

  it('keeps the reading position when older messages are prepended', () => {
    const { view, container } = mountWithMessages(30);

    container.scrollTop = 0;

    view.rerender(<Harness messages={makeMessages(30)} loading={true} />);

    container.setMessageCount(60);
    view.rerender(<Harness messages={makeMessages(60)} loading={false} />);
    act(() => {
      vi.advanceTimersByTime(500);
    });

    expect(container.scrollTop).toBe(30 * MESSAGE_HEIGHT);
  });
});
