'use client';

import { useEffect, useRef, useState } from 'react';
import { CheckCircleIcon, ErrorCircleIcon } from '@vapor-ui/icons';
import {
  Box,
  Button,
  Callout,
  Field,
  Form,
  HStack,
  Text,
  TextInput,
  VStack,
} from '@vapor-ui/core';
import authService from '@/services/authService';

export default function LoginForm({ login, redirect, onNavigate = () => {}, registered = false }) {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
  });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [redirecting, setRedirecting] = useState(false);
  const [serverNotice, setServerNotice] = useState(null);
  const submittingRef = useRef(false);
  const isBusy = loading || redirecting;

  useEffect(() => {
    let active = true;

    const checkServerConnection = async () => {
      try {
        await authService.checkServerConnection();
      } catch {
        if (active) {
          setServerNotice(
            '서버 상태를 확인할 수 없습니다. 로그인을 시도해보세요. 문제가 지속되면 새로고침해주세요.'
          );
        }
      }
    };

    checkServerConnection();

    return () => {
      active = false;
    };
  }, []);

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (submittingRef.current) return;

    submittingRef.current = true;
    setLoading(true);
    setError(null);

    try {
      await login({
        email: formData.email.trim(),
        password: formData.password,
      });

      // 로그인 성공 후에는 라우팅이 시작될 때까지 폼을 잠근다.
      setRedirecting(true);
      const navigation = onNavigate(redirect || '/chat');

      // Pages Router는 navigation Promise를 반환하지만 App Router는 void를
      // 반환한다. Promise를 지원하는 경우에만 이동 완료를 기다리고,
      // App Router에서는 컴포넌트가 unmount될 때까지 redirecting 상태를 유지한다.
      if (navigation && typeof navigation.then === 'function') {
        await navigation;
        setRedirecting(false);
      }
    } catch (submitError) {
      setRedirecting(false);
      setError(submitError.message || '로그인 처리 중 오류가 발생했습니다.');
    } finally {
      submittingRef.current = false;
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-(--vapor-space-300) bg-(--vapor-color-background)">
      <VStack
        $css={{
          gap: '$250',
          width: '400px',
          padding: '$300',
          borderRadius: '$300',
          border: '1px solid var(--vapor-color-border-normal)',
        }}
        render={<Form onSubmit={handleSubmit} />}
      >
        <div className="mb-4 text-center">
          <img src="/images/logo-h.png" className="w-1/2 mx-auto" alt="KTB Chat 로고" />
        </div>

        {registered && (
          <Callout.Root colorPalette="success" role="status" data-testid="register-complete-message">
            <Callout.Icon>
              <CheckCircleIcon />
            </Callout.Icon>
            가입이 완료되었습니다. 로그인해 주세요.
          </Callout.Root>
        )}

        {serverNotice && (
          <Callout.Root colorPalette="warning" data-testid="server-status-message">
            <Callout.Icon>
              <ErrorCircleIcon />
            </Callout.Icon>
            {serverNotice}
          </Callout.Root>
        )}

        {error && (
          <Callout.Root colorPalette="warning" data-testid="login-error-message">
            <Callout.Icon>
              <ErrorCircleIcon />
            </Callout.Icon>
            {error}
          </Callout.Root>
        )}

        {redirecting && (
          <Text role="status" aria-live="polite" data-testid="login-redirect-status">
            로그인 완료. 페이지로 이동 중입니다.
          </Text>
        )}

        <VStack $css={{ gap: '$400' }}>
          <VStack $css={{ gap: '$200' }}>
            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                이메일
                <TextInput
                  id="login-email"
                  size="lg"
                  type="email"
                  required
                  disabled={isBusy}
                  value={formData.email}
                  onValueChange={(value) => setFormData((previous) => ({ ...previous, email: value }))}
                  placeholder="이메일을 입력하세요"
                  data-testid="login-email-input"
                />
              </Box>
              <Field.Error match="valueMissing">이메일을 입력해주세요.</Field.Error>
              <Field.Error match="typeMismatch">유효한 이메일 형식이 아닙니다.</Field.Error>
            </Field.Root>

            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                비밀번호
                <TextInput
                  id="login-password"
                  size="lg"
                  type="password"
                  required
                  disabled={isBusy}
                  value={formData.password}
                  onValueChange={(value) => setFormData((previous) => ({ ...previous, password: value }))}
                  placeholder="비밀번호를 입력하세요"
                  data-testid="login-password-input"
                />
              </Box>
              <Field.Error match="valueMissing">비밀번호를 입력해주세요.</Field.Error>
            </Field.Root>
          </VStack>

          <Button
            type="submit"
            size="lg"
            disabled={isBusy}
            aria-busy={isBusy}
            data-testid="login-submit-button"
          >
            {redirecting ? '이동 중...' : loading ? '로그인 중...' : '로그인'}
          </Button>
        </VStack>

        <HStack $css={{ justifyContent: 'center' }}>
          <Text typography="body2">계정이 없으신가요?</Text>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => onNavigate('/register')}
            disabled={isBusy}
          >
            회원가입
          </Button>
        </HStack>
      </VStack>
    </div>
  );
}
