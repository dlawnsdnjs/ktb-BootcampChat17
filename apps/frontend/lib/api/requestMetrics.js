export const API_REQUEST_METRICS = {
  HEALTH_CHECK: 'ktb-chat.api.health.duration',
  LOGIN: 'ktb-chat.api.auth.login.duration',
};

const getStatus = (result, error) => result?.status || error?.response?.status || 0;

const recordRequestMetric = (name, startedAt, result, error) => {
  if (typeof performance === 'undefined') return;

  const finishedAt = performance.now();
  const metric = {
    name,
    durationMs: Math.round((finishedAt - startedAt) * 100) / 100,
    status: getStatus(result, error),
    success: !error,
  };

  if (typeof performance.measure === 'function') {
    try {
      performance.measure(name, {
        start: startedAt,
        end: finishedAt,
      });
    } catch {
      // 일부 브라우저/테스트 환경에서는 사용자 정의 측정 옵션을 지원하지 않는다.
    }
  }

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent('ktb-chat:api-request-metric', { detail: metric }));
  }
};

/**
 * 요청별 체감 지연을 측정한다.
 * Health Check와 로그인 API는 호출 목적과 지표 이름을 분리해 기록한다.
 */
export const measureApiRequest = async (name, request) => {
  const startedAt = typeof performance === 'undefined' ? 0 : performance.now();

  try {
    const result = await request();
    recordRequestMetric(name, startedAt, result, null);
    return result;
  } catch (error) {
    recordRequestMetric(name, startedAt, null, error);
    throw error;
  }
};
