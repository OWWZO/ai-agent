export function loadCachedSessionFiles<T>(
  sessionId: string,
  cache: Map<string, T>,
  requests: Map<string, Promise<T>>,
  load: (sessionId: string) => Promise<T>
): Promise<T> {
  if (cache.has(sessionId)) {
    return Promise.resolve(cache.get(sessionId)!);
  }
  const inFlight = requests.get(sessionId);
  if (inFlight) {
    return inFlight;
  }

  const request = load(sessionId)
    .then((value) => {
      cache.set(sessionId, value);
      return value;
    })
    .finally(() => {
      requests.delete(sessionId);
    });
  requests.set(sessionId, request);
  return request;
}
