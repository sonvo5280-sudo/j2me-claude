// Keep-alive test: the game must keep running after the viewer disconnects.
// Usage: BASE=http://127.0.0.1:8099 node test.mjs
import WebSocket from 'ws';

const BASE = process.env.BASE || 'http://127.0.0.1:8080';
const WS_BASE = BASE.replace(/^http/, 'ws');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const api = async (method, p) => (await fetch(BASE + p, { method })).json();

let failed = 0;
const check = (name, ok, extra = '') => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? '  ' + extra : ''}`);
  if (!ok) failed++;
};

function connect(id) {
  return new Promise((resolve, reject) => {
    const t0 = Date.now();
    const ws = new WebSocket(`${WS_BASE}/ws?session=${id}`);
    ws.once('message', (data, isBinary) => {
      resolve({ ws, firstFrameMs: Date.now() - t0, jpeg: isBinary && data[0] === 0xff && data[1] === 0xd8 });
    });
    ws.once('error', reject);
    ws.once('close', (code) => reject(new Error('closed ' + code)));
  });
}

const s = await api('POST', '/api/sessions');
check('create session', !!s.id, s.id);

// 1) attach, get frames, send keys
let c = await connect(s.id);
check('first frame is JPEG', c.jpeg, `${c.firstFrameMs}ms`);
for (let i = 0; i < 3; i++) {
  c.ws.send(JSON.stringify({ t: 'key', down: true, code: -5 }));
  c.ws.send(JSON.stringify({ t: 'key', down: false, code: -5 }));
}
await sleep(2300);
const before = await api('GET', `/api/sessions/${s.id}`);
check('keys reached the game', before.state.presses === 3, `presses=${before.state.presses}`);
check('viewer counted', before.viewers === 1);

// 2) close the "browser"
c.ws.close();
await sleep(500); // let in-flight frames drain
const atClose = await api('GET', `/api/sessions/${s.id}`);
await sleep(3500);
const away = await api('GET', `/api/sessions/${s.id}`);
check('session still running after disconnect', away.status === 'running');
check('game kept ticking while nobody watched',
  away.state.ticks >= atClose.state.ticks + 3,
  `ticks ${atClose.state.ticks} -> ${away.state.ticks}`);
check('worker stopped encoding frames while unwatched',
  away.framesReceived - atClose.framesReceived <= 2,
  `+${away.framesReceived - atClose.framesReceived} frames in 3.5s`);

// 3) come back
c = await connect(s.id);
check('reconnect gets a frame instantly', c.jpeg && c.firstFrameMs < 500, `${c.firstFrameMs}ms`);
await sleep(1200);
const back = await api('GET', `/api/sessions/${s.id}`);
check('frames flow again after reconnect', back.framesReceived > away.framesReceived + 5,
  `+${back.framesReceived - away.framesReceived}`);
c.ws.close();

await api('DELETE', `/api/sessions/${s.id}`);
console.log(failed ? `\n${failed} check(s) failed` : '\nall checks passed');
process.exit(failed ? 1 : 0);
