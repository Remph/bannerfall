// @vitest-environment jsdom
import { cleanup, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { villageQueryKey, type Village } from '../../api/queries/villages';
import { FIXTURE_NOW, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { WarehousePage } from './WarehousePage';

function open(shown: Village = village, language: 'en' | 'de' = 'en') {
  return renderPage({
    path: '/v/:villageId/warehouse',
    url: `/v/${shown.id}/warehouse`,
    element: <WarehousePage />,
    data: [[villageQueryKey(shown.id), shown]],
    language,
  });
}

/** The warehouse page (M22): stock and a countdown per resource, reached from the resource bar. */
describe('WarehousePage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the capacity, the three resources and the way to the upgrade', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Warehouse' })).toBeTruthy();
    expect(screen.getByText('Lv 10 · holds 6,397 of each resource')).toBeTruthy();
    const list = screen.getByRole('heading', { name: 'When is it full?' }).closest('section') as HTMLElement;
    const rows = within(list).getAllByRole('listitem');
    expect(rows.map((row) => within(row).getByText(/^(Wood|Clay|Iron)$/).textContent)).toEqual([
      'Wood',
      'Clay',
      'Iron',
    ]);
    expect(within(rows[0] as HTMLElement).getByText('4,200 / 6,397')).toBeTruthy();
    expect(within(rows[0] as HTMLElement).getByText(/^Full in /)).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Upgrade the warehouse in the Town Hall' }).getAttribute('href')).toBe(
      `/v/${village.id}/hq#WAREHOUSE`,
    );
    expect(screen.getByRole('link', { name: /Village overview/ }).getAttribute('href')).toBe(`/v/${village.id}`);
  });

  it('says that a full resource loses its production and that nothing is produced without a rate', () => {
    open({
      ...village,
      resources: { ...village.resources, wood: 6397 },
      production: { wood: 33, clay: 0, iron: 33 },
    });

    expect(screen.getByText('Full: production is lost until you spend some')).toBeTruthy();
    expect(screen.getByText('Not produced')).toBeTruthy();
  });

  it('lists a running warehouse upgrade', () => {
    open({
      ...village,
      buildQueue: [
        {
          id: 1,
          building: 'WAREHOUSE',
          targetLevel: 11,
          startAt: '2026-10-15T20:00:00Z',
          endAt: '2026-10-15T21:00:00Z',
          demolish: false,
          automated: false,
        },
      ],
    });

    expect(screen.getByText('Lv 11')).toBeTruthy();
  });

  it('is shown in German too', () => {
    open(village, 'de');

    expect(screen.getByRole('heading', { level: 1, name: 'Speicher' })).toBeTruthy();
  });

  it('sends a village id that is not a number home', () => {
    renderPage({ path: '/v/:villageId/warehouse', url: '/v/x/warehouse', element: <WarehousePage />, data: [] });

    expect(screen.queryByRole('heading', { level: 1 })).toBeNull();
  });
});
