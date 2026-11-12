import { useState } from 'react';

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}

function Detail({ e, hideTitle }) {
  const [hint, ...rest] = e.detail.startsWith('Gợi ý:') ? e.detail.split(/\n\n/) : [null, e.detail];
  const body = rest.join('\n\n');
  return (
    <div className="mb-3 text-left">
      {!hideTitle && <div className={`text-sm font-medium ${e.level === 'fatal' ? 'text-red-300' : 'text-amber-300'}`}>{e.title}</div>}
      {hint && <p className="mt-1 text-sm text-slate-200">{hint}</p>}
      {body && (
        <pre className="mt-2 max-h-56 overflow-auto whitespace-pre rounded-lg bg-slate-950 p-3 font-mono text-xs leading-relaxed text-slate-300">
          {body}
        </pre>
      )}
    </div>
  );
}

const asText = (errors) => errors.map((e) => `[${e.level}] ${e.title}\n${e.detail}`).join('\n\n');

/** Shown when the game could not start or stopped. Explains why instead of just saying "stopped". */
export function FatalOverlay({ id, info, phase, errors, go, onRetry }) {
  const [log, setLog] = useState(null);
  const [copied, setCopied] = useState('');
  const fatal = errors.filter((e) => e.level === 'fatal');
  const shown = fatal.length ? [...fatal, ...errors.filter((e) => e.level !== 'fatal')] : []; // the cause first, warnings after

  const loadLog = async () => {
    try {
      const lines = await (await fetch(`/api/sessions/${id}/log`)).json();
      setLog(lines.filter((l) => !l.startsWith('ERROR ') && !l.startsWith('STATE ')).slice(-80).join('\n') || '(log trống)');
    } catch {
      setLog('Không đọc được log.');
    }
  };

  const heading = phase === 'missing'
    ? 'Không tìm thấy phiên chơi này.'
    : fatal.length ? 'Game không khởi chạy được' : 'Game đã dừng.';

  return (
    <div className="absolute inset-0 overflow-auto bg-black p-4">
      <div className="mx-auto flex min-h-full max-w-xl flex-col justify-center gap-2 py-4">
        <h2 className="text-center text-lg font-semibold text-slate-100">{heading}</h2>
        {!fatal.length && info?.exitCode ? (
          <p className="text-center text-sm text-slate-400">Mã thoát: {String(info.exitCode)}</p>
        ) : null}
        {shown.map((e, i) => <Detail key={i} e={e} hideTitle={e.title === heading} />)}

        {log !== null && (
          <pre className="max-h-56 overflow-auto whitespace-pre rounded-lg bg-slate-950 p-3 font-mono text-xs text-slate-400">
            {log}
          </pre>
        )}

        <div className="mt-1 flex flex-wrap justify-center gap-2">
          <button onClick={() => go('/')} className="rounded-lg bg-slate-800 px-4 py-2 text-sm hover:bg-slate-700">Về trang chủ</button>
          {info?.game && (
            <button onClick={onRetry} className="rounded-lg bg-sky-600 px-4 py-2 text-sm hover:bg-sky-500">Chơi lại</button>
          )}
          {info && <button onClick={loadLog} className="rounded-lg border border-slate-700 px-4 py-2 text-sm hover:bg-slate-800">Xem log</button>}
          {shown.length > 0 && (
            <button
              onClick={async () => setCopied((await copy(`${info?.game || ''}\n\n${asText(shown)}${log ? '\n\nLOG\n' + log : ''}`)) ? 'Đã sao chép' : 'Không sao chép được')}
              className="rounded-lg border border-slate-700 px-4 py-2 text-sm hover:bg-slate-800"
            >
              {copied || 'Sao chép lỗi'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

/** Non-fatal problems (a game thread died, unsupported API used…): a strip on top that opens the details. */
export function WarnBanner({ warns }) {
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(0); // how many warnings were already dismissed
  if (warns.length <= hidden) return null;
  const last = warns[warns.length - 1];
  return (
    <div className="absolute inset-x-0 top-0 z-10 bg-amber-900/95 text-amber-50 shadow-lg">
      <div className="flex items-center gap-2 px-3 py-1.5 text-xs">
        <span aria-hidden>⚠</span>
        <button onClick={() => setOpen((o) => !o)} className="min-w-0 flex-1 truncate text-left underline-offset-2 hover:underline">
          {last.title}{warns.length > 1 ? ` (+${warns.length - 1})` : ''}
        </button>
        <button onClick={() => setOpen((o) => !o)} className="shrink-0 rounded px-2 py-0.5 hover:bg-amber-800">{open ? 'Thu gọn' : 'Chi tiết'}</button>
        <button onClick={() => { setHidden(warns.length); setOpen(false); }} aria-label="Ẩn cảnh báo" className="shrink-0 rounded px-2 py-0.5 hover:bg-amber-800">✕</button>
      </div>
      {open && (
        <div className="max-h-[60vh] overflow-auto border-t border-amber-800 bg-slate-900 p-3 text-slate-100">
          {warns.map((e, i) => <Detail key={i} e={e} />)}
        </div>
      )}
    </div>
  );
}
