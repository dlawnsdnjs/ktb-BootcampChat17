export const API_REQUEST_METRICS = {
  HEALTH_CHECK: 'ktb-chat.api.health.duration',
  LOGIN: 'ktb-chat.api.auth.login.duration',
  REGISTER: 'ktb-chat.api.auth.register.duration',
};

export const UI_METRICS = {
  REGISTER_REDIRECT: 'ktb-chat.ui.auth.register-redirect.duration',
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
    } catch {}
  }

  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent('ktb-chat:api-request-metric', { detail: metric }));
  }
};

export const measureDuration = async (name, task) => {
  const startedAt = typeof performance === 'undefined' ? 0 : performance.now();

  try {
    const result = await task();
    recordRequestMetric(name, startedAt, result, null);
    return result;
  } catch (error) {
    recordRequestMetric(name, startedAt, null, error);
    throw error;
  }
};

export const measureApiRequest = (name, request) => measureDuration(name, request);
