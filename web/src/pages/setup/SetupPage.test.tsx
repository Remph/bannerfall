// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { worldSettingsKey } from '../../api/queries/admin';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { pregameWorldSettings } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { SetupPage } from './SetupPage';

const host: Me = {
  accountId: 1,
  username: 'Host',
  role: 'ADMIN',
  playerId: 1,
  points: 0,
  protectedUntil: '2026-10-20T20:00:00Z',
  needsRespawn: false,
  villages: [],
  linked: false,
  respawnNearGuild: false,
};

/** The names of the checked presets (a preset's name is its card's first line). */
function checkedPresets(): (string | null)[] {
  return screen
    .getAllByRole<HTMLInputElement>('radio')
    .filter((radio) => radio.checked)
    .map((radio) => radio.nextElementSibling?.textContent ?? null);
}

function openStart(setupDone: boolean, step = 'start') {
  renderPage({
    path: '/setup',
    url: `/setup?step=${step}`,
    element: <SetupPage />,
    data: [
      [meQueryKey, host],
      [worldSettingsKey, { ...pregameWorldSettings, setupDone }],
    ],
  });
}

describe('SetupPage: the starting point (1.6)', () => {
  afterEach(cleanup);

  it('a fresh setup suggests the friends’ round', () => {
    openStart(false);
    expect(checkedPresets()).toEqual(['Friends’ round']);
  });

  it('running it again picks nothing, so Next keeps the stored world', () => {
    openStart(true);
    expect(checkedPresets()).toEqual([]);
  });
});

describe('SetupPage: the 1.6 settings', () => {
  afterEach(cleanup);

  it('a map spread fills in the map size and noble range, which stay editable', () => {
    openStart(true, 'map');
    fireEvent.click(screen.getByRole('button', { name: 'Vast' }));
    const size = screen.getByLabelText<HTMLInputElement>('Map size (fields per side)');
    const range = screen.getByLabelText<HTMLInputElement>('Noble range (fields)');
    expect([size.value, range.value]).toEqual(['160', '112']);
    fireEvent.change(size, { target: { value: '150' } });
    expect(size.value).toBe('150');
    expect(screen.getByRole('button', { name: 'Vast' }).getAttribute('aria-pressed')).toBe('true');
  });

  it('the rules step has archers, morale and the rising noble price', () => {
    openStart(true, 'rules');
    const archers = screen.getByRole<HTMLInputElement>('switch', { name: /^Archers/ });
    expect(archers.checked).toBe(false);
    expect(screen.getByRole<HTMLInputElement>('switch', { name: /^Morale/ }).checked).toBe(true);
    expect(screen.getByLabelText<HTMLInputElement>('Lowest morale (%)').value).toBe('30');
    const noblePrice = screen.getByRole<HTMLInputElement>('switch', { name: /^Rising noble price/ });
    expect(noblePrice.checked).toBe(true);
    fireEvent.click(noblePrice);
    expect(noblePrice.checked).toBe(false);
    fireEvent.click(noblePrice);
    // back on: the stored step again
    expect(screen.getByText(/adds 25 % of the base price/)).toBeTruthy();
    // starting together only means something with guilds
    expect(screen.queryByRole('switch', { name: /^Guild mates start/ })).toBeNull();
    fireEvent.click(screen.getByRole('switch', { name: /^Guilds/ }));
    expect(screen.getByRole<HTMLInputElement>('switch', { name: /^Guild mates start/ }).checked).toBe(true);
  });
});

describe('SetupPage: the summary (1.6)', () => {
  afterEach(cleanup);

  /** The value next to a summary label. */
  function fact(label: string): string | null {
    return screen.getByText(label, { selector: 'dt' }).nextElementSibling?.textContent ?? null;
  }

  it('lists the map spread with its size and noble range, morale, the noble price and archers', () => {
    openStart(true, 'opening');
    expect(fact('Map spread')).toBe('Normal');
    expect(fact('Map size (fields per side)')).toBe('100 × 100');
    expect(fact('Noble range (fields)')).toBe('70');
    expect(fact('Morale')).toBe('on, at least 30 %');
    expect(fact('Noble price')).toBe('+25 % per village and noble');
    expect(fact('Archers')).toBe('off');
  });
});
