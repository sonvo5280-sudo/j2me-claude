// One command for the whole app: builds what is missing, then runs the gateway (which also serves the web page).
//   npm start        -> http://localhost:3000   (PORT overrides)
// The games need a JDK 21 (javac + java). If there is none, a portable one is downloaded into .jdk/ (J2ME_LOCAL_JDK=1
// forces that, JDK_URL overrides the download address). If that fails the web page still loads, but starting a game
// reports that Java is missing.
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const win = process.platform === 'win32';
const say = (m) => console.log(`[start] ${m}`);

function run(cmd, args, opts = {}) {
  return spawnSync(cmd, args, { cwd: ROOT, stdio: 'inherit', shell: win, ...opts });
}

// 1. web page
const dist = path.join(ROOT, 'web/dist/index.html');
if (!fs.existsSync(dist) || process.env.REBUILD_WEB === '1') {
  say('building the web page…');
  if (!fs.existsSync(path.join(ROOT, 'web/node_modules'))) run('npm', ['--prefix', 'web', 'install']);
  const r = run('npm', ['--prefix', 'web', 'run', 'build']);
  if (r.status !== 0) { console.error('[start] the web build failed, see the messages above'); process.exit(1); }
}

// 2. Java: a JDK 21 on the machine, or a portable one downloaded into .jdk/ (Linux/macOS, needs internet access)
const exe = (n) => (win ? `${n}.exe` : n);
const works = (cmd) => { const r = spawnSync(cmd, ['-version'], { stdio: 'pipe', shell: win }); return !r.error && r.status === 0; };
const JDK_DIR = path.join(ROOT, '.jdk');

function findLocalJdk() {
  if (!fs.existsSync(JDK_DIR)) return null;
  for (const d of fs.readdirSync(JDK_DIR)) {
    for (const home of [path.join(JDK_DIR, d), path.join(JDK_DIR, d, 'Contents', 'Home')]) {
      if (fs.existsSync(path.join(home, 'bin', exe('javac')))) return home;
    }
  }
  return null;
}

async function downloadJdk() {
  const os = { linux: 'linux', darwin: 'mac' }[process.platform];
  const arch = { x64: 'x64', arm64: 'aarch64' }[process.arch];
  const url = process.env.JDK_URL
    || (os && arch && `https://api.adoptium.net/v3/binary/latest/21/ga/${os}/${arch}/jdk/hotspot/normal/eclipse`);
  if (!url) { say(`cannot download a JDK automatically for ${process.platform}/${process.arch}; install JDK 21 yourself`); return null; }
  say(`no JDK 21 found, downloading a portable one into .jdk/ (about 200 MB, first run only)…`);
  fs.mkdirSync(JDK_DIR, { recursive: true });
  const tarball = path.join(JDK_DIR, 'jdk.tar.gz');
  try {
    const res = await fetch(url, { redirect: 'follow', signal: AbortSignal.timeout(10 * 60 * 1000) });
    if (!res.ok || !res.body) throw new Error(`HTTP ${res.status} from ${new URL(url).host}`);
    await pipeline(Readable.fromWeb(res.body), fs.createWriteStream(tarball));
    const t = spawnSync('tar', ['xzf', tarball, '-C', JDK_DIR], { stdio: 'inherit' });
    if (t.error || t.status !== 0) throw new Error(`could not unpack the archive (${t.error?.message || 'tar exit ' + t.status})`);
    return findLocalJdk();
  } catch (e) {
    say(`WARNING: JDK download failed: ${e.message}`);
    return null;
  } finally {
    fs.rmSync(tarball, { force: true });
  }
}

function useJdk(home) {
  process.env.JAVA_HOME = home;
  process.env.PATH = path.join(home, 'bin') + path.delimiter + (process.env.PATH || '');
  say(`using JDK at ${home}`);
}

const built = fs.existsSync(path.join(ROOT, 'worker/build/microemu')) || fs.existsSync(path.join(ROOT, 'worker/build/own'));
let javaOk = process.env.J2ME_LOCAL_JDK !== '1' && works('java') && (built || works('javac'));
if (!javaOk) {
  const home = findLocalJdk() || (await downloadJdk());
  if (home) { useJdk(home); javaOk = true; }
}
if (!javaOk) {
  say('WARNING: no JDK found (javac/java). The page will load, but games cannot start until JDK 21 is installed.');
} else if (!built) {
  say('compiling the Java worker (first run only)…');
  const hasBash = spawnSync('bash', ['-c', 'exit 0'], { stdio: 'pipe', shell: win }).status === 0; // build.sh uses bash
  const r = run(hasBash ? 'bash' : 'sh', ['worker/build.sh']);
  if (r.status !== 0) say('WARNING: the worker build failed; games will not start. See the messages above.');
}

// 3. gateway
const port = process.env.PORT || '3000';
say(`starting on port ${port}`);
const gw = spawn(process.execPath, ['server.js'], {
  cwd: path.join(ROOT, 'gateway'),
  stdio: 'inherit',
  env: { ...process.env, PORT: port },
});
for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, () => gw.kill(sig));
gw.on('exit', (code) => process.exit(code ?? 0));
