import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import LoginForm from '../LoginForm';

const authServiceMock = vi.hoisted(() => ({
  checkServerConnection: vi.fn(),
}));

vi.mock('@/services/authService', () => ({
  default: authServiceMock,
}));

const renderLoginForm = (props = {}) => {
  const login = props.login || vi.fn().mockResolvedValue({});
  const onNavigate = props.onNavigate || vi.fn();

  render(
    <LoginForm
      login={login}
      onNavigate={onNavigate}
      {...props}
    />
  );

  return { login, onNavigate };
};

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authServiceMock.checkServerConnection.mockReturnValue(new Promise(() => {}));
  });

  it('renders the form while health check is still pending', () => {
    renderLoginForm();

    expect(screen.getByTestId('login-email-input')).toBeVisible();
    expect(screen.getByTestId('login-password-input')).toBeVisible();
    expect(screen.getByTestId('login-submit-button')).toBeVisible();
    expect(screen.getByTestId('login-submit-button')).not.toBeDisabled();
  });

  it('allows login when health check fails', async () => {
    authServiceMock.checkServerConnection.mockRejectedValue(new Error('offline'));
    const { login } = renderLoginForm();

    expect(await screen.findByTestId('server-status-message')).toBeVisible();

    fireEvent.change(screen.getByTestId('login-email-input'), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByTestId('login-password-input'), {
      target: { value: 'password' },
    });
    fireEvent.click(screen.getByTestId('login-submit-button'));

    await waitFor(() => expect(login).toHaveBeenCalledTimes(1));
  });

  it('sends only one login request for duplicate clicks', async () => {
    let resolveLogin;
    const login = vi.fn(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve;
        })
    );
    renderLoginForm({ login });

    fireEvent.change(screen.getByTestId('login-email-input'), {
      target: { value: 'user@example.com' },
    });
    fireEvent.change(screen.getByTestId('login-password-input'), {
      target: { value: 'password' },
    });

    const submitButton = screen.getByTestId('login-submit-button');
    fireEvent.click(submitButton);
    fireEvent.click(submitButton);

    await waitFor(() => expect(login).toHaveBeenCalledTimes(1));
    expect(submitButton).toBeDisabled();

    resolveLogin({});
    await waitFor(() => expect(submitButton).not.toBeDisabled());
  });
});
