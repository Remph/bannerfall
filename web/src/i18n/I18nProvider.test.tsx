// @vitest-environment jsdom
import { act, cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { de } from './de';
import { en } from './en';
import { I18nProvider } from './I18nProvider';
import { useI18n } from './index';
import type { Language } from './language';

// lets a test make the next downloads fail
const failing = vi.hoisted(() => ({ left: 0 }));
vi.mock('./dictionaries', async (importOriginal) => {
  const original = await importOriginal<typeof import('./dictionaries')>();
  return {
    ...original,
    loadDictionary: (language: Language) =>
      failing.left-- > 0 ? Promise.reject(new Error('offline')) : original.loadDictionary(language),
  };
});

function Probe() {
  const { language, setLanguage, t } = useI18n();
  return (
    <>
      <p>{`${language}: ${t.auth.loginTitle}`}</p>
      <button type="button" onClick={() => setLanguage(language === 'en' ? 'de' : 'en')}>
        switch
      </button>
    </>
  );
}

describe('I18nProvider', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    failing.left = 0;
  });
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows nothing until the texts of the stored language have loaded, then the page', async () => {
    localStorage.setItem('bannerfall.language', 'de');

    const { container } = render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    );
    expect(container.childElementCount).toBe(0);

    expect(await screen.findByText(`de: ${de.auth.loginTitle}`)).toBeDefined();
    expect(document.documentElement.lang).toBe('de');
  });

  it('keeps the old texts until the other language has loaded, then switches and remembers it', async () => {
    localStorage.setItem('bannerfall.language', 'en');
    render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    );
    await screen.findByText(`en: ${en.auth.loginTitle}`);

    act(() => screen.getByRole('button', { name: 'switch' }).click());

    expect(await screen.findByText(`de: ${de.auth.loginTitle}`)).toBeDefined();
    expect(document.documentElement.lang).toBe('de');
    expect(localStorage.getItem('bannerfall.language')).toBe('de');
  });

  it('tries the first texts again when the download failed, instead of staying empty', async () => {
    // a reload just happened: the next one would loop, so the provider waits and retries
    sessionStorage.setItem('bannerfall.staleChunkReloadAt', String(Date.now()));
    localStorage.setItem('bannerfall.language', 'en');
    vi.useFakeTimers({ shouldAdvanceTime: true });
    failing.left = 3; // the first request, the one of the effect and the first retry

    const { container } = render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    );
    await act(() => vi.advanceTimersByTimeAsync(100));
    expect(container.childElementCount).toBe(0);

    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(container.childElementCount).toBe(0);

    await act(() => vi.advanceTimersByTimeAsync(3000));
    expect(screen.getByText(`en: ${en.auth.loginTitle}`)).toBeDefined();
  });

  it('keeps the old texts when the download of another language fails', async () => {
    localStorage.setItem('bannerfall.language', 'en');
    render(
      <I18nProvider>
        <Probe />
      </I18nProvider>,
    );
    await screen.findByText(`en: ${en.auth.loginTitle}`);

    failing.left = 1;
    act(() => screen.getByRole('button', { name: 'switch' }).click());
    await act(() => Promise.resolve());

    expect(screen.getByText(`en: ${en.auth.loginTitle}`)).toBeDefined();
  });
});
