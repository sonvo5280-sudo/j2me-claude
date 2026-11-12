import { useCallback, useEffect, useRef, useState } from 'react';
import { api, prettyName } from './api.js';
import Keypad from './Keypad.jsx';

// Physical keyboard -> J2ME key codes. Anything printable is sent as its own character code
// (letters, digits, space, symbols), so the game receives exactly what was typed.
const SPECIAL = {
  ArrowUp: -1, ArrowDown: -2, ArrowLeft: -3, ArrowRight: -4,
  Enter: -5, NumpadEnter: -5, F1: -6, F2: -7, Backspace: -8,
};
const BACKSPACE = -8;
const SENTINEL = ' '; // the hidden phone-keyboard field always holds one space, so Backspace is detectable

function Btn({ onClick, label, children, className = '', disabled }) {
  return (
    <button
      type="button"
      onClick={onClick}
      onMouseDown={(e) => e.preventDefault()} // don't steal focus from the phone-keyboard input
      disabled={disabled}
      title={label}
      aria-label={label}
      className={`inline-flex h-9 items-center justify-center gap-1.5 rounded-lg px-3 text-sm hover:bg-slate-800 disabled:opacity-50 ${className}`}
    >
      {children}
    </button>
  );
}

export default function Control({ id, go }) {
  const [info, setInfo] = useState(null);
  const [phase, setPhase] = useState('connecting'); // connecting | live | ended | missing
  const [confirm, setConfirm] = useState(''); // '' | 'reset' | 'close'
  const [busy, setBusy] = useState(false);
  const [imeOn, setImeOn] = useState(false); // phone's own on-screen keyboard is open
  const imeRef = useRef(null);
  const canvasRef = useRef(null);
  const boxRef = useRef(null);
  const wsRef = useRef(null);
  const held = useRef(new Map()); // code -> sources currently holding it (keyboard, keypad, phone keyboard)
  const kbDown = useRef(new Map()); // physical e.code -> code that was sent on keydown
  const sizeRef = useRef({ w: 240, h: 320 });

  // ---- layout: fit the canvas into the free area (no bezel) ----
  const fit = useCallback(() => {
    const box = boxRef.current, c = canvasRef.current;
    if (!box || !c) return;
    const { w, h } = sizeRef.current;
    const s = Math.min(box.clientWidth / w, box.clientHeight / h);
    c.style.width = `${Math.floor(w * s)}px`;
    c.style.height = `${Math.floor(h * s)}px`;
  }, []);

  useEffect(() => {
    const ro = new ResizeObserver(fit);
    if (boxRef.current) ro.observe(boxRef.current);
    return () => ro.disconnect();
  }, [fit]);

  // ---- session info ----
  useEffect(() => {
    let dead = false;
    api.session(id)
      .then((s) => { if (!dead) { setInfo(s); if (s.status !== 'running') setPhase('ended'); } })
      .catch((e) => { if (!dead && e.status === 404) setPhase('missing'); });
    return () => { dead = true; };
  }, [id]);

  // ---- websocket with reconnect; closing it never stops the game ----
  useEffect(() => {
    let closed = false, retry = 0, timer;
    const draw = async (blob) => {
      const c = canvasRef.current;
      if (!c) return;
      try {
        const bmp = await createImageBitmap(blob);
        if (c.width !== bmp.width || c.height !== bmp.height) {
          c.width = bmp.width;
          c.height = bmp.height;
          sizeRef.current = { w: bmp.width, h: bmp.height };
          fit();
        }
        c.getContext('2d').drawImage(bmp, 0, 0);
        bmp.close();
      } catch { /* bad frame */ }
    };
    const open = () => {
      const proto = location.protocol === 'https:' ? 'wss' : 'ws';
      const ws = new WebSocket(`${proto}://${location.host}/ws?session=${id}`);
      ws.binaryType = 'blob';
      wsRef.current = ws;
      ws.onopen = () => { retry = 0; setPhase('live'); };
      ws.onmessage = (e) => { if (e.data instanceof Blob) draw(e.data); };
      ws.onclose = (e) => {
        if (closed) return;
        if (e.code === 4404 || e.code === 4000) {
          setPhase((p) => (e.code === 4404 && p !== 'ended' ? 'missing' : 'ended'));
          return;
        }
        setPhase('connecting');
        timer = setTimeout(open, Math.min(3000, 300 * 2 ** retry++));
      };
    };
    open();
    return () => {
      closed = true;
      clearTimeout(timer);
      wsRef.current?.close();
    };
  }, [id, fit]);

  // A key is "down" for the game while at least one source holds it. Keypad, physical keyboard and the phone
  // keyboard are separate sources, so releasing one never cuts off another that is still holding the same key.
  const press = useCallback((src, isDown, code) => {
    const ws = wsRef.current;
    if (ws?.readyState !== 1) return;
    let set = held.current.get(code);
    if (isDown) {
      if (!set) held.current.set(code, (set = new Set()));
      const first = set.size === 0;
      set.add(src);
      if (first) ws.send(JSON.stringify({ t: 'key', down: true, code }));
    } else if (set?.delete(src) && set.size === 0) {
      held.current.delete(code);
      ws.send(JSON.stringify({ t: 'key', down: false, code }));
    }
  }, []);
  const padSend = useCallback((isDown, code) => press('pad', isDown, code), [press]);

  // ---- physical keyboard: straight to the game, no intermediate field ----
  useEffect(() => {
    const ime = () => imeRef.current;
    const codeOf = (e) => {
      if (e.metaKey || e.ctrlKey || e.altKey) return undefined; // leave browser shortcuts alone
      if (SPECIAL[e.key] !== undefined) return SPECIAL[e.key];
      if (e.target === ime()) return undefined; // text from the phone keyboard goes through its input event
      return e.key.length === 1 ? e.key.charCodeAt(0) : undefined;
    };
    const dn = (e) => {
      const code = codeOf(e);
      if (code === undefined) return;
      e.preventDefault();
      if (e.repeat || kbDown.current.has(e.code)) return;
      kbDown.current.set(e.code, code);
      press('kb:' + e.code, true, code);
    };
    const up = (e) => {
      const code = kbDown.current.get(e.code); // release what was pressed, even if Shift changed the character
      if (code === undefined) return;
      e.preventDefault();
      kbDown.current.delete(e.code);
      press('kb:' + e.code, false, code);
    };
    const blur = () => {
      for (const [k, code] of kbDown.current) press('kb:' + k, false, code);
      kbDown.current.clear();
    };
    window.addEventListener('keydown', dn);
    window.addEventListener('keyup', up);
    window.addEventListener('blur', blur);
    return () => {
      window.removeEventListener('keydown', dn);
      window.removeEventListener('keyup', up);
      window.removeEventListener('blur', blur);
      blur();
    };
  }, [press]);

  // ---- touch / mouse on the screen ----
  const pointer = (type) => (e) => {
    const c = canvasRef.current;
    const ws = wsRef.current;
    if (!c || ws?.readyState !== 1) return;
    if (type === 2 && !e.currentTarget.hasPointerCapture(e.pointerId)) return;
    if (type === 0) e.currentTarget.setPointerCapture(e.pointerId);
    const r = c.getBoundingClientRect();
    const x = Math.round(((e.clientX - r.left) * c.width) / r.width);
    const y = Math.round(((e.clientY - r.top) * c.height) / r.height);
    ws.send(JSON.stringify({ t: 'ptr', type, x, y }));
  };

  // ---- phone keyboard: the OS only opens its keyboard for a focused field, so an invisible one is used;
  // whatever is typed is forwarded to the game immediately and the field is reset (nothing is buffered or shown).
  const tap = useCallback((code) => { press('ime', true, code); press('ime', false, code); }, [press]);

  const flushIme = useCallback(() => {
    const el = imeRef.current;
    if (!el) return;
    const now = el.value;
    let i = 0;
    while (i < SENTINEL.length && i < now.length && SENTINEL[i] === now[i]) i++;
    for (let n = SENTINEL.length - i; n > 0; n--) tap(BACKSPACE);
    for (const ch of now.slice(i)) tap(ch.charCodeAt(0));
    el.value = SENTINEL;
  }, [tap]);

  const toggleIme = () => {
    const el = imeRef.current;
    if (!el) return;
    if (document.activeElement === el) el.blur();
    else { el.value = SENTINEL; el.focus(); }
  };

  const start = async (game) => {
    const s = await api.start(game);
    go(`/play/${s.id}`, { replace: true });
  };

  const reset = async () => {
    setConfirm('');
    if (!info?.game) return;
    setBusy(true);
    try {
      await api.stop(id).catch(() => {});
      await start(info.game);
    } catch (e) {
      setBusy(false);
      alert(`Không khởi động lại được: ${e.message}`);
    }
  };

  const close = async () => {
    setConfirm('');
    setBusy(true);
    await api.stop(id).catch(() => {});
    go('/');
  };

  // a confirm prompt that dismisses itself
  useEffect(() => {
    if (!confirm) return;
    const t = setTimeout(() => setConfirm(''), 3500);
    return () => clearTimeout(t);
  }, [confirm]);

  const title = prettyName(info?.game);
  const gone = phase === 'ended' || phase === 'missing';

  return (
    <div className="flex h-full flex-col bg-black">
      <header className="flex shrink-0 items-center gap-1 border-b border-slate-800 bg-slate-950 px-2 py-1.5">
        <Btn onClick={() => go('/')} label="Về trang chủ (game vẫn chạy)">← <span className="hidden sm:inline">Trang chủ</span></Btn>
        <h1 className="mx-2 min-w-0 flex-1 truncate text-center text-sm font-medium">{title}</h1>

        <span className="hidden pointer-coarse:inline-flex">
          <Btn
            onClick={toggleIme}
            label="Bật/tắt bàn phím của điện thoại"
            className={imeOn ? 'bg-sky-700 hover:bg-sky-600' : ''}
          >
            ⌨<span className="hidden min-[420px]:inline"> Bàn phím</span>
          </Btn>
        </span>

        {confirm === 'reset' ? (
          <Btn onClick={reset} label="Xác nhận khởi động lại" className="bg-amber-600 hover:bg-amber-500">Chắc chắn?</Btn>
        ) : (
          <Btn onClick={() => setConfirm('reset')} label="Khởi động lại game" disabled={busy || !info?.game}>
            ↻ <span className="hidden sm:inline">Reset</span>
          </Btn>
        )}

        {confirm === 'close' ? (
          <Btn onClick={close} label="Xác nhận đóng game" className="bg-red-600 hover:bg-red-500">Đóng game?</Btn>
        ) : (
          <Btn onClick={() => setConfirm('close')} label="Đóng game (dừng hẳn)" disabled={busy} className="text-red-300">
            ✕ <span className="hidden sm:inline">Đóng</span>
          </Btn>
        )}
      </header>

      <main ref={boxRef} className="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden">
        <canvas
          ref={canvasRef}
          width={240}
          height={320}
          onPointerDown={pointer(0)}
          onPointerUp={pointer(1)}
          onPointerCancel={pointer(1)}
          onPointerMove={pointer(2)}
          onContextMenu={(e) => e.preventDefault()}
          className={`keybtn cursor-default bg-black ${gone ? 'opacity-30' : ''}`}
          style={{ touchAction: 'none', cursor: 'default' }}
        />
        {phase === 'connecting' && !gone && (
          <div className="pointer-events-none absolute inset-x-0 top-3 text-center text-xs text-slate-400">Đang kết nối…</div>
        )}
        {gone && (
          <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 p-6 text-center">
            <p className="text-slate-200">
              {phase === 'missing' ? 'Không tìm thấy phiên chơi này.' : 'Game đã dừng.'}
            </p>
            <div className="flex gap-2">
              <button onClick={() => go('/')} className="rounded-lg bg-slate-800 px-4 py-2 text-sm hover:bg-slate-700">Về trang chủ</button>
              {info?.game && (
                <button
                  onClick={() => start(info.game).catch((e) => alert(e.message))}
                  className="rounded-lg bg-sky-600 px-4 py-2 text-sm hover:bg-sky-500"
                >
                  Chơi lại
                </button>
              )}
            </div>
          </div>
        )}
      </main>

      {!gone && (
        <div className="shrink-0 border-t border-slate-800 bg-slate-950">
          <Keypad send={padSend} />
        </div>
      )}

      {/* invisible field that only exists to open the phone's keyboard */}
      <input
        ref={imeRef}
        aria-hidden="true"
        tabIndex={-1}
        autoCapitalize="off"
        autoCorrect="off"
        autoComplete="off"
        spellCheck={false}
        enterKeyHint="done"
        onFocus={() => setImeOn(true)}
        onBlur={() => setImeOn(false)}
        onInput={(e) => { if (!e.nativeEvent.isComposing) flushIme(); }}
        onCompositionEnd={flushIme}
        className="pointer-events-none fixed bottom-0 left-0 h-px w-px opacity-0"
        style={{ fontSize: 16 }}
      />
    </div>
  );
}
