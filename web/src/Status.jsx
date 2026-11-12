import { useEffect, useState } from 'react';

const dur = (s) => (s >= 3600 ? `${Math.floor(s / 3600)} giờ ${Math.floor((s % 3600) / 60)} phút` : s >= 60 ? `${Math.floor(s / 60)} phút ${s % 60} giây` : `${s} giây`);
const at = (t) => new Date(t).toLocaleTimeString('vi-VN');

function Card({ label, children }) {
  return (
    <div className="rounded-2xl border border-slate-800 bg-slate-900/60 p-4">
      <div className="text-xs text-slate-400">{label}</div>
      <div className="mt-1 text-lg font-medium">{children}</div>
    </div>
  );
}

export default function Status({ go }) {
  const [h, setH] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    let dead = false;
    const load = async () => {
      try {
        const r = await fetch('/api/health', { cache: 'no-store' });
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        const body = await r.json();
        if (!dead) { setH(body); setErr(''); }
      } catch (e) {
        if (!dead) setErr(`Không đọc được trạng thái máy chủ (${e.message})`);
      }
    };
    load();
    const t = setInterval(load, 3000);
    return () => { dead = true; clearInterval(t); };
  }, []);

  const used = h ? h.memory.systemTotalMB - h.memory.systemFreeMB : 0;
  const memPct = h ? Math.round((used / h.memory.systemTotalMB) * 100) : 0;

  return (
    <div className="mx-auto min-h-full max-w-3xl px-4 py-8 sm:py-12">
      <header className="mb-6 flex items-center gap-3">
        <button onClick={() => go('/')} className="rounded-lg px-3 py-2 text-sm hover:bg-slate-800">← Trang chủ</button>
        <h1 className="text-xl font-semibold">Trạng thái máy chủ</h1>
      </header>

      {err && <p role="alert" className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-300">{err}</p>}
      {!h && !err && <p className="text-sm text-slate-400">Đang tải…</p>}

      {h && (
        <>
          <div className="mb-6 grid gap-3 sm:grid-cols-2">
            <Card label="Đã chạy được">{dur(h.uptimeSec)}</Card>
            <Card label="Phiên đang chạy">{h.sessions.running} / {h.sessions.max}</Card>
            <Card label="Bộ nhớ máy chủ">
              <span className={memPct > 90 ? 'text-red-300' : memPct > 75 ? 'text-amber-300' : ''}>{memPct}%</span>
              <span className="ml-2 text-sm font-normal text-slate-400">{h.memory.systemFreeMB} MB trống / {h.memory.systemTotalMB} MB</span>
            </Card>
            <Card label="Tải CPU (1 phút)">
              {h.cpu.load1}<span className="ml-2 text-sm font-normal text-slate-400">trên {h.cpu.cores} lõi</span>
            </Card>
          </div>

          <h2 className="mb-2 text-sm font-medium text-slate-400">Các phiên</h2>
          <ul className="mb-6 space-y-2">
            {h.sessions.list.length === 0 && <li className="text-sm text-slate-500">Chưa có phiên nào.</li>}
            {h.sessions.list.map((s) => (
              <li key={s.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-xl border border-slate-800 bg-slate-900/60 px-3 py-2 text-sm">
                <span className="font-mono text-xs text-slate-500">{s.id}</span>
                <span className="min-w-0 flex-1 truncate">{s.game || 'Game thử nghiệm'}</span>
                <span className={s.status === 'running' ? 'text-emerald-400' : 'text-slate-500'}>{s.status === 'running' ? 'đang chạy' : 'đã dừng'}</span>
                <span className="text-slate-400">{s.viewers} người xem</span>
                {s.rssMB != null && <span className="text-slate-400">{s.rssMB} MB RAM</span>}
                {s.errors > 0 && <span className="text-amber-300">{s.errors} lỗi/cảnh báo</span>}
              </li>
            ))}
          </ul>

          <h2 className="mb-2 text-sm font-medium text-slate-400">Lỗi gần đây của gateway</h2>
          {h.errors.length === 0 ? (
            <p className="text-sm text-emerald-400">Không có lỗi nào.</p>
          ) : (
            <ul className="space-y-2">
              {[...h.errors].reverse().map((e, i) => (
                <li key={i} className="rounded-xl border border-red-900/60 bg-red-950/30 p-3 text-sm">
                  <div className="font-medium text-red-300">{e.where} <span className="font-normal text-slate-500">lúc {at(e.at)}</span></div>
                  <div className="mt-1 break-words text-slate-200">{e.message}</div>
                  {e.stack && <pre className="mt-2 overflow-auto whitespace-pre rounded-lg bg-slate-950 p-2 font-mono text-xs text-slate-400">{e.stack}</pre>}
                </li>
              ))}
            </ul>
          )}
          <p className="mt-6 text-xs text-slate-500">
            Mạng cho game: {h.net === 'allowed' ? 'được phép' : 'tắt'} · Lõi: {h.cores.default} · Node {h.node}
          </p>
        </>
      )}
    </div>
  );
}
