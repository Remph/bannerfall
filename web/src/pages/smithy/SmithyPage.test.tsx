// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { smithyQueryKey } from '../../api/queries/smithy';
import { villageQueryKey } from '../../api/queries/villages';
import { FIXTURE_NOW, smithy, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { SmithyPage } from './SmithyPage';

function open(data = smithy, villageData = village) {
  return renderPage({
    path: '/v/:villageId/smithy',
    url: `/v/${village.id}/smithy`,
    element: <SmithyPage />,
    data: [
      [villageQueryKey(village.id), villageData],
      [smithyQueryKey(village.id), data],
    ],
  });
}

describe('SmithyPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the running research and keeps the smithy busy for other units', () => {
    open();

    expect(screen.getByRole('heading', { level: 1, name: 'Smithy' })).toBeTruthy();
    expect(screen.getAllByText('Spearman to Lv 2').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: 'Cancel the research of Spearman to Lv 2' })).toBeTruthy();
    expect(screen.queryByText('Nothing is being researched in this village.')).toBeNull();
    expect(screen.getAllByText('The smithy of this village is busy with another research.').length).toBeGreaterThan(0);
  });

  it('with levels off shows only the base values and no research', () => {
    open({ ...smithy, levelsEnabled: false });

    expect(
      screen.getByText('Unit levels are switched off in this world: every unit has its base values.'),
    ).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Research' })).toBeNull();
  });

  it('sends the paladin and the thief to the Banner Hall for their levels (1.4)', () => {
    open({ ...smithy, levelsEnabled: false });

    // their levels count with unit levels off too: the paladin's badge and his three levels show
    expect(screen.getByText('Lv 2 of 3')).toBeTruthy();
    expect(screen.getAllByText('Its levels are trained in the Banner Hall.')).toHaveLength(2);
    const links = screen.getAllByRole('link', { name: 'To the Banner Hall' });
    expect(links[0]?.getAttribute('href')).toBe(`/v/${village.id}/recruit/banner_hall`);
  });

  it('without a smithy points to the Town Hall', () => {
    open({ ...smithy, smithyLevel: 0 }, { ...village, buildings: { ...village.buildings, SMITHY: 0 } });

    expect(screen.getByText('The smithy is not built in this village yet. Research needs it.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Build the smithy in the Town Hall' })).toBeTruthy();
  });
});
