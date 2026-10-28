import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { shouldReload } from '../game/staleChunks';
import { loadDictionary } from './dictionaries';
import type { Dictionary } from './en';
import { I18nContext, type I18nValue } from './index';
import { loadStoredLanguage, pickLanguage, storeLanguage, type Language } from './language';

/** The language and its texts always change together: the old texts stay until the new ones are there. */
interface Active {
  language: Language;
  t: Dictionary;
}

/** How long to wait before the first texts are asked for again after a failed download. */
const RETRY_MS = 3000;

function sessionStorageOrNull(): Storage | null {
  try {
    return window.sessionStorage;
  } catch {
    return null; // privacy settings
  }
}

export function I18nProvider({ children }: { children: ReactNode }) {
  // The function form runs only once, on the first render, and starts the download.
  const [requested, setRequested] = useState<Language>(() => {
    const language = pickLanguage(loadStoredLanguage(), navigator.languages);
    loadDictionary(language).catch(() => {}); // the effect below deals with a failure
    return language;
  });
  const [active, setActive] = useState<Active | null>(null);
  // Counts the failed downloads of the first texts; each one triggers the next try.
  const [failures, setFailures] = useState(0);
  const hasTexts = useRef(false);

  // A newer choice cancels the answer of an older one that arrives late.
  useEffect(() => {
    let current = true;
    let retry: ReturnType<typeof setTimeout> | undefined;
    loadDictionary(requested).then(
      (t) => {
        hasTexts.current = true;
        if (current) {
          setActive({ language: requested, t });
        }
      },
      () => {
        // A later language stays on the old texts until the next choice. Without any texts the app
        // would stay empty: the chunk may be gone after an update (the same reload as for the pages,
        // at most once in 10 s), or the network was away for a moment (try again).
        if (current && !hasTexts.current) {
          if (failures === 0 && shouldReload(sessionStorageOrNull(), Date.now())) {
            window.location.reload();
          } else {
            retry = setTimeout(() => setFailures((count) => count + 1), RETRY_MS);
          }
        }
      },
    );
    return () => {
      current = false;
      clearTimeout(retry);
    };
  }, [requested, failures]);

  const language = active?.language;
  useEffect(() => {
    if (language !== undefined) {
      document.documentElement.lang = language; // screen readers and hyphenation use it
    }
  }, [language]);

  // useMemo keeps the same object between renders, so components using the context
  // only re-render when the language actually changes.
  const value = useMemo<I18nValue | null>(
    () =>
      active === null
        ? null
        : {
            language: active.language,
            t: active.t,
            setLanguage: (next) => {
              setRequested(next);
              storeLanguage(next);
            },
          },
    [active],
  );

  // Nothing to show before the first texts are there (a few milliseconds on a warm cache).
  return value === null ? null : <I18nContext value={value}>{children}</I18nContext>;
}
