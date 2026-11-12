async function call(url, opts) {
  const r = await fetch(url, opts);
  let body = null;
  try { body = await r.json(); } catch { /* empty body */ }
  if (!r.ok) throw Object.assign(new Error(body?.error || `HTTP ${r.status}`), { status: r.status });
  return body;
}

export const api = {
  games: () => call('/api/games'),
  sessions: () => call('/api/sessions'),
  session: (id) => call(`/api/sessions/${id}`),
  start: (game) =>
    call('/api/sessions', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ game }),
    }),
  stop: (id) => call(`/api/sessions/${id}`, { method: 'DELETE' }),
  upload: (file) => call(`/api/games?name=${encodeURIComponent(file.name)}`, { method: 'POST', body: file }),
};

export const prettyName = (n) => (n || 'Game thử nghiệm').replace(/\.jar$/i, '').replace(/_+/g, ' ');
