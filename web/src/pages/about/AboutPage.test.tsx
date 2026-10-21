// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { renderPage } from '../../testing/renderPage';
import { AboutPage } from './AboutPage';

describe('AboutPage: the thanks to the test players (1.6)', () => {
  afterEach(cleanup);

  it.each([
    ['en', 'Thank you', 'Lots of love'],
    ['de', 'Danke', 'Alles Liebe'],
  ] as const)('names them in its own card, first after the intro (%s)', (language, title, signature) => {
    renderPage({ path: '/about', url: '/about', element: <AboutPage />, data: [], language });
    const headings = screen.getAllByRole('heading').map((heading) => heading.textContent);
    expect(headings[1]).toBe(title);
    expect(screen.getByText(/Anjum, David (and|und) Kai/)).toBeDefined();
    expect(screen.getByText(new RegExp(signature))).toBeDefined();
  });
});

describe('AboutPage: the credit for the medieval icons (1.6)', () => {
  afterEach(cleanup);

  it('names the game-icons.net artists and links the source and the CC BY 3.0 licence', () => {
    renderPage({ path: '/about', url: '/about', element: <AboutPage />, data: [] });
    expect(screen.getByText(/Lorc, Delapouite, Skoll, HeavenlyDog, Cathelineau and DarkZaitzev/)).toBeDefined();
    expect(screen.getByRole('link', { name: 'game-icons.net' }).getAttribute('href')).toBe('https://game-icons.net');
    expect(screen.getByRole('link', { name: 'Licence CC BY 3.0' }).getAttribute('href')).toBe(
      'https://creativecommons.org/licenses/by/3.0/',
    );
  });
});
