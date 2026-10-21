// @vitest-environment jsdom
import { act, cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { SendAllResult } from '../../game/farm';
import { renderPage } from '../../testing/renderPage';
import { SendAllBar } from './SendAllBar';

/** Targets with a banner chance open, for the second button. */
const OPEN_CHANCES = 3;
const FIRST_TARGET = 11;
const SECOND_TARGET = 12;
const THIRD_TARGET = 13;
/** A run that sent two targets and skipped one for lack of troops. */
const RUN: SendAllResult = {
  sent: [FIRST_TARGET, SECOND_TARGET],
  skipped: [{ targetId: THIRD_TARGET, reason: 'NOT_ENOUGH_UNITS' }],
};

/** `bannerChances: null`: a world without banner drops. */
function open({ bannerChances = OPEN_CHANCES as number | null, result = undefined as SendAllResult | undefined } = {}) {
  const onSend = vi.fn<(bannerChanceOnly: boolean) => void>();
  renderPage({
    path: '/',
    url: '/',
    element: (
      <SendAllBar
        leavesPlayersOut={false}
        bannerChances={bannerChances ?? undefined}
        pending={false}
        result={result}
        error={null}
        onSend={onSend}
        preview={{ all: { attacks: 30, haul: 22_200 }, bannerChances: { attacks: 1, haul: 800 } }}
      />
    ),
    data: [],
  });
  return onSend;
}

describe('SendAllBar', () => {
  afterEach(cleanup);

  it('sends on the second tap only', () => {
    const onSend = open();

    fireEvent.click(screen.getByRole('button', { name: 'Send all' }));
    expect(onSend).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Really send all?' }));

    expect(onSend).toHaveBeenCalledWith(false);
  });

  it('says on the first tap what the run would do, in place of the hint (1.6)', () => {
    open();
    expect(screen.queryByText('30 attacks, loot up to 22,200')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'Send all' }));
    expect(screen.getByText('30 attacks, loot up to 22,200')).toBeTruthy();
    expect(screen.queryByText(/Top to bottom/)).toBeNull();

    // the other run's first tap: its own numbers, the first button turns back
    fireEvent.click(screen.getByRole('button', { name: 'Send banner chances (3)' }));
    expect(screen.getByText('1 attack, loot up to 800')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Send all' })).toBeTruthy();
  });

  it('turns back after a few seconds without the second tap', () => {
    vi.useFakeTimers();
    try {
      open();
      fireEvent.click(screen.getByRole('button', { name: 'Send all' }));
      act(() => vi.advanceTimersByTime(4000));

      expect(screen.getByRole('button', { name: 'Send all' })).toBeTruthy();
      expect(screen.getByText(/Top to bottom/)).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('says no banner chance is open instead of a button with 0', () => {
    open({ bannerChances: 0 });

    expect(screen.queryByRole('button', { name: 'Send banner chances (0)' })).toBeNull();
    expect(screen.getByText('No banner chance open')).toBeTruthy();
  });

  it('keeps the banner chance rules behind the info button', () => {
    open();
    const info = screen.getByRole('button', { name: 'How banner chances work' });
    const rules = document.getElementById(info.getAttribute('aria-controls') ?? '');

    expect(info.getAttribute('aria-expanded')).toBe('false');
    expect(rules?.hidden).toBe(true);
    fireEvent.click(info);
    expect(info.getAttribute('aria-expanded')).toBe('true');
    expect(rules?.hidden).toBe(false);
  });

  it('has neither the second button nor the rules without banner drops', () => {
    open({ bannerChances: null });

    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.queryByText('No banner chance open')).toBeNull();
  });

  it('sums the run up next to the buttons', () => {
    open({ result: RUN });

    expect(screen.getByRole('status').textContent).toBe('2 attacks sent. 1 not sent: not enough troops at home.');
  });

  it('gives a run that sent nothing no success check', () => {
    open({ result: { sent: [], skipped: [{ targetId: THIRD_TARGET, reason: 'NOT_ENOUGH_UNITS' }] } });
    const chip = screen.getByRole('status');

    expect(chip.textContent).toBe('No attack sent. 1 not sent: not enough troops at home.');
    expect(chip.querySelector('svg')).toBeNull();
  });

  it('offers "notify me when this run is back" only when asked to (1.6)', () => {
    open();
    expect(screen.queryByRole('switch')).toBeNull();
    cleanup();

    const onChange = vi.fn<(on: boolean) => void>();
    renderPage({
      path: '/',
      url: '/',
      element: (
        <SendAllBar
          leavesPlayersOut={false}
          bannerChances={undefined}
          pending={false}
          result={undefined}
          error={null}
          notifyRun={{ on: false, onChange }}
          onSend={vi.fn()}
          preview={{ all: { attacks: 2, haul: 800 }, bannerChances: { attacks: 0, haul: 0 } }}
        />
      ),
      data: [],
    });
    fireEvent.click(screen.getByRole('switch', { name: 'Notify me when this run is back' }));
    expect(onChange).toHaveBeenCalledWith(true);
  });
});
