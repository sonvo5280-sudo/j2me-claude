import http from 'node:http';
import fs from 'node:fs';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { WebSocketServer } from 'ws';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 8080);
const BUILD = path.resolve(HERE, '../worker/build');
const ASM = path.resolve(HERE, '../worker/vendor/microemu/lib/asm-3.2.jar');
// Two MIDP cores share the package javax.microedition.*, so each one gets its own classpath.
const CORE_CP = {
  microemu: [path.join(BUILD, 'common'), path.join(BUILD, 'microemu'), ASM].join(path.delimiter),
  own: [path.join(BUILD, 'common'), path.join(BUILD, 'own')].join(path.delimiter),
};
const CORES = Object.keys(CORE_CP).filter((c) => fs.existsSync(path.join(BUILD, c)));
const DEFAULT_CORE = CORES.includes(process.env.CORE) ? process.env.CORE : CORES.includes('microemu') ? 'microemu' : CORES[0];
const GAMES_DIR = path.resolve(process.env.GAMES_DIR || path.join(HERE, '../games'));
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(HERE, '../data'));
const WEB_DIST = path.resolve(HERE, '../web/dist');
const MAX_SESSIONS = Number(process.env.MAX_SESSIONS || 5);
const ALLOW_NET = process.env.ALLOW_NET === '1'; // game network access, off unless the operator opts in
const MAX_UPLOAD = 30 * 1024 * 1024;

fs.mkdirSync(GAMES_DIR, { recursive: true });

/** @type {Map<string, any>} */
const sessions = new Map();

function listGames() {
  return fs
    .readdirSync(GAMES_DIR)
    .filter((n) => /\.jar$/i.test(n))
    .sort()
    .map((name) => ({ name, size: fs.statSync(path.join(GAMES_DIR, name)).size }));
}

