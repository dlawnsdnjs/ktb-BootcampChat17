import React from 'react';
import { render, waitFor, fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ChatInput from '../ChatInput';

const renderChatInput = (onSubmit = vi.fn()) => {
  render(
    <ChatInput
      fileInputRef={{ current: null }}
      room={{ participants: [] }}
      onSubmit={onSubmit}
    />
  );

  const textarea = screen.getByTestId('chat-message-input');
  fireEvent.change(textarea, { target: { value: '안녕하세요' } });

  return { textarea, onSubmit };
};

describe('ChatInput', () => {
  it('does not submit while the IME is still composing', () => {
    const { textarea, onSubmit } = renderChatInput();

    fireEvent.keyDown(textarea, { key: 'Enter', isComposing: true });

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('does not submit on the legacy keyCode 229 composition signal', () => {
    const { textarea, onSubmit } = renderChatInput();

    fireEvent.keyDown(textarea, { key: 'Enter', keyCode: 229 });

    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits once when Enter arrives after composition ends', () => {
    const { textarea, onSubmit } = renderChatInput();

    fireEvent.keyDown(textarea, { key: 'Enter', isComposing: true });
    fireEvent.keyDown(textarea, { key: 'Enter' });

    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it('renders the lazy emoji picker under React 19', async () => {
    const { container, getByLabelText } = render(
      <ChatInput
        fileInputRef={{ current: null }}
        room={{ participants: [] }}
      />
    );

    fireEvent.click(getByLabelText('이모티콘'));

    await waitFor(() => {
      expect(container.querySelector('em-emoji-picker')).toBeInTheDocument();
    });
  });
});
