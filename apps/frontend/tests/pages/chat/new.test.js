import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import NewChatRoom from '../../../pages/chat/new';

const mocks = vi.hoisted(() => ({
  currentUser: { token: 'token-1' },
  post: vi.fn(),
  push: vi.fn(),
}));

vi.mock('next/router', () => ({
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ user: mocks.currentUser }),
}));

vi.mock('@/lib/api/client', () => ({
  default: { post: mocks.post },
}));

const enterRoomNameAndSubmit = (name = '새 채팅방') => {
  fireEvent.change(screen.getByTestId('chat-room-name-input'), {
    target: { value: name },
  });
  fireEvent.click(screen.getByTestId('create-chat-room-button'));
};

describe('NewChatRoom', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.currentUser = { token: 'token-1' };
  });

  it('creates a room once and navigates directly without a REST join request', async () => {
    mocks.post.mockResolvedValue({
      data: { data: { _id: 'room-1' } },
    });
    render(<NewChatRoom />);

    enterRoomNameAndSubmit(' 일반 방 ');

    await waitFor(() => {
      expect(mocks.push).toHaveBeenCalledWith('/chat/room-1');
    });
    expect(mocks.post).toHaveBeenCalledTimes(1);
    expect(mocks.post).toHaveBeenCalledWith('/api/rooms', {
      name: '일반 방',
      password: undefined,
    });
    expect(
      mocks.post.mock.calls.some(([url]) => url === '/api/rooms/room-1/join'),
    ).toBe(false);
  });

  it('does not navigate and displays the error when room creation fails', async () => {
    mocks.post.mockRejectedValue(new Error('채팅방 생성 실패'));
    render(<NewChatRoom />);

    enterRoomNameAndSubmit();

    expect(await screen.findByText('채팅방 생성 실패')).toBeInTheDocument();
    expect(mocks.push).not.toHaveBeenCalled();
    expect(mocks.post).toHaveBeenCalledTimes(1);
  });

  it('keeps the password room creation payload', async () => {
    mocks.post.mockResolvedValue({
      data: { data: { _id: 'private-room' } },
    });
    render(<NewChatRoom />);

    fireEvent.change(screen.getByTestId('chat-room-name-input'), {
      target: { value: '비밀 방' },
    });
    fireEvent.click(screen.getByRole('switch', { name: '비밀번호 설정' }));
    fireEvent.change(screen.getByPlaceholderText('비밀번호를 입력하세요'), {
      target: { value: 'secret-1234' },
    });
    fireEvent.click(screen.getByTestId('create-chat-room-button'));

    await waitFor(() => {
      expect(mocks.push).toHaveBeenCalledWith('/chat/private-room');
    });
    expect(mocks.post).toHaveBeenCalledTimes(1);
    expect(mocks.post).toHaveBeenCalledWith('/api/rooms', {
      name: '비밀 방',
      password: 'secret-1234',
    });
  });

  it('does not submit without authentication and displays the existing error', async () => {
    mocks.currentUser = null;
    render(<NewChatRoom />);

    enterRoomNameAndSubmit();

    expect(
      await screen.findByText('인증 정보가 없습니다. 다시 로그인해주세요.'),
    ).toBeInTheDocument();
    expect(mocks.post).not.toHaveBeenCalled();
    expect(mocks.push).not.toHaveBeenCalled();
  });

  it('keeps the form in a loading state while creation is pending', async () => {
    mocks.post.mockReturnValue(new Promise(() => {}));
    render(<NewChatRoom />);

    enterRoomNameAndSubmit();

    expect(await screen.findByText('생성 중...')).toBeInTheDocument();
    expect(screen.getByTestId('create-chat-room-button')).toBeDisabled();
    expect(screen.getByTestId('chat-room-name-input')).toBeDisabled();
  });
});
