import React, { useRef, useState } from 'react';
import { useRouter } from 'next/router';
import { ErrorCircleIcon, CheckCircleIcon } from '@vapor-ui/icons';
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
import { useAuth, withoutAuth } from '@/contexts/AuthContext';
import { measureDuration, UI_METRICS } from '@/lib/api/requestMetrics';
import {
  EMAIL_PATTERN,
  NAME_MIN_LENGTH,
  PASSWORD_HINT,
  PASSWORD_PATTERN,
  REGISTER_MESSAGES,
} from '@/lib/auth/registerRules';

const LOGIN_PATH_AFTER_REGISTER = '/?registered=1';

const Register = () => {
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const submittingRef = useRef(false);
  const router = useRouter();
  const { register: registerContext } = useAuth();

  const trimmedName = formData.name.trim();
  const nameTooShort = trimmedName.length > 0 && trimmedName.length < NAME_MIN_LENGTH;
  const passwordMismatch =
    formData.confirmPassword.length > 0 && formData.password !== formData.confirmPassword;

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (submittingRef.current) return;

    if (formData.password !== formData.confirmPassword || nameTooShort) return;

    submittingRef.current = true;
    setLoading(true);
    setError(null);

    try {
      await registerContext({
        name: trimmedName,
        email: formData.email.trim(),
        password: formData.password,
      });

      setSuccess(true);

      await measureDuration(UI_METRICS.REGISTER_REDIRECT, () =>
        router.replace(LOGIN_PATH_AFTER_REGISTER)
      );
    } catch (err) {
      setError(err.message || '회원가입 처리 중 오류가 발생했습니다.');
      submittingRef.current = false;
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-[var(--vapor-space-300)] bg-[var(--vapor-color-background)]">
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
        <div className="text-center mb-4">
          <img src="images/logo-h.png" className="w-1/2 mx-auto" alt="KTB Chat 로고" />
        </div>

        {error && (
          <Callout.Root colorPalette="warning" role="alert" data-testid="register-error-message">
            <Callout.Icon>
              <ErrorCircleIcon />
            </Callout.Icon>
            {error}
          </Callout.Root>
        )}

        {success && (
          <Callout.Root colorPalette="success" role="status" data-testid="register-success-message">
            <Callout.Icon>
              <CheckCircleIcon />
            </Callout.Icon>
            가입이 완료되었습니다. 로그인 화면으로 이동합니다.
          </Callout.Root>
        )}

        <VStack $css={{ gap: '$400' }}>
          <VStack $css={{ gap: '$200' }}>
            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                이름
                <TextInput
                  id="register-name"
                  size="lg"
                  type="text"
                  required
                  disabled={loading}
                  value={formData.name}
                  onValueChange={(value) => setFormData(prev => ({ ...prev, name: value }))}
                  placeholder="이름을 입력하세요"
                  data-testid="register-name-input"
                />
              </Box>
              <Field.Error match="valueMissing">{REGISTER_MESSAGES.nameRequired}</Field.Error>
              <Field.Error match={nameTooShort} data-testid="register-name-too-short">
                {REGISTER_MESSAGES.nameTooShort}
              </Field.Error>
            </Field.Root>

            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                이메일
                <TextInput
                  id="register-email"
                  size="lg"
                  type="email"
                  required
                  pattern={EMAIL_PATTERN}
                  disabled={loading}
                  value={formData.email}
                  onValueChange={(value) => setFormData(prev => ({ ...prev, email: value }))}
                  placeholder="이메일을 입력하세요"
                  data-testid="register-email-input"
                />
              </Box>
              <Field.Error match="valueMissing">{REGISTER_MESSAGES.emailRequired}</Field.Error>
              <Field.Error match="typeMismatch">{REGISTER_MESSAGES.emailInvalid}</Field.Error>
              <Field.Error match="patternMismatch">{REGISTER_MESSAGES.emailInvalid}</Field.Error>
            </Field.Root>

            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                비밀번호
                <TextInput
                  id="register-password"
                  size="lg"
                  type="password"
                  required
                  pattern={PASSWORD_PATTERN}
                  disabled={loading}
                  value={formData.password}
                  onValueChange={(value) => setFormData(prev => ({ ...prev, password: value }))}
                  placeholder="비밀번호를 입력하세요"
                  data-testid="register-password-input"
                />
              </Box>
              <Field.Description>{PASSWORD_HINT}</Field.Description>
              <Field.Error match="valueMissing">{REGISTER_MESSAGES.passwordRequired}</Field.Error>
              <Field.Error match="patternMismatch">{REGISTER_MESSAGES.passwordRule}</Field.Error>
            </Field.Root>

            <Field.Root>
              <Box
                render={<Field.Label />}
                $css={{ flexDirection: 'column' }}
                style={{ fontSize: '14px', fontWeight: '500', marginBottom: '8px' }}
              >
                비밀번호 확인
                <TextInput
                  id="register-password-confirm"
                  size="lg"
                  type="password"
                  required
                  disabled={loading}
                  value={formData.confirmPassword}
                  onValueChange={(value) => setFormData(prev => ({ ...prev, confirmPassword: value }))}
                  placeholder="비밀번호를 다시 입력하세요"
                  data-testid="register-password-confirm-input"
                />
              </Box>
              <Field.Error match="valueMissing">
                {REGISTER_MESSAGES.passwordConfirmRequired}
              </Field.Error>
              <Field.Error match={passwordMismatch} data-testid="register-password-mismatch">
                {REGISTER_MESSAGES.passwordMismatch}
              </Field.Error>
            </Field.Root>
          </VStack>

          <Button
            type="submit"
            size="lg"
            disabled={loading || passwordMismatch || nameTooShort}
            data-testid="register-submit-button"
          >
            {loading ? '회원가입 중...' : '회원가입'}
          </Button>
        </VStack>

        <HStack $css={{ justifyContent: 'center' }}>
          <Text typography="body2">이미 계정이 있으신가요?</Text>
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => router.push('/')}
            disabled={loading}
          >
            로그인
          </Button>
        </HStack>
      </VStack>
    </div>
  );
};

export default withoutAuth(Register);
