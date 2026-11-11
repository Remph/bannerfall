import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router/dom';
import { queryClient } from './api/queryClient';
import { reloadOnStaleChunks } from './game/staleChunks';
import { I18nProvider } from './i18n/I18nProvider';
import { rememberSetupCode } from './pages/setup/setupCode';
import { router } from './router';
import { followThemeColor } from './theme/theme';
// Self-hosted fonts (05-frontend §8): only the weights we use. The browser downloads a file only
// when a character on the page needs it.
import '@fontsource/bricolage-grotesque/400.css';
import '@fontsource/bricolage-grotesque/500.css';
import '@fontsource/bricolage-grotesque/600.css';
import '@fontsource/bricolage-grotesque/700.css';
import '@fontsource/bricolage-grotesque/800.css';
// the title face of the stitched theme (styles/looks.css); the browser fetches the file only when it is used
import '@fontsource/almendra/latin-700.css';
import './styles/global.css';

reloadOnStaleChunks();
followThemeColor();
rememberSetupCode(window.location);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <I18nProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </I18nProvider>
  </StrictMode>,
);
