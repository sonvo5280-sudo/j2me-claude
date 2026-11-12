import { useEffect, useState } from 'react';

/**
 * Strip shown when the link to the server has been down for a few seconds. It also works out WHY, because
 * "reconnecting…" alone does not tell a dead server from a dead Wi-Fi: the device offline, the server not
 * answering at all, or the server fine but the live game connection (WebSocket) dropped.
 */
export default function ConnectionBanner({ downSince }) {
  const [now, setNow] = useState(Date.now());
  const [reason, setReason] = useState('checking'); // checking | offline | server | link

  useEffect(() => {
    if (!downSince) return undefined;
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, [downSince]);

  const shown = downSince && now - downSince > 3000;

  useEffect(() => {
    if (!shown) return undefined;
    let dead = false;
    const probe = async () => {
      if (typeof navigator !== 'undefined' && navigator.onLine === false) return setReason('offline');
      try {
        const ctl = new AbortController();
        const t = setTimeout(() => ctl.abort(), 4000);
        const r = await fetch('/api/health', { cache: 'no-store', signal: ctl.signal });
        clearTimeout(t);
        if (!dead) setReason(r.ok ? 'link' : 'server');
      } catch {
        if (!dead) setReason('server');
      }
    };
    probe();
    const t = setInterval(probe, 3000);
    return () => { dead = true; clearInterval(t); };
  }, [shown]);

  if (!shown) return null;
  const secs = Math.round((now - downSince) / 1000);
  const text = {
    checking: 'Đang kiểm tra kết nối…',
    offline: 'Thiết bị của bạn đang không có mạng. Game vẫn chạy trên máy chủ và sẽ tự nối lại khi có mạng.',
    server: 'Không liên lạc được với máy chủ (máy chủ tắt, đang khởi động lại, hoặc đường truyền bị chặn). '
      + 'Nếu máy chủ khởi động lại thì các phiên đang chạy sẽ mất.',
    link: 'Máy chủ vẫn hoạt động nhưng kết nối trực tiếp tới game bị ngắt. Đang thử nối lại…',
  }[reason];

  return (
    <div role="alert" className="bg-red-900/95 px-3 py-1.5 text-xs text-red-50">
      <span className="font-medium">Mất kết nối ({secs}s).</span> {text}
    </div>
  );
}
