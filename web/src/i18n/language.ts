export const languages = ['en', 'de'] as const;

/** 'en' | 'de' – derived from the array above, so both stay in sync. */
export type Language = (typeof languages)[number];

/** Each language's name in itself – shown like this in every UI language. */
export const languageNames: Record<Language, string> = { en: 'English', de: 'Deutsch' };

const STORAGE_KEY = 'bannerfall.language';

/** Type guard: if it returns true, TypeScript treats `value` as a Language afterwards. */
export function isLanguage(value: string | null): value is Language {
  return languages.some((language) => language === value);
}

/** The stored choice wins; otherwise the first supported browser language; otherwise English. */
export function pickLanguage(stored: string | null, browserLanguages: readonly string[]): Language {
  if (isLanguage(stored)) {
    return stored;
  }
  for (const tag of browserLanguages) {
    const prefix = tag.slice(0, 2).toLowerCase();
    if (isLanguage(prefix)) {
      return prefix;
    }
  }
  return 'en';
}

// localStorage can throw (private mode, blocked storage). Then we just don't remember the choice.
export function loadStoredLanguage(): string | null {
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function storeLanguage(language: Language): void {
  try {
    localStorage.setItem(STORAGE_KEY, language);
  } catch {
    // ignore
  }
}
