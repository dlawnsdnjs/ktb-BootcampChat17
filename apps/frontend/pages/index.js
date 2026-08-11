import { useRouter } from 'next/router';
import { useAuth, withoutAuth } from '@/contexts/AuthContext';
import LoginForm from '@/components/auth/LoginForm';

const LoginPage = () => {
  const router = useRouter();
  const { login } = useAuth();

  return (
    <LoginForm
      login={login}
      redirect={typeof router.query.redirect === 'string' ? router.query.redirect : undefined}
      registered={router.query.registered === '1'}
      onNavigate={(path) => router.push(path)}
    />
  );
};

// 회원가입 직후 재인증하는 기존 E2E 흐름도 유지한다.
export default withoutAuth(LoginPage, { redirectAuthenticated: false });
