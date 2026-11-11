import { describe, expect, it } from 'vitest';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { dePushTexts } from '../i18n/push-de';
import { enPushTexts } from '../i18n/push-en';

/** The source of every module under src, by its path from here ("../push/serviceWorker.ts"). */
const sources = Object.fromEntries(
  Object.entries(import.meta.glob<string>('../**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true })).map(
    ([path, text]) => [path.startsWith('./') ? `../push/${path.slice(2)}` : path, text],
  ),
);

/** "../push" + "../i18n/de" = "../i18n/de"; only paths that go up from src/push or down into src. */
function join(dir: string, relative: string): string {
  const parts = dir.split('/');
  for (const part of relative.split('/')) {
    if (part === '..') {
      parts.pop();
    } else if (part !== '.') {
      parts.push(part);
    }
  }
  return parts.join('/');
}

/** The modules a module imports as values (`import type` is erased by the build). */
function valueImports(file: string): string[] {
  const text = sources[file] ?? '';
  const dir = file.slice(0, file.lastIndexOf('/'));
  const found: string[] = [];
  for (const match of text.matchAll(/^(?:import|export)\s+(?!type\b)[^;]*?from\s+'(\.[^']*)'/gms)) {
    const base = join(dir, match[1] ?? '');
    const target = [`${base}.ts`, `${base}.tsx`, `${base}/index.ts`].find((path) => path in sources);
    if (target !== undefined) {
      found.push(target);
    }
  }
  return found;
}

function closure(entry: string): Set<string> {
  const seen = new Set<string>();
  const queue = [entry];
  for (let file = queue.pop(); file !== undefined; file = queue.pop()) {
    if (!seen.has(file)) {
      seen.add(file);
      queue.push(...valueImports(file));
    }
  }
  return seen;
}

/**
 * The service worker (`/sw.js`) is a script of its own: whatever it imports ends up in it. With both
 * dictionaries it was 262 kB, with the push texts alone it is about 16 kB (F5, 2.0).
 */
describe('service worker imports', () => {
  it('does not reach the full dictionaries', () => {
    const files = [...closure('../push/serviceWorker.ts')].map((file) => file.slice('../'.length));
    expect(files).toContain('i18n/push-de.ts');
    expect(files).toContain('i18n/push-en.ts');
    expect(files.filter((file) => /^i18n\/(en|de|dictionaries)\.ts$/.test(file))).toEqual([]);
  });

  it('shares the words with the app', () => {
    for (const [full, own] of [
      [en, enPushTexts],
      [de, dePushTexts],
    ] as const) {
      expect(full.push).toBe(own.push);
      expect(full.realtime).toBe(own.realtime);
      expect(full.resources).toBe(own.resources);
      expect(full.buildings).toBe(own.buildings);
      expect(full.map.barbarianVillage).toBe(own.map.barbarianVillage);
    }
  });
});
