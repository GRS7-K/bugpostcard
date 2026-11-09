import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../', import.meta.url);
const paths = new Map([
  ['/', ['index.html', 'text/html']],
  ['/index.html', ['index.html', 'text/html']],
  ['/demo/site.css', ['demo/site.css', 'text/css']],
  ['/demo/app.js', ['demo/app.js', 'text/javascript']],
  ['/lib/browser.js', ['lib/browser.js', 'text/javascript']],
  ['/lib/editor.js', ['lib/editor.js', 'text/javascript']],
  ['/lib/editor.css', ['lib/editor.css', 'text/css']],
  ['/lib/report.js', ['lib/report.js', 'text/javascript']],
]);
const port = Number(process.env.BUGPOSTCARD_PORT ?? 48765);
createServer((req, res) => {
  const route = paths.get(req.url?.split('?')[0]);
  if (req.method !== 'GET' || !route) {
    res.writeHead(404).end('Not found');
    return;
  }
  const [file, type] = route;
  const stream = createReadStream(fileURLToPath(new URL(file, root)));
  stream.on('error', () => res.destroy());
  res.writeHead(200, { 'Content-Type': type + '; charset=utf-8', 'X-Content-Type-Options': 'nosniff' });
  stream.pipe(res);
}).listen(port, '127.0.0.1', () => console.log('BugPostcard demo at http://127.0.0.1:' + port));
