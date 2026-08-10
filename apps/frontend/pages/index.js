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
      onNavigate={(path) => router.push(path)}
    />
  );
};

export default withoutAuth(LoginPage);
