import { createContext, useContext } from 'react';
import type { Dictionary } from './en';
import type { Language } from './language';

export interface I18nValue {
  language: Language;
  setLanguage: (language: Language) => void;
  /** The texts of the active language. */
  t: Dictionary;
}

/** Filled by <I18nProvider>. null means "used outside the provider" (a bug). */
export const I18nContext = createContext<I18nValue | null>(null);

export function useI18n(): I18nValue {
  const value = useContext(I18nContext);
  if (value === null) {
    throw new Error('useI18n() must be used inside <I18nProvider>');
  }
  return value;
}

/** The usual way to get texts in a component: `const t = useT(); … {t.auth.loginTitle}` */
export function useT(): Dictionary {
  return useI18n().t;
}
