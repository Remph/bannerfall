// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { renderPage } from '../../testing/renderPage';
import { PlanLineView } from './PlanLineView';

function show(language: 'en' | 'de') {
  renderPage({
    path: '/',
    url: '/',
    element: (
      <ol>
        <PlanLineView line={{ kind: 'mines', level: 7 }} index={2} templates={[]} />
      </ol>
    ),
    data: [],
    language,
  });
}

describe('PlanLineView', () => {
  afterEach(cleanup);

  it('shows the mines line as one line with its level', () => {
    show('en');

    const item = screen.getByRole('listitem');
    expect(item.textContent).toContain('All mines');
    expect(item.textContent).toContain('Lv 7');
    expect(item.textContent).toContain('3');
  });

  it('names it in German too', () => {
    show('de');

    expect(screen.getByRole('listitem').textContent).toContain('Alle Minen');
  });
});
