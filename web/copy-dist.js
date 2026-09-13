import { existsSync, mkdirSync, cpSync } from 'node:fs';
import { resolve } from 'node:path';

// 构建后把前端产物同步到后端 static 目录（供 spring-boot:run 返回 SPA）。
// 使用 Node 内置 fs.cpSync，跨 Windows / Linux / macOS，无需 robocopy。
const src = resolve('dist');
const dst = resolve('..', 'server', 'src', 'main', 'resources', 'static');

if (!existsSync(src)) {
  console.error(`Source directory not found: ${src}`);
  process.exit(1);
}

if (!existsSync(dst)) {
  mkdirSync(dst, { recursive: true });
}
cpSync(src, dst, { recursive: true, force: true });
console.log(`Copied ${src} -> ${dst}`);
