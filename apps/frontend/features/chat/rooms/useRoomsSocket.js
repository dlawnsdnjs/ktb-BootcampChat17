import { useRef, useEffect } from 'react';
import socketClient from '@/lib/socket/socketClient';

const CONNECTION_STATUS = {
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  ERROR: 'error',
};

export const useRoomsSocket = ({
  currentUser,
  setConnectionStatus,
  setRooms,
}) => {
  const socketRef = useRef(null);

  useEffect(() => {
    if (!currentUser?.token) return;

    let isSubscribed = true;
    let roomFlushTimer = null;
    const pendingRooms = {
      created: new Map(),
      updated: new Map(),
      activity: new Map(),
    };

    const scheduleRoomsFlush = () => {
      if (roomFlushTimer !== null) return;

      roomFlushTimer = setTimeout(() => {
        roomFlushTimer = null;
        if (!isSubscribed) return;

        const created = Array.from(pendingRooms.created.values()).reverse();
        const updated = new Map(pendingRooms.updated);
        const activity = new Map(pendingRooms.activity);
        pendingRooms.created.clear();
        pendingRooms.updated.clear();
        pendingRooms.activity.clear();

        const applyPending = (room) => {
          const roomId = room?._id;
          const replacement = updated.get(roomId) || room;
          const recentMessageCount = activity.get(roomId);
          return recentMessageCount === undefined
            ? replacement
            : { ...replacement, recentMessageCount };
        };

        setRooms((previousRooms) => {
          const existingIds = new Set(previousRooms.map((room) => room._id));
          const additions = created
            .filter((room) => room?._id && !existingIds.has(room._id))
            .map(applyPending);
          return [...additions, ...previousRooms.map(applyPending)];
        });
      }, 0);
    };

    const connectSocket = async () => {
      try {
        const socket = await socketClient
          .connect({
            auth: {
              token: currentUser.token,
              sessionId: currentUser.sessionId,
            },
          })
          .catch((err) => {
            console.log('Socket connection error:', err);
            setConnectionStatus(CONNECTION_STATUS.ERROR);
          });

        if (!isSubscribed || !socket) return;

        socketRef.current = socket;

        const subscribeRoomList = () => {
          socket.emit('joinRoomList');
          setConnectionStatus(CONNECTION_STATUS.CONNECTED);
        };

        const handlers = {
          connect: subscribeRoomList,
          disconnect: () => {
            setConnectionStatus(CONNECTION_STATUS.DISCONNECTED);
          },
          error: () => {
            setConnectionStatus(CONNECTION_STATUS.ERROR);
          },
          roomCreated: (newRoom) => {
            if (!newRoom?._id) return;
            pendingRooms.created.set(newRoom._id, newRoom);
            scheduleRoomsFlush();
          },
          roomUpdated: (updatedRoom) => {
            if (!updatedRoom?._id) return;
            pendingRooms.updated.set(updatedRoom._id, updatedRoom);
            scheduleRoomsFlush();
          },
          // 활성도 지표만 담긴 경량 payload이므로 방 정보를 덮지 않고 병합한다
          roomActivity: (activity) => {
            if (!activity?._id) return;
            pendingRooms.activity.set(activity._id, activity.recentMessageCount);
            scheduleRoomsFlush();
          },
        };

        Object.entries(handlers).forEach(([event, handler]) => {
          socket.on(event, handler);
        });

        // socketClient.connect resolves after the initial connect event, so subscribe now.
        subscribeRoomList();
      } catch (error) {
        if (!isSubscribed) return;

        if (
          error.message?.includes('Authentication required') ||
          error.message?.includes('Invalid session')
        ) {
          // Auth error will be handled by the useAuth context
        }

        setConnectionStatus(CONNECTION_STATUS.ERROR);
      }
    };

    connectSocket();

    return () => {
      isSubscribed = false;
      if (roomFlushTimer !== null) {
        clearTimeout(roomFlushTimer);
      }
      if (socketRef.current) {
        socketRef.current.emit('leaveRoomList');
        socketRef.current.disconnect();
        socketRef.current = null;
      }
    };
  }, [currentUser]); // eslint-disable-line react-hooks/exhaustive-deps

  return { socketRef };
};

export default useRoomsSocket;
