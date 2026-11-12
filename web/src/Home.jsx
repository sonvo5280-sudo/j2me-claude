import { useCallback, useEffect, useRef, useState } from 'react';
import { api, prettyName } from './api.js';

const size = (b) => (b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
const TILES = ['bg-sky-600', 'bg-emerald-600', 'bg-amber-600', 'bg-rose-600', 'bg-violet-600', 'bg-teal-600'];
const tileOf = (name) => TILES[[...name].reduce((a, c) => a + c.charCodeAt(0), 0) % TILES.length];

export default function Home({ go }) {
  const [games, setGames] = useState(null);
  const [sessions, setSessions] = useState([]);
  const [error, setError] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState('');
  const [over, setOver] = useState(false);
  const fileRef = useRef(null);

  const refresh = useCallback(async () => {
    try {
      const [g, s] = await Promise.all([api.games(), api.sessions()]);
      setGames(g);
      setSessions(s);
    } catch {
      setError('Không kết nối được máy chủ');
    }
  }, []);

  useEffect(() => {
    refresh();
    const t = setInterval(refresh, 3000);
    return () => clearInterval(t);
  }, [refresh]);

  const running = (name) => sessions.find((s) => s.game === name && s.status === 'running');

  const play = async (name) => {
    setError('');
    const have = running(name);
    if (have) return go(`/play/${have.id}`);
    setBusy(name);
    try {
      const s = await api.start(name);
      go(`/play/${s.id}`);
    } catch (e) {
      setError(e.status === 429 ? 'Đang chạy quá nhiều game, hãy đóng bớt rồi thử lại' : e.message);
    } finally {
      setBusy('');
    }
  };

  const upload = async (files) => {
    const list = [...files].filter((f) => /\.jar$/i.test(f.name));
    if (!list.length) return setError('Chỉ nhận file .jar');
    setError('');
    setNote('');
    setBusy('upload');
    const done = [];
    for (const f of list) {
      try {
        await api.upload(f);
        done.push(f.name);
      } catch (e) {
        setError(`${f.name}: ${e.message}`);
      }
    }
    setBusy('');
    if (done.length) setNote(`Đã tải lên ${done.length} game`);
    refresh();
  };

  return (
    <div className="mx-auto min-h-full max-w-3xl px-4 py-8 sm:py-12">
      <header className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight">Game J2ME</h1>
        <p className="mt-1 text-sm text-slate-400">
          Game chạy trên máy chủ, đóng trình duyệt vẫn chạy tiếp. Mở lại bấm “Tiếp tục” để vào đúng chỗ đang chơi.
        </p>
      </header>

      <div
        onDragOver={(e) => { e.preventDefault(); setOver(true); }}
        onDragLeave={() => setOver(false)}
        onDrop={(e) => { e.preventDefault(); setOver(false); upload(e.dataTransfer.files); }}
        onClick={() => fileRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && fileRef.current?.click()}
        className={`mb-8 flex cursor-pointer flex-col items-center gap-1 rounded-2xl border-2 border-dashed px-4 py-8 text-center transition-colors ${
          over ? 'border-sky-400 bg-sky-500/10' : 'border-slate-700 hover:border-slate-500 hover:bg-slate-900'
        }`}
      >
        <span className="text-sm font-medium">
          {busy === 'upload' ? 'Đang tải lên…' : 'Kéo thả file .jar vào đây, hoặc bấm để chọn'}
        </span>
        <span className="text-xs text-slate-500">Chọn được nhiều file cùng lúc, tối đa 30 MB mỗi file</span>
        <input
          ref={fileRef}
          type="file"
          accept=".jar"
          multiple
          className="hidden"
          onChange={(e) => { upload(e.target.files); e.target.value = ''; }}
        />
      </div>

      {error && <p className="mb-4 rounded-lg bg-red-500/10 px-3 py-2 text-sm text-red-300">{error}</p>}
      {note && !error && <p className="mb-4 rounded-lg bg-emerald-500/10 px-3 py-2 text-sm text-emerald-300">{note}</p>}

      <h2 className="mb-3 text-sm font-medium text-slate-400">
        Danh sách game{games ? ` (${games.length})` : ''}
      </h2>

      {games && games.length === 0 && <p className="text-sm text-slate-500">Chưa có game nào. Hãy tải một file .jar lên.</p>}

      <ul className="grid gap-3 sm:grid-cols-2">
        {(games || []).map((g) => {
          const live = running(g.name);
          const label = prettyName(g.name);
          return (
            <li key={g.name} className="flex items-center gap-3 rounded-2xl border border-slate-800 bg-slate-900/60 p-3">
              <div className={`flex h-14 w-14 shrink-0 items-center justify-center rounded-xl text-xl font-semibold ${tileOf(g.name)}`}>
                {label.slice(0, 2).toUpperCase()}
              </div>
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium">{label}</div>
                <div className="flex items-center gap-2 text-xs text-slate-400">
                  <span>{size(g.size)}</span>
                  {live && (
                    <span className="flex items-center gap-1 text-emerald-400">
                      <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> đang chạy
                    </span>
                  )}
                </div>
              </div>
              <button
                onClick={() => play(g.name)}
                disabled={busy === g.name}
                className={`shrink-0 rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-60 ${
                  live ? 'bg-emerald-600 hover:bg-emerald-500' : 'bg-sky-600 hover:bg-sky-500'
                }`}
              >
                {busy === g.name ? 'Đang mở…' : live ? 'Tiếp tục' : 'Chơi'}
              </button>
            </li>
          );
        })}
      </ul>

      <p className="mt-10 text-center text-xs text-slate-600">
        <button onClick={() => go('/status')} className="underline-offset-2 hover:text-slate-400 hover:underline">Trạng thái máy chủ</button>
      </p>
    </div>
  );
}
