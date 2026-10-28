import type { Dictionary } from './en';
import type { Language } from './language';

/**
 * The texts of each language are a chunk of their own (about half of the app's code): a visitor
 * downloads the one he reads, and the other one when he switches.
 */
const loaders: Record<Language, () => Promise<Dictionary>> = {
  en: () => import('./en').then((module) => module.en),
  de: () => import('./de').then((module) => module.de),
};

const loading = new Map<Language, Promise<Dictionary>>();

/** The texts of a language; the download starts at the first call, later calls share it. */
export function loadDictionary(language: Language): Promise<Dictionary> {
  let pending = loading.get(language);
  if (pending === undefined) {
    pending = loaders[language]();
    loading.set(language, pending);
    // A failed download (offline, a chunk gone after an update) is tried again by the next call.
    pending.catch(() => loading.delete(language));
  }
  return pending;
}
