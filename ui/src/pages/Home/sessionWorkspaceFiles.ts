export function loadCachedSessionFiles<T>(
  sessionId: string,
  cache: Map<string, T>,
  requests: Map<string, Promise<T>>,
  load: (sessionId: string) => Promise<T>,
  force = false
): Promise<T> {
  if (!force && cache.has(sessionId)) {
    return Promise.resolve(cache.get(sessionId)!);
  }
  if (!force) {
    const inFlight = requests.get(sessionId);
    if (inFlight) {
      return inFlight;
    }
  }

  const request = load(sessionId)
    .then((value) => {
      if (requests.get(sessionId) === request) {
        cache.set(sessionId, value);
      }
      return value;
    })
    .finally(() => {
      if (requests.get(sessionId) === request) {
        requests.delete(sessionId);
      }
    });
  requests.set(sessionId, request);
  return request;
}
