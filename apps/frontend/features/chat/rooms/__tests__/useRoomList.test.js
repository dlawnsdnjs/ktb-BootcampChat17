import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import axiosInstance from '@/services/axios';
import { useRoomList } from '../useRoomList';
import { CONNECTION_STATUS } from '../useServerConnection';

vi.mock('@/services/axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const roomsResponse = (rooms) => ({ data: { data: rooms } });

const renderRoomList = () =>
  renderHook(() =>
    useRoomList({
      currentUser: { token: 'token-1' },
      router: { push: vi.fn() },
      connectionStatus: CONNECTION_STATUS.CONNECTED,
      setConnectionStatus: vi.fn(),
      retryCount: 0,
      setRetryCount: vi.fn(),
      isRetrying: false,
      setIsRetrying: vi.fn(),
      getRetryDelay: vi.fn(() => 1000),
      attemptConnection: vi.fn(() => Promise.resolve(true)),
    })
  );

describe('useRoomList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('replaces the list on refresh without leaving the refreshing flag on', async () => {
    axiosInstance.get.mockResolvedValue(roomsResponse([{ _id: 'room-1' }]));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.rooms).toEqual([{ _id: 'room-1' }]);
    expect(result.current.refreshing).toBe(false);
  });

  it('keeps the current list and stays quiet when a silent refresh fails', async () => {
    axiosInstance.get.mockResolvedValueOnce(roomsResponse([{ _id: 'room-1' }]));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.fetchRooms();
    });

    axiosInstance.get.mockRejectedValueOnce(new Error('SERVER_UNREACHABLE'));

    await act(async () => {
      await result.current.refreshRooms({ silent: true });
    });

    expect(result.current.rooms).toEqual([{ _id: 'room-1' }]);
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it('surfaces a refresh failure when the user asked for it', async () => {
    axiosInstance.get.mockRejectedValue(new Error('SERVER_UNREACHABLE'));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).toMatchObject({
      title: '채팅방 목록 갱신 실패',
      showRetry: false,
    });
  });

  it('clears a previous error once a refresh succeeds', async () => {
    axiosInstance.get.mockRejectedValueOnce(new Error('SERVER_UNREACHABLE'));

    const { result } = renderRoomList();

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).not.toBeNull();

    axiosInstance.get.mockResolvedValueOnce(roomsResponse([{ _id: 'room-1' }]));

    await act(async () => {
      await result.current.refreshRooms();
    });

    expect(result.current.error).toBeNull();
    expect(result.current.rooms).toEqual([{ _id: 'room-1' }]);
  });

  describe('handleJoinRoom', () => {
    it('sends the password when one is supplied', async () => {
      axiosInstance.post.mockResolvedValueOnce({ data: { success: true } });
      const { result } = renderRoomList();

      await act(async () => {
        await result.current.handleJoinRoom('room-1', 'secret');
      });

      expect(axiosInstance.post).toHaveBeenCalledWith('/api/rooms/room-1/join', {
        password: 'secret',
      });
    });

    it('omits the password field for open rooms', async () => {
      axiosInstance.post.mockResolvedValueOnce({ data: { success: true } });
      const { result } = renderRoomList();

      await act(async () => {
        await result.current.handleJoinRoom('room-1');
      });

      expect(axiosInstance.post).toHaveBeenCalledWith('/api/rooms/room-1/join', {});
    });

    it('reports a rejected password to the caller without raising a list error', async () => {
      axiosInstance.post.mockRejectedValueOnce({
        response: { status: 403, data: { message: '비밀번호가 일치하지 않습니다.' } },
      });
      const { result } = renderRoomList();

      let outcome;
      await act(async () => {
        outcome = await result.current.handleJoinRoom('room-1', 'wrong');
      });

      expect(outcome).toEqual({
        success: false,
        passwordRejected: true,
        message: '비밀번호가 일치하지 않습니다.',
      });
      expect(result.current.error).toBeNull();
    });

    it('surfaces non-password failures as a list error', async () => {
      axiosInstance.post.mockRejectedValueOnce({
        response: { status: 404, data: {} },
      });
      const { result } = renderRoomList();

      let outcome;
      await act(async () => {
        outcome = await result.current.handleJoinRoom('room-1');
      });

      expect(outcome).toEqual({ success: false });
      expect(result.current.error?.message).toBe('채팅방을 찾을 수 없습니다.');
    });
  });
});
