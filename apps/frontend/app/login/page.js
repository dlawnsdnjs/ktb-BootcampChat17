'use client';

import { Suspense, useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import LoginForm from '@/components/auth/LoginForm';

const LoadingState = () => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      height: '100vh',
      backgroundColor: 'var(--vapor-color-background)',
      color: 'var(--vapor-color-text-primary)',
    }}
  >
    <div>Loading...</div>
  </div>
);

function LoginPageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { isAuthenticated, isLoading, login } = useAuth();
  const initialAuthCheckCompleted = useRef(false);
  const redirectPath = searchParams.get('redirect') || '/chat';

  useEffect(() => {
    if (isLoading || initialAuthCheckCompleted.current) {
      return;
    }

    initialAuthCheckCompleted.current = true;

    if (isAuthenticated) {
      router.replace(redirectPath);
    }
  }, [isAuthenticated, isLoading, redirectPath, router]);

  if (isLoading || isAuthenticated) {
    return <LoadingState />;
  }

  return (
    <LoginForm
      login={login}
      redirect={redirectPath === '/chat' ? undefined : redirectPath}
      registered={searchParams.get('registered') === '1'}
      onNavigate={(path) => router.push(path)}
    />
  );
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingState />}>
      <LoginPageContent />
    </Suspense>
  );
}
