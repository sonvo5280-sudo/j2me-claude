import { useRef } from 'react';

// J2ME key codes: arrows -1..-4, OK -5, soft keys -6/-7. No digits, * or # on purpose.
function Key({ code, send, className = '', label, children }) {
  const held = useRef(false);
  const up = () => {
    if (held.current) { held.current = false; send(false, code); }
  };
  return (
    <button
      type="button"
      aria-label={label}
      onPointerDown={(e) => {
        e.preventDefault();
        e.currentTarget.setPointerCapture(e.pointerId);
        if (!held.current) { held.current = true; send(true, code); }
      }}
      onPointerUp={up}
      onPointerCancel={up}
      onLostPointerCapture={up}
      onContextMenu={(e) => e.preventDefault()}
      className={`keybtn flex items-center justify-center bg-slate-800 text-slate-200 active:bg-sky-600 ${className}`}
    >
      {children}
    </button>
  );
}

export default function Keypad({ send }) {
  const arrow = 'h-14 w-14 text-xl pointer-fine:h-11 pointer-fine:w-11 pointer-fine:text-base';
  const soft = 'h-12 w-24 rounded-xl text-sm pointer-fine:h-10 sm:w-28';
  return (
    // left soft key | arrows + OK | right soft key, centred under the screen
    <div className="flex items-center justify-center gap-4 px-4 py-3 pointer-fine:py-2 sm:gap-8">
      <Key code={-6} send={send} label="Phím mềm trái" className={soft}>Mềm trái</Key>
      <div className="grid shrink-0 grid-cols-3 grid-rows-3 gap-1.5">
        <span />
        <Key code={-1} send={send} label="Lên" className={`${arrow} rounded-xl`}>▲</Key>
        <span />
        <Key code={-3} send={send} label="Trái" className={`${arrow} rounded-xl`}>◀</Key>
        <Key code={-5} send={send} label="OK" className={`${arrow} rounded-full bg-sky-700 text-sm font-semibold`}>OK</Key>
        <Key code={-4} send={send} label="Phải" className={`${arrow} rounded-xl`}>▶</Key>
        <span />
        <Key code={-2} send={send} label="Xuống" className={`${arrow} rounded-xl`}>▼</Key>
        <span />
      </div>
      <Key code={-7} send={send} label="Phím mềm phải" className={soft}>Mềm phải</Key>
    </div>
  );
}
