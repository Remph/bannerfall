import { describe, expect, it } from 'vitest';

/**
 * `ChatDock.tsx` next to `chatDock.ts` builds on Linux but not on Windows or macOS: there the import
 * `./ChatDock` finds `chatDock.ts` first (found after M10). Two modules in one folder must differ by
 * more than letter case.
 */
describe('file names', () => {
  it('no two modules in a folder differ only in letter case', () => {
    // every .ts/.tsx path under src, e.g. "./pages/chat/ChatDock.tsx" (Vite lists them, nothing is loaded)
    const paths = Object.keys(import.meta.glob('./**/*.{ts,tsx}'));
    expect(caseClashes(paths)).toEqual([]);
  });
});

/** Pairs of paths that are the same module name apart from letter case ("a/Foo.tsx" and "a/foo.ts"). */
function caseClashes(paths: readonly string[]): string[] {
  const seen = new Map<string, string>();
  const clashes: string[] = [];
  for (const path of paths) {
    const module = path.replace(/\.tsx?$/, '');
    const other = seen.get(module.toLowerCase());
    if (other !== undefined && other.replace(/\.tsx?$/, '') !== module) {
      clashes.push(`${other} / ${path}`);
    }
    seen.set(module.toLowerCase(), path);
  }
  return clashes;
}

describe('caseClashes', () => {
  it('finds the M9b pair and nothing else', () => {
    expect(caseClashes(['./a/ChatDock.tsx', './a/chatDock.ts', './a/chatDock.test.ts', './b/chatDock.ts'])).toEqual([
      './a/ChatDock.tsx / ./a/chatDock.ts',
    ]);
  });
});