function createSession(opts = {}) {
  const id = randomUUID().slice(0, 8);
  const workerArgs = [];
  let game = null;
  const core = CORES.includes(opts.core) ? opts.core : DEFAULT_CORE;
  if (!core) throw Object.assign(new Error('worker is not built (run worker/build.sh)'), { code: 500 });
  if (opts.game) {
    const name = path.basename(String(opts.game));
    if (!listGames().some((g) => g.name === name)) throw Object.assign(new Error('unknown game'), { code: 400 });
    game = name;
    const rms = path.join(DATA_DIR, 'rms', name.replace(/\.jar$/i, ''));
    fs.mkdirSync(rms, { recursive: true });
    workerArgs.push('--jar', path.join(GAMES_DIR, name), '--rms', rms, '--core', core);
    const w = Number(opts.w), h = Number(opts.h);
    if (w >= 96 && w <= 480 && h >= 96 && h <= 640) workerArgs.push('--w', String(w), '--h', String(h));
    if (ALLOW_NET && opts.net) workerArgs.push('--net', 'on');
    if (opts.touch === false) workerArgs.push('--touch', 'off');
  }
  const proc = spawn(
    'java',
    [
      '--add-exports', 'java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
      '-Xmx256m', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8',
      '-cp', CORE_CP[core], 'Worker', ...workerArgs,
    ],
    { stdio: ['pipe', 'pipe', 'pipe'] }
  );
  proc.stdin.on('error', () => {});
  const s = {
    id,
    game,
    core: game ? core : null,
    proc,
    status: 'running',
    createdAt: Date.now(),
    clients: new Set(),
    lastFrame: null,
    framesReceived: 0,
    framesSent: 0,
    state: {},
    log: [],
    errors: [], // {level: 'fatal'|'warn', title, detail}
    exitCode: null,
  };

  // stdout: [uint32 BE length][JPEG] frames
  let buf = Buffer.alloc(0);
  proc.stdout.on('data', (chunk) => {
    buf = Buffer.concat([buf, chunk]);
    while (buf.length >= 4) {
      const len = buf.readUInt32BE(0);
      if (buf.length < 4 + len) break;
      const frame = Buffer.from(buf.subarray(4, 4 + len));
      buf = buf.subarray(4 + len);
      s.lastFrame = frame;
      s.framesReceived++;
      for (const ws of s.clients) {
        if (ws.readyState === 1) {
          ws.send(frame);
          s.framesSent++;
        }
      }
    }
  });

  // stderr: "STATE k=v k=v" lines are metrics, everything else is the game/worker log
  let errBuf = '';
  proc.stderr.on('data', (chunk) => {
    errBuf += chunk.toString();
    let i;
    while ((i = errBuf.indexOf('\n')) >= 0) {
      const line = errBuf.slice(0, i).replace(/\r$/, '');
      errBuf = errBuf.slice(i + 1);
      if (line.startsWith('STATE ')) {
        for (const kv of line.slice(6).trim().split(' ')) {
          const [k, v] = kv.split('=');
          s.state[k] = Number(v);
        }
      } else if (line.startsWith('ERROR ')) {
        // "ERROR fatal|warn <base64 of 'title\\ndetail'>" written by the worker
        const m = line.match(/^ERROR (fatal|warn) (\S+)$/);
        if (m) {
          const text = Buffer.from(m[2], 'base64').toString('utf8');
          const nl = text.indexOf('\n');
          if (s.errors.length < 20) s.errors.push({ level: m[1], title: nl < 0 ? text : text.slice(0, nl), detail: nl < 0 ? '' : text.slice(nl + 1) });
        }
        s.log.push(line.length > 300 ? line.slice(0, 300) + '…' : line);
        if (s.log.length > 300) s.log.shift();
      } else if (line.trim()) {
        s.log.push(line);
        if (s.log.length > 300) s.log.shift();
        if (process.env.VERBOSE) console.log(`[${id}] ${line}`);
      }
    }
  });

  // java missing / not executable: without this handler the whole gateway would crash
  proc.on('error', (e) => {
    s.status = 'stopped';
    s.errors.push({
      level: 'fatal',
      title: 'Không chạy được Java trên máy chủ',
      detail: `${e.message}\nGợi ý: máy chủ cần JDK/JRE 21 có lệnh "java" trong PATH.`,
    });
    for (const ws of s.clients) ws.close(4000, 'session ended');
    s.clients.clear();
  });

  proc.on('exit', (code, signal) => {
    s.exitCode = code ?? signal;
    s.status = 'stopped';
    if ((code !== 0 || signal) && !s.errors.some((x) => x.level === 'fatal')) {
      // the JVM died without explaining itself (bad classpath, OOM kill, crash): hand over the log tail
      const tail = s.log.filter((l) => !l.startsWith('ERROR ')).slice(-25).join('\n');
      s.errors.push({
        level: 'fatal',
        title: signal ? `Tiến trình giả lập bị dừng (${signal})` : `Tiến trình giả lập thoát bất thường (mã ${code})`,
        detail: (signal === 'SIGKILL' ? 'Gợi ý: có thể máy chủ hết bộ nhớ và hệ điều hành đã dừng tiến trình.\n\n' : '') +
          (tail ? 'Những dòng log cuối:\n' + tail : 'Không có log nào.'),
      });
    }
    for (const ws of s.clients) ws.close(4000, 'session ended');
    s.clients.clear();
    console.log(`[${id}] worker exited (${code})`);
  });

  sessions.set(id, s);
  console.log(`[${id}] session started${game ? ' game=' + game : ' (stub)'}`);
  return s;
}

function setWatching(s) {
  if (s.status === 'running') s.proc.stdin.write(`W ${s.clients.size > 0 ? 1 : 0}\n`);
}

function publicInfo(s) {
  return {
    id: s.id,
    game: s.game,
    core: s.core,
    status: s.status,
    createdAt: s.createdAt,
    viewers: s.clients.size,
    framesReceived: s.framesReceived,
    framesSent: s.framesSent,
    state: s.state,
    errors: s.errors,
    exitCode: s.exitCode,
  };
}

function json(res, code, body) {
  res.writeHead(code, { 'content-type': 'application/json', 'access-control-allow-origin': '*' });
  res.end(JSON.stringify(body));
}

