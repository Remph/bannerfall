import { QueryClient, QueryClientProvider, type QueryKey } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import { de } from '../i18n/de';
import { en } from '../i18n/en';
import { I18nContext } from '../i18n';
import type { Language } from '../i18n/language';

interface PageOptions {
  /** The route pattern, e.g. `/v/:villageId/banners`. */
  path: string;
  /** The URL the test opens, e.g. `/v/59/banners`. */
  url: string;
  element: ReactElement;
  /** Child routes, rendered in the page's outlet (the report next to the list, 1.3). */
  children?: RouteObject[];
  /** What the queries answer: each entry is put into the cache, so nothing is fetched. */
  data: [QueryKey, unknown][];
  language?: Language;
}

/**
 * Renders a page the way the app does (router, query cache, texts) with the server's answers
 * already in the cache (1.3 render tests). `staleTime: Infinity` keeps the queries from fetching:
 * a query the test forgot shows the page's loading or error state instead of data.
 */
export function renderPage({ path, url, element, children, data, language = 'en' }: PageOptions) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { staleTime: Infinity, retry: false } } });
  for (const [key, value] of data) {
    queryClient.setQueryData(key, value);
  }
  const router = createMemoryRouter([{ path, element, children }], { initialEntries: [url] });
  const i18n = { language, t: language === 'de' ? de : en, setLanguage: () => {} };
  return {
    queryClient,
    ...render(
      <I18nContext value={i18n}>
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </I18nContext>,
    ),
  };
}
