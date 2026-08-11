import socketClient from './socketClient';

export const READ_RECEIPT_FLUSH_INTERVAL = 500;
export const READ_RECEIPT_MAX_BATCH = 100;

const DEFAULT_ROOM_KEY = '';

export const createReadReceiptBatcher = ({
  client = socketClient,
  flushInterval = READ_RECEIPT_FLUSH_INTERVAL,
  maxBatch = READ_RECEIPT_MAX_BATCH,
} = {}) => {
  const pending = new Map();
  let timerId = null;

  const clearTimer = () => {
    if (timerId) {
      clearTimeout(timerId);
      timerId = null;
    }
  };

  const send = (messageIds) => {
    if (messageIds.length === 0 || !client.canSend()) {
      return;
    }

    try {
      client.markMessagesAsRead(messageIds);
    } catch (error) {
      console.error('Error marking messages as read:', error);
    }
  };

  const flush = (roomId) => {
    const key = roomId ?? DEFAULT_ROOM_KEY;
    const messageIds = pending.get(key);
    pending.delete(key);

    if (pending.size === 0) {
      clearTimer();
    }

    if (messageIds) {
      send(Array.from(messageIds));
    }
  };

  const flushAll = () => {
    clearTimer();

    const batches = Array.from(pending.values());
    pending.clear();

    for (const messageIds of batches) {
      send(Array.from(messageIds));
    }
  };

  const queue = (messageId, roomId) => {
    if (!messageId) {
      return;
    }

    const key = roomId ?? DEFAULT_ROOM_KEY;
    let messageIds = pending.get(key);

    if (!messageIds) {
      messageIds = new Set();
      pending.set(key, messageIds);
    }

    messageIds.add(messageId);

    if (messageIds.size >= maxBatch) {
      flush(roomId);
      return;
    }

    if (!timerId) {
      timerId = setTimeout(flushAll, flushInterval);
    }
  };

  const reset = () => {
    clearTimer();
    pending.clear();
  };

  return { queue, flush, flushAll, reset };
};

const readReceiptBatcher = createReadReceiptBatcher();

export default readReceiptBatcher;
