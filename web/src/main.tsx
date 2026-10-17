import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { queryClient } from './api/queryClient';
import { reloadOnStaleChunks } from './game/staleChunks';
import { I18nProvider } from './i18n/I18nProvider';
import { router } from './router';
// Self-hosted fonts (05-frontend §8): only the weights we use. The browser downloads a file only
// when a character on the page needs it.
import '@fontsource/alegreya-sans/400.css';
import '@fontsource/alegreya-sans/500.css';
import '@fontsource/alegreya-sans/700.css';
import '@fontsource/alegreya-sc/700.css';
import '@fontsource/uncial-antiqua/400.css';
import './styles/global.css';

reloadOnStaleChunks();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
