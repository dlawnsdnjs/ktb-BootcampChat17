import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { createReadReceiptBatcher } from '../readReceiptBatcher';

const createClient = ({ canSend = true } = {}) => ({
  canSend: vi.fn(() => canSend),
  markMessagesAsRead: vi.fn(),
});

describe('readReceiptBatcher', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sends queued ids as a single batch after the flush interval', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');
    batcher.queue('m2', 'room-1');
    batcher.queue('m3', 'room-1');

    expect(client.markMessagesAsRead).not.toHaveBeenCalled();

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(1);
    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['m1', 'm2', 'm3']);
  });

  it('deduplicates repeated ids', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');
    batcher.queue('m1', 'room-1');

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['m1']);
  });

  it('keeps rooms in separate batches', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('a1', 'room-1');
    batcher.queue('b1', 'room-2');
    batcher.queue('a2', 'room-1');

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(2);
    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['a1', 'a2']);
    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['b1']);
  });

  it('flushes immediately when the batch reaches maxBatch', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500, maxBatch: 2 });

    batcher.queue('m1', 'room-1');
    batcher.queue('m2', 'room-1');

    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['m1', 'm2']);

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(1);
  });

  it('flushes a single room on demand and leaves other rooms pending', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('a1', 'room-1');
    batcher.queue('b1', 'room-2');

    batcher.flush('room-1');

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(1);
    expect(client.markMessagesAsRead).toHaveBeenCalledWith(['a1']);

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(2);
    expect(client.markMessagesAsRead).toHaveBeenLastCalledWith(['b1']);
  });

  it('does not send when the socket cannot send', () => {
    const client = createClient({ canSend: false });
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');
    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).not.toHaveBeenCalled();
  });

  it('drops queued ids on reset', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');
    batcher.reset();

    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).not.toHaveBeenCalled();
  });

  it('does not throw when the client send fails', () => {
    const client = createClient();
    client.markMessagesAsRead.mockImplementation(() => {
      throw new Error('Socket not connected');
    });
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');

    expect(() => vi.advanceTimersByTime(500)).not.toThrow();

    errorSpy.mockRestore();
  });

  it('restarts the timer for ids queued after a flush', () => {
    const client = createClient();
    const batcher = createReadReceiptBatcher({ client, flushInterval: 500 });

    batcher.queue('m1', 'room-1');
    vi.advanceTimersByTime(500);

    batcher.queue('m2', 'room-1');
    vi.advanceTimersByTime(500);

    expect(client.markMessagesAsRead).toHaveBeenCalledTimes(2);
    expect(client.markMessagesAsRead).toHaveBeenLastCalledWith(['m2']);
  });
});
