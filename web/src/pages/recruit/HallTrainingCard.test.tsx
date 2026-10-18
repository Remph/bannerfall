// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { hallTrainingQueryKey } from '../../api/queries/smithy';
import { FIXTURE_NOW, hallTraining, village } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { HallTrainingCard } from './HallTrainingCard';

function open(language: 'en' | 'de' = 'en', data = hallTraining) {
  return renderPage({
    path: '/v/:villageId/recruit/banner_hall',
    url: `/v/${village.id}/recruit/banner_hall`,
    element: <HallTrainingCard village={village} />,
    data: [[hallTrainingQueryKey(village.id), data]],
    language,
  });
}

describe('HallTrainingCard', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(FIXTURE_NOW));
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('shows the running training and keeps the hall busy for the thief', () => {
    open();

    expect(screen.getByRole('button', { name: 'Cancel the training of Paladin to Lv 3' })).toBeTruthy();
    expect(screen.getByText('Train Lv 2')).toBeTruthy();
    expect(screen.getByText('The Banner Hall of this village is busy with another training.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Train' }).hasAttribute('disabled')).toBe(true);
  });

  it("shows the thief's chance per level instead of his values", () => {
    open();

    expect(screen.getByRole('table', { name: 'Chance per surviving thief at each level' })).toBeTruthy();
    expect(screen.getByText('3%')).toBeTruthy();
    expect(screen.getByText('5%')).toBeTruthy();
    // the paladin keeps his values per level
    expect(screen.getByText('132')).toBeTruthy();
  });

  it('names only the paladin in a world without banners', () => {
    open('en', { ...hallTraining, units: hallTraining.units.filter((unit) => unit.unit === 'PALADIN') });

    expect(screen.getByText(/^The Banner Hall trains the levels of the paladin\./)).toBeTruthy();
  });

  it('speaks German', () => {
    open('de');

    expect(screen.getByText('Hallentraining')).toBeTruthy();
    expect(screen.getByText('Stufe 2 trainieren')).toBeTruthy();
  });
});
