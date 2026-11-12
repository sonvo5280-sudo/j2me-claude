import { useCallback, useEffect, useState } from 'react';

/** Minimal History-API router: returns [pathname, go(path, {replace})]. */
export function useRoute() {
  const [path, setPath] = useState(() => window.location.pathname);

  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener('popstate', onPop);
    return () => window.removeEventListener('popstate', onPop);
  }, []);

  const go = useCallback((to, { replace = false } = {}) => {
    if (to === window.location.pathname) return;
    window.history[replace ? 'replaceState' : 'pushState'](null, '', to);
    setPath(to);
  }, []);

  return [path, go];
}
