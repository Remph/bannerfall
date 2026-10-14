import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { de } from './de';
import { en, type Dictionary } from './en';
import { I18nContext, type I18nValue } from './index';
import { loadStoredLanguage, pickLanguage, storeLanguage, type Language } from './language';

const dictionaries: Record<Language, Dictionary> = { en, de };

export function I18nProvider({ children }: { children: ReactNode }) {
  // The function form runs only once, on the first render.
  const [language, setLanguageState] = useState<Language>(() =>
    pickLanguage(loadStoredLanguage(), navigator.languages),
  );

  useEffect(() => {
    document.documentElement.lang = language; // screen readers and hyphenation use it
  }, [language]);

  // useMemo keeps the same object between renders, so components using the context
  // only re-render when the language actually changes.
  const value = useMemo<I18nValue>(
    () => ({
      language,
      t: dictionaries[language],
      setLanguage: (next) => {
        setLanguageState(next);
        storeLanguage(next);
      },
    }),
    [language],
  );

  return <I18nContext value={value}>{children}</I18nContext>;
}
