import { afterEach, describe, expect, it, vi } from 'vitest';
import { API_REQUEST_METRICS, measureApiRequest } from '../requestMetrics';

describe('request metrics', () => {
  afterEach(() => {
    performance.clearMeasures();
  });

  it('records health and login timings under separate metric names', async () => {
    await measureApiRequest(API_REQUEST_METRICS.HEALTH_CHECK, async () => ({ status: 200 }));
    await measureApiRequest(API_REQUEST_METRICS.LOGIN, async () => ({ status: 200 }));

    expect(performance.getEntriesByName(API_REQUEST_METRICS.HEALTH_CHECK)).toHaveLength(1);
    expect(performance.getEntriesByName(API_REQUEST_METRICS.LOGIN)).toHaveLength(1);
  });

  it('records failed request status and preserves the original error', async () => {
    const error = new Error('request failed');
    error.response = { status: 503 };
    const metricListener = vi.fn();
    window.addEventListener('ktb-chat:api-request-metric', metricListener);

    await expect(
      measureApiRequest(API_REQUEST_METRICS.HEALTH_CHECK, async () => {
        throw error;
      })
    ).rejects.toBe(error);

    expect(metricListener).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: expect.objectContaining({
          name: API_REQUEST_METRICS.HEALTH_CHECK,
          status: 503,
          success: false,
        }),
      })
    );
    window.removeEventListener('ktb-chat:api-request-metric', metricListener);
  });
});
