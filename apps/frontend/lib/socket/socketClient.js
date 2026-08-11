import socketService from '../../services/socket';

const sendDomainEvent = (service, socket, event, data) => {
  if (socket) {
    return service.sendOn(socket, event, data);
  }

  return service.send(event, data);
};

const ensureConnectedSocket = (socket) => {
  if (!socket?.connected) {
    throw new Error('Socket not connected');
  }
};

const createTimeoutError = (message) => new Error(message);

const waitForSocketEvent = ({
  socket,
  successEvent,
  errorEvents,
  timeoutMs,
  timeoutMessage,
  successPredicate = () => true,
  send,
}) => {
  ensureConnectedSocket(socket);

  return new Promise((resolve, reject) => {
    let timeoutId;

    const cleanup = () => {
      if (timeoutId) {
        clearTimeout(timeoutId);
      }
      socket.off(successEvent, handleSuccess);
      for (const event of errorEvents) {
        socket.off(event, handleError);
      }
    };

    const settle = (callback, value) => {
      cleanup();
      callback(value);
    };

    const handleSuccess = (data) => {
      if (successPredicate(data)) {
        settle(resolve, data);
      }
    };
    const handleError = (error) => settle(reject, error);

    socket.on(successEvent, handleSuccess);
    for (const event of errorEvents) {
      socket.once(event, handleError);
    }

    timeoutId = setTimeout(() => {
      settle(reject, createTimeoutError(timeoutMessage));
    }, timeoutMs);

    try {
      send();
    } catch (error) {
      settle(reject, error);
    }
  });
};

const roomEventMap = {
  participantsUpdate: 'onParticipantsUpdate',
  messagesRead: 'onMessagesRead',
  message: 'onMessage',
  previousMessagesLoaded: 'onPreviousMessagesLoaded',
  messageReactionUpdate: 'onMessageReactionUpdate',
  error: 'onError',
};

const connectionEventMap = {
  connect: 'onConnect',
  disconnect: 'onDisconnect',
  connect_error: 'onConnectError',
};

/**
 * socket.io v4 에서 재연결 이벤트는 socket 이 아니라 manager(socket.io)에서 발생한다.
 * socket 에 붙이면 영원히 호출되지 않아 재연결 성공도 최종 실패도 감지하지 못한다.
 */
const managerEventMap = {
  reconnect_attempt: 'onReconnecting',
  reconnect: 'onReconnect',
  reconnect_failed: 'onReconnectFailed',
};

const createClientMessageId = () => globalThis.crypto?.randomUUID?.()
  || `${Date.now()}-${Math.random().toString(16).slice(2)}`;

const pendingMessageAcks = new WeakMap();

const trackPendingMessageAck = (socket, promise) => {
  let pending = pendingMessageAcks.get(socket);
  if (!pending) {
    pending = new Set();
    pendingMessageAcks.set(socket, pending);
  }

  pending.add(promise);
  const remove = () => {
    pending.delete(promise);
    if (pending.size === 0) {
      pendingMessageAcks.delete(socket);
    }
  };
  promise.then(remove, remove);
  return promise;
};

const waitForPendingMessageAcks = (socket) => {
  const pending = pendingMessageAcks.get(socket);
  return pending?.size ? Promise.allSettled([...pending]) : Promise.resolve([]);
};

const subscribeMappedEvents = (emitter, handlers, eventMap) => {
  if (!emitter) {
    return () => {};
  }

  const subscriptions = Object.entries(eventMap)
    .map(([event, handlerName]) => [event, handlers[handlerName]])
    .filter(([, handler]) => typeof handler === 'function');

  for (const [event, handler] of subscriptions) {
    emitter.on(event, handler);
  }

  return () => {
    for (const [event, handler] of subscriptions) {
      emitter.off(event, handler);
    }
  };
};

