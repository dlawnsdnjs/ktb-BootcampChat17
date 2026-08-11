import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ChatRoomsView from '../ChatRoomsView';
import { CONNECTION_STATUS } from '../useServerConnection';

const mocks = vi.hoisted(() => ({
  rooms: [],
  handleJoinRoom: vi.fn(),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'user-1', token: 'token-1', sessionId: 'session-1' },
  }),
}));

vi.mock('../useServerConnection', async () => {
  const actual = await vi.importActual('../useServerConnection');
  return {
    ...actual,
    useServerConnection: () => ({
      connectionStatus: actual.CONNECTION_STATUS.CONNECTED,
      setConnectionStatus: vi.fn(),
      retryCount: 0,
      setRetryCount: vi.fn(),
      isRetrying: false,
      setIsRetrying: vi.fn(),
      getRetryDelay: vi.fn(() => 1000),
      attemptConnection: vi.fn(() => Promise.resolve(true)),
    }),
  };
});

vi.mock('../useRoomList', () => ({
  useRoomList: () => ({
    rooms: mocks.rooms,
    setRooms: vi.fn(),
    error: null,
    loading: false,
    refreshing: false,
    joiningRoom: false,
    fetchRooms: vi.fn(() => Promise.resolve()),
    refreshRooms: vi.fn(() => Promise.resolve(true)),
    handleJoinRoom: mocks.handleJoinRoom,
  }),
}));

vi.mock('../useRoomsSocket', () => ({ useRoomsSocket: vi.fn() }));

const CREATED_AT = '2026-06-20T12:00:00.000Z';

const openRoom = {
  _id: 'open-1',
  name: '공개방',
  hasPassword: false,
  participants: [],
  createdAt: CREATED_AT,
};
const lockedRoom = {
  _id: 'locked-1',
  name: '비밀방',
  hasPassword: true,
  participants: [],
  createdAt: CREATED_AT,
};

const renderView = () => render(<ChatRoomsView router={{ push: vi.fn() }} />);

const clickJoin = () => fireEvent.click(screen.getByTestId('join-chat-room-button'));

describe('비밀번호 방 입장', () => {
  beforeEach(() => {
    mocks.handleJoinRoom.mockReset();
    mocks.handleJoinRoom.mockResolvedValue({ success: true });
  });

  it('공개방은 비밀번호를 묻지 않고 바로 입장한다', async () => {
    mocks.rooms = [openRoom];
    renderView();

    clickJoin();

    await waitFor(() => {
      expect(mocks.handleJoinRoom).toHaveBeenCalledWith('open-1');
    });
    expect(screen.queryByTestId('room-password-dialog')).not.toBeInTheDocument();
  });

  it('잠긴 방은 즉시 입장하지 않고 비밀번호를 묻는다', async () => {
    mocks.rooms = [lockedRoom];
    renderView();

    clickJoin();

    await waitFor(() => {
      expect(screen.getByTestId('room-password-dialog')).toBeInTheDocument();
    });
    expect(mocks.handleJoinRoom).not.toHaveBeenCalled();
  });

  it('입력한 비밀번호를 담아 입장을 요청한다', async () => {
    mocks.rooms = [lockedRoom];
    renderView();

    clickJoin();
    await screen.findByTestId('room-password-dialog');

    fireEvent.change(screen.getByTestId('room-password-input'), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByTestId('room-password-submit'));

    await waitFor(() => {
      expect(mocks.handleJoinRoom).toHaveBeenCalledWith('locked-1', 'secret');
    });
  });

  it('비밀번호가 틀리면 입력창을 유지한 채 오류를 보여준다', async () => {
    mocks.rooms = [lockedRoom];
    mocks.handleJoinRoom.mockResolvedValue({
      success: false,
      passwordRejected: true,
      message: '비밀번호가 일치하지 않습니다.',
    });
    renderView();

    clickJoin();
    await screen.findByTestId('room-password-dialog');

    fireEvent.change(screen.getByTestId('room-password-input'), {
      target: { value: 'wrong' },
    });
    fireEvent.click(screen.getByTestId('room-password-submit'));

    await waitFor(() => {
      expect(screen.getByTestId('room-password-error')).toHaveTextContent(
        '비밀번호가 일치하지 않습니다.'
      );
    });
    expect(screen.getByTestId('room-password-dialog')).toBeInTheDocument();
  });
});
