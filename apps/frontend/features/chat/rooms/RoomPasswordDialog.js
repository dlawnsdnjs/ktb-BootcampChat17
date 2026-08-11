import React, { useState } from 'react';
import { ErrorCircleIcon } from '@vapor-ui/icons';
import {
  Box,
  Button,
  Callout,
  Dialog,
  Field,
  HStack,
  Text,
  TextInput,
  VStack,
} from '@vapor-ui/core';

const RoomPasswordDialog = ({
  room,
  submitting = false,
  errorMessage = '',
  onSubmit,
  onClose,
}) => {
  const [password, setPassword] = useState('');

  const open = Boolean(room);

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!password.trim() || submitting) return;
    onSubmit(password);
  };

  return (
    <Dialog.Root
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) onClose();
      }}
    >
      <Dialog.Popup data-testid="room-password-dialog">
        <form onSubmit={handleSubmit}>
          <Dialog.Header>
            <Dialog.Title>비밀번호 입력</Dialog.Title>
          </Dialog.Header>

          <Dialog.Body>
            <VStack $css={{ gap: '$200' }}>
              <Text typography="body2" foreground="normal-200">
                {room?.name}
              </Text>

              {errorMessage && (
                <Callout.Root colorPalette="danger" data-testid="room-password-error">
                  <HStack $css={{ gap: '$200', alignItems: 'flex-start' }}>
                    <Callout.Icon>
                      <ErrorCircleIcon size={18} />
                    </Callout.Icon>
                    <Text typography="body2">{errorMessage}</Text>
                  </HStack>
                </Callout.Root>
              )}

              <Field.Root>
                <Box render={<Field.Label />} $css={{ flexDirection: 'column' }}>
                  <Text typography="subtitle2" foreground="normal-200">
                    비밀번호
                  </Text>
                  <TextInput
                    id="join-room-password"
                    type="password"
                    size="lg"
                    autoFocus
                    placeholder="비밀번호를 입력하세요"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    disabled={submitting}
                    data-testid="room-password-input"
                  />
                </Box>
              </Field.Root>
            </VStack>
          </Dialog.Body>

          <Dialog.Footer>
            <HStack $css={{ gap: '$100', justifyContent: 'flex-end' }}>
              <Button
                type="button"
                colorPalette="secondary"
                onClick={onClose}
                disabled={submitting}
              >
                취소
              </Button>
              <Button
                type="submit"
                colorPalette="primary"
                disabled={submitting || !password.trim()}
                data-testid="room-password-submit"
              >
                {submitting ? '입장 중...' : '입장'}
              </Button>
            </HStack>
          </Dialog.Footer>
        </form>
      </Dialog.Popup>
    </Dialog.Root>
  );
};

export default RoomPasswordDialog;