export const createSocketClient = (service = socketService) => ({
  connect: (options) => service.connect(options),
  disconnect: () => service.disconnect(),
  isConnected: () => service.isConnected(),
  canSend: () => service.isConnected(),
  send: (event, data) => service.send(event, data),
  sendChatMessage: (payload, socket) => sendDomainEvent(service, socket, 'chatMessage', payload),
  sendChatMessageAndWait: (payload, socket, { timeoutMs = 8000 } = {}) => {
    const clientMessageId = payload.clientMessageId || createClientMessageId();
    const correlatedPayload = { ...payload, clientMessageId };
    return trackPendingMessageAck(socket, waitForSocketEvent({
      socket,
      successEvent: 'messageAck',
      errorEvents: ['error'],
      timeoutMs,
      timeoutMessage: '메시지 전송이 지연되고 있습니다. 다시 시도해주세요.',
      successPredicate: ack => ack?.clientMessageId === clientMessageId,
      // Delivery is at-least-once; the server atomically deduplicates clientMessageId.
      // Two writes also survive a hard navigation dropping the browser's final frame.
      send: () => {
        sendDomainEvent(service, socket, 'chatMessage', correlatedPayload);
        sendDomainEvent(service, socket, 'chatMessage', correlatedPayload);
      },
    }));
  },
  closeRoomWhenIdle: async (roomId, socket, { disconnectDelayMs = 25 } = {}) => {
    if (!socket) return;

    await waitForPendingMessageAcks(socket);
    if (socket.connected) {
      try {
        sendDomainEvent(service, socket, 'leaveRoom', roomId);
      } catch (_) {}
    }

    await new Promise(resolve => setTimeout(resolve, disconnectDelayMs));
    socket.disconnect();
    socket.removeAllListeners?.();
  },
  fetchPreviousMessages: (payload, socket) => sendDomainEvent(service, socket, 'fetchPreviousMessages', payload),
  fetchPreviousMessagesAndWait: (payload, socket, { timeoutMs = 10000 } = {}) =>
    waitForSocketEvent({
      socket,
      successEvent: 'previousMessagesLoaded',
      errorEvents: ['error'],
      timeoutMs,
      timeoutMessage: '메시지 로딩 시간이 초과되었습니다.',
      send: () => sendDomainEvent(service, socket, 'fetchPreviousMessages', payload),
    }),
  joinRoom: (roomId, socket) => sendDomainEvent(service, socket, 'joinRoom', roomId),
  joinRoomAndWait: (roomId, socket, { timeoutMs = 10000 } = {}) =>
    waitForSocketEvent({
      socket,
      successEvent: 'joinRoomSuccess',
      errorEvents: ['joinRoomError', 'error'],
      timeoutMs,
      timeoutMessage: '채팅방 입장 시간이 초과되었습니다.',
      send: () => sendDomainEvent(service, socket, 'joinRoom', roomId),
    }),
  leaveRoom: (roomId, socket) => sendDomainEvent(service, socket, 'leaveRoom', roomId),
  tryLeaveRoom: (roomId, socket) => service.trySendOn(socket, 'leaveRoom', roomId),
  markMessagesAsRead: (messageIds, socket) => {
    if (!Array.isArray(messageIds)) {
      throw new Error('messageIds must be an array');
    }

    return sendDomainEvent(service, socket, 'markMessagesAsRead', { messageIds });
  },
  sendMessageReaction: (messageId, reaction, type, socket) => sendDomainEvent(service, socket, 'messageReaction', {
    messageId,
    reaction,
    type,
  }),
  subscribeRoomEvents: (socket, handlers) => subscribeMappedEvents(socket, handlers, roomEventMap),
  subscribeConnectionEvents: (socket, handlers) => {
    const unsubscribeSocket = subscribeMappedEvents(socket, handlers, connectionEventMap);
    const unsubscribeManager = subscribeMappedEvents(socket?.io, handlers, managerEventMap);

    return () => {
      unsubscribeSocket();
      unsubscribeManager();
    };
  },
});

const socketClient = createSocketClient();

export default socketClient;