function readBody(req, limit) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let n = 0;
    req.on('data', (c) => {
      n += c.length;
      if (n > limit) {
        reject(Object.assign(new Error('too large'), { code: 413 }));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css',
  '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon', '.json': 'application/json',
};

function serveStatic(url, res) {
  if (!fs.existsSync(WEB_DIST)) return false;
  let rel = decodeURIComponent(url.pathname);
  if (rel === '/') rel = '/index.html';
  let file = path.join(WEB_DIST, path.normalize(rel));
  if (!file.startsWith(WEB_DIST)) return false;
  // client-side routes (/play/:id) have no file: hand them the app shell
  if ((!fs.existsSync(file) || !fs.statSync(file).isFile()) && !path.extname(rel)) file = path.join(WEB_DIST, 'index.html');
  if (!fs.existsSync(file) || !fs.statSync(file).isFile()) return false;
  res.writeHead(200, { 'content-type': MIME[path.extname(file)] || 'application/octet-stream' });
  fs.createReadStream(file).pipe(res);
  return true;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  try {
    if (req.method === 'OPTIONS') {
      res.writeHead(204, {
        'access-control-allow-origin': '*',
        'access-control-allow-methods': 'GET,POST,PUT,DELETE',
        'access-control-allow-headers': 'content-type',
      });
      return res.end();
    }

    if (url.pathname === '/api/cores' && req.method === 'GET') return json(res, 200, { cores: CORES, default: DEFAULT_CORE });
    if (url.pathname === '/api/games' && req.method === 'GET') return json(res, 200, listGames());
    if (url.pathname === '/api/games' && req.method === 'POST') {
      const name = path.basename(url.searchParams.get('name') || '');
      if (!/^[\w.\- ()]+\.jar$/i.test(name)) return json(res, 400, { error: 'name must be a .jar file' });
      const data = await readBody(req, MAX_UPLOAD);
      if (data.length < 4 || data[0] !== 0x50 || data[1] !== 0x4b) return json(res, 400, { error: 'not a jar/zip file' });
      fs.writeFileSync(path.join(GAMES_DIR, name), data);
      return json(res, 201, { name, size: data.length });
    }

    if (url.pathname === '/api/sessions' && req.method === 'GET') {
      return json(res, 200, [...sessions.values()].map(publicInfo));
    }
    if (url.pathname === '/api/sessions' && req.method === 'POST') {
      const running = [...sessions.values()].filter((x) => x.status === 'running').length;
      if (running >= MAX_SESSIONS) return json(res, 429, { error: 'too many running sessions' });
      let opts = {};
      const raw = await readBody(req, 4096);
      if (raw.length) {
        try { opts = JSON.parse(raw.toString()); } catch { return json(res, 400, { error: 'bad json' }); }
      }
      return json(res, 201, publicInfo(createSession(opts)));
    }

    const m = url.pathname.match(/^\/api\/sessions\/([\w-]+)(\/log)?$/);
    if (m) {
      const s = sessions.get(m[1]);
      if (!s) return json(res, 404, { error: 'not found' });
      if (m[2] && req.method === 'GET') return json(res, 200, s.log);
      if (req.method === 'GET') return json(res, 200, publicInfo(s));
      if (req.method === 'DELETE') {
        if (s.status === 'running') s.proc.stdin.write('Q\n');
        sessions.delete(s.id);
        return json(res, 200, { ok: true });
      }
    }

    if (req.method === 'GET' && !url.pathname.startsWith('/api/') && serveStatic(url, res)) return;
    json(res, 404, { error: 'not found' });
  } catch (e) {
    json(res, e.code === 400 || e.code === 413 ? e.code : 500, { error: e.message });
  }
});

// WebSocket: attach/detach a viewer. Closing the socket NEVER stops the session.
const wss = new WebSocketServer({ server, path: '/ws' });
wss.on('connection', (ws, req) => {
  const id = new URL(req.url, 'http://x').searchParams.get('session');
  const s = sessions.get(id);
  if (!s || s.status !== 'running') return ws.close(4404, 'no such running session');

  s.clients.add(ws);
  setWatching(s);
  if (s.lastFrame) ws.send(s.lastFrame); // instant picture on reconnect

  ws.on('message', (data, isBinary) => {
    if (isBinary || s.status !== 'running') return;
    try {
      const msg = JSON.parse(data.toString());
      if (msg.t === 'key' && Number.isInteger(msg.code)) {
        s.proc.stdin.write(`K ${msg.down ? 1 : 0} ${msg.code}\n`);
      } else if (msg.t === 'ptr' && [0, 1, 2].includes(msg.type) && Number.isInteger(msg.x) && Number.isInteger(msg.y)) {
        s.proc.stdin.write(`P ${msg.type} ${msg.x} ${msg.y}\n`);
      } else if (msg.t === 'text' && typeof msg.s === 'string') {
        const clean = msg.s.slice(0, 200).replace(/[\r\n]/g, '').replace(/\u0008/g, '\\b');
        if (clean) s.proc.stdin.write(`T ${clean}\n`);
      }
    } catch { /* ignore malformed */ }
  });
  ws.on('close', () => {
    s.clients.delete(ws);
    setWatching(s); // nobody left -> worker stops encoding frames, game continues
  });
});

process.on('SIGINT', () => {
  for (const s of sessions.values()) if (s.status === 'running') s.proc.stdin.write('Q\n');
  process.exit(0);
});

server.listen(PORT, () => console.log(`gateway on :${PORT}, games ${GAMES_DIR}, cores [${CORES}] default ${DEFAULT_CORE}, net ${ALLOW_NET ? 'allowed' : 'off'}`));
