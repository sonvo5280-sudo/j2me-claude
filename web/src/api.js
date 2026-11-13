async function call(url, opts) {
  const r = await fetch(url, opts);
  const text = await r.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  if (!r.ok) {
    throw Object.assign(new Error(body?.error || `Máy chủ trả về lỗi HTTP ${r.status}${text && !body ? `: ${text.slice(0, 120)}` : ''}`), { status: r.status });
  }
  if (body === null || typeof body !== 'object') {
    // e.g. a proxy in front of the server answering 200 with a web page instead of the API's JSON
    throw Object.assign(
      new Error(`Phản hồi không hợp lệ từ ${url} (HTTP ${r.status}, ${r.headers.get('content-type') || 'không rõ kiểu'}): ${text.slice(0, 120) || '(rỗng)'}`),
      { status: r.status },
    );
  }
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
