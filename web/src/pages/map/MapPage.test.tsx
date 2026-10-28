// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { meQueryKey } from '../../api/queries/auth';
import { myGuildQueryKey } from '../../api/queries/guilds';
import { villageQueryKey } from '../../api/queries/villages';
import { worldQueryKey } from '../../api/queries/world';
import { FIXTURE_NOW, me, village, world } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { MapPage } from './MapPage';

function open(url = `/v/${village.id}/map`, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/map',
    url,
    element: <MapPage />,
    data: [
      [meQueryKey, me],
      [villageQueryKey(village.id), village],
      [worldQueryKey, world],
      [myGuildQueryKey, { guild: null, invitations: [] }],
    ],
    language,
  });
}

/** The map draws on a canvas, which jsdom doesn't have: the page around it is what is tested here. */
describe('MapPage', () => {
  beforeAll(() => {
    // no canvas in jsdom: the page then draws nothing, without jsdom's "not implemented" error
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => {},
      removeEventListener: () => {},
    }));
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
      },
    );
  });

  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  afterAll(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('shows the map with its zoom buttons, the go-to field and the legend', () => {
    open();

    expect(screen.getByLabelText(/map/i, { selector: 'canvas' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Zoom in' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Zoom out' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Centre on my village' })).toBeTruthy();
    expect(screen.getByLabelText('Go to coordinates')).toBeTruthy();
    expect(screen.getByText('For example 48|52 or 48.52')).toBeTruthy();
  });

  it('names a wrong field instead of jumping to it', () => {
    open();
    const field = screen.getByLabelText('Go to coordinates');

    fireEvent.change(field, { target: { value: 'north' } });
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));
    expect(screen.getByText('Enter coordinates like 48|52.')).toBeTruthy();

    fireEvent.change(field, { target: { value: '500|500' } });
    fireEvent.click(screen.getByRole('button', { name: 'Go' }));
    expect(screen.getByText('That field is not on the map (0 to 99).')).toBeTruthy();
  });

  it('is shown in German too', () => {
    open(`/v/${village.id}/map`, 'de');

    expect(screen.getByRole('button', { name: 'Vergrößern' })).toBeTruthy();
  });
});
