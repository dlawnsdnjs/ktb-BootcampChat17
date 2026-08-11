import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginForm from '../LoginForm';

const authServiceMock = vi.hoisted(() => ({
  checkServerConnection: vi.fn(),
}));

vi.mock('@/services/authService', () => ({
  default: authServiceMock,
}));

const fillCredentials = () => {
  fireEvent.change(screen.getByTestId('login-email-input'), {
    target: { value: 'user@example.com' },
  });
  fireEvent.change(screen.getByTestId('login-password-input'), {
    target: { value: 'Password123!' },
  });
};

describe('LoginForm', () => {
  beforeEach(() => {
    authServiceMock.checkServerConnection.mockResolvedValue(true);
  });

  it('keeps the form busy until navigation completes', async () => {
    let resolveNavigation;
    const navigation = new Promise((resolve) => {
      resolveNavigation = resolve;
    });
    const login = vi.fn().mockResolvedValue({});
    const onNavigate = vi.fn(() => navigation);

    render(<LoginForm login={login} onNavigate={onNavigate} />);
    fillCredentials();

    const submitButton = screen.getByTestId('login-submit-button');
    fireEvent.click(submitButton);

    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('/chat'));
    expect(submitButton).toBeDisabled();
    expect(submitButton).toHaveTextContent('이동 중...');
    expect(screen.getByTestId('login-redirect-status')).toBeVisible();

    resolveNavigation();

    await waitFor(() => expect(submitButton).not.toBeDisabled());
    expect(submitButton).toHaveTextContent('로그인');
  });

  it('does not submit more than once while login is pending', async () => {
    let resolveLogin;
    const login = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve;
        })
    );
    const onNavigate = vi.fn();

    render(<LoginForm login={login} onNavigate={onNavigate} />);
    fillCredentials();

    const submitButton = screen.getByTestId('login-submit-button');
    fireEvent.click(submitButton);
    fireEvent.click(submitButton);

    expect(login).toHaveBeenCalledTimes(1);
    expect(submitButton).toBeDisabled();

    resolveLogin({});

    await waitFor(() => expect(onNavigate).toHaveBeenCalledWith('/chat'));
  });
});
