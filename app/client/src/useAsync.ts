// Small data-loading hook shared by the screens. Detects the server's MODULE_DISABLED answer so a screen can show
// the maintenance notice (FR-5; SD-4 "isEnabled(moduleId) -> false -> show maintenance notice").
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from './api';

export function useAsync<T>(fn: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);

  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await fn());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'ERROR', String(e)));
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    reload();
  }, [reload]);

  return { data, setData, error, loading, reload };
}

/** Runs an action, returning its error message (or null). */
export async function attempt(action: () => Promise<unknown>): Promise<string | null> {
  try {
    await action();
    return null;
  } catch (e) {
    return e instanceof Error ? e.message : String(e);
  }
}
