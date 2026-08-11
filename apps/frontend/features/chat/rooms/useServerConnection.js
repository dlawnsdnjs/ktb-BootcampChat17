import { useState, useCallback } from 'react';
import axiosInstance from '@/services/axios';
import { HEALTH_TIMEOUT_MS } from '@/lib/api/client';
import { calculateFullJitterDelay } from '@/lib/retry/backoff';

export const CONNECTION_STATUS = {
  CHECKING: 'checking',
  CONNECTING: 'connecting',
  CONNECTED: 'connected',
  DISCONNECTED: 'disconnected',
  ERROR: 'error',
};

export const RETRY_CONFIG = {
  maxRetries: 1,
  baseDelay: 3000,
  maxDelay: 30000,
  backoffFactor: 2,
  reconnectInterval: 30000,
};

export const useServerConnection = () => {
  const [connectionStatus, setConnectionStatus] = useState(CONNECTION_STATUS.CHECKING);
  const [retryCount, setRetryCount] = useState(0);
  const [isRetrying, setIsRetrying] = useState(false);

  const getRetryDelay = useCallback((count) => {
    return calculateFullJitterDelay({
      attempt: count,
      baseDelay: RETRY_CONFIG.baseDelay,
      maxDelay: RETRY_CONFIG.maxDelay,
      backoffFactor: RETRY_CONFIG.backoffFactor,
    });
  }, []);

  const attemptConnection = useCallback(async (retryAttempt = 0) => {
    try {
      setConnectionStatus(CONNECTION_STATUS.CONNECTING);

      const response = await axiosInstance.get('/api/health', {
        timeout: HEALTH_TIMEOUT_MS,
      });

      if (response?.status === 401) {
        setIsRetrying(false);
        setConnectionStatus(CONNECTION_STATUS.ERROR);
        throw new Error('AUTH_EXPIRED');
      }

      const isConnected =
        response?.data?.status === 'ok' && response?.status === 200;

      if (isConnected) {
        setConnectionStatus(CONNECTION_STATUS.CONNECTED);
        setRetryCount(0);
        setIsRetrying(false);
        return true;
      }

      throw new Error('Server not ready');
    } catch (error) {
      if (error.response?.status === 401 || error.message === 'AUTH_EXPIRED') {
        setIsRetrying(false);
        setConnectionStatus(CONNECTION_STATUS.ERROR);
        throw new Error('AUTH_EXPIRED');
      }

      if (!error.response && retryAttempt < RETRY_CONFIG.maxRetries) {
        const delay = getRetryDelay(retryAttempt);
        setRetryCount(retryAttempt + 1);
        setIsRetrying(true);
        await new Promise((resolve) => setTimeout(resolve, delay));
        return attemptConnection(retryAttempt + 1);
      }

      setIsRetrying(false);
      setConnectionStatus(CONNECTION_STATUS.ERROR);
      throw new Error('SERVER_UNREACHABLE');
    }
  }, [getRetryDelay]);

  return {
    connectionStatus,
    setConnectionStatus,
    retryCount,
    setRetryCount,
    isRetrying,
    setIsRetrying,
    getRetryDelay,
    attemptConnection,
  };
};

export default useServerConnection;
