export const calculateFullJitterDelay = ({
  attempt,
  baseDelay,
  maxDelay,
  backoffFactor = 2,
  random = Math.random,
}) => {
  const retryCap = Math.min(
    maxDelay,
    baseDelay * Math.pow(backoffFactor, attempt)
  );

  return Math.floor(random() * (retryCap + 1));
};
