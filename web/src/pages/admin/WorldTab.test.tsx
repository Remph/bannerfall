// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { worldSettingsKey, type AdminWorldSettings } from '../../api/queries/admin';
import { ToastProvider } from '../../components/ToastProvider';
import { pregameWorldSettings } from '../../testing/fixtures';
import { renderPage } from '../../testing/renderPage';
import { WorldTab } from './WorldTab';

// the requests themselves are the typed client's; here only what the tab sends and what fails
const saveSettings = vi.hoisted(() => vi.fn());
const saveEnd = vi.hoisted(() => vi.fn());
vi.mock('../../api/queries/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/queries/admin')>()),
  useUpdateWorldSettings: () => ({ mutateAsync: saveSettings }),
  useSetWorldEnd: () => ({ mutateAsync: saveEnd }),
}));

const open: AdminWorldSettings = { ...pregameWorldSettings, phase: 'OPEN', setupDone: true };

function openTab(section: string, settings: AdminWorldSettings = open) {
  renderPage({
    path: '/admin',
    url: `/admin?tab=world&section=${section}`,
    element: (
      <ToastProvider>
        <WorldTab opening={null} />
      </ToastProvider>
    ),
    data: [[worldSettingsKey, settings]],
  });
}

/** The save bar's text, or null while there is nothing to save. */
function bar(): string | null {
  return screen.queryByRole('region', { name: /change/ })?.textContent ?? null;
}

describe('WorldTab: one save bar for every section (1.6)', () => {
  beforeEach(() => {
    saveSettings.mockReset();
    saveEnd.mockReset();
  });
  afterEach(cleanup);

  it('names the changes by section and row, marks the section, and discards them', () => {
    openTab('rules');
    expect(bar()).toBeNull();
    fireEvent.click(screen.getByRole('switch', { name: /^Morale/ }));
    fireEvent.click(screen.getByRole('switch', { name: /^Standard theft/ }));
    expect(bar()).toContain('2 changes');
    expect(bar()).toContain('Rules: Morale, Standard theft');
    expect(screen.getByRole('button', { name: /^Rules/ }).textContent).toContain('(unsaved changes)');
    // switching sections keeps the draft
    fireEvent.click(screen.getByRole('button', { name: /^Basics/ }));
    expect(bar()).toContain('2 changes');
    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));
    expect(bar()).toBeNull();
  });

  it('shows a switch’s numbers only while it is on', () => {
    openTab('rules');
    expect(screen.getByLabelText('Lowest morale (%)')).toBeTruthy();
    fireEvent.click(screen.getByRole('switch', { name: /^Morale/ }));
    expect(screen.queryByLabelText('Lowest morale (%)')).toBeNull();
  });

  it('names a wrong row and keeps Save off until it is fixed', () => {
    openTab('rules');
    fireEvent.change(screen.getByLabelText('Lowest morale (%)'), { target: { value: '10' } });
    expect(bar()).toContain('Check: Morale');
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Save' }).disabled).toBe(true);
  });

  it('saves the settings and the end date together in one go', async () => {
    saveSettings.mockResolvedValue(undefined);
    saveEnd.mockResolvedValue(undefined);
    openTab('rules');
    fireEvent.click(screen.getByRole('switch', { name: /^Morale/ }));
    fireEvent.click(screen.getByRole('button', { name: /^Victory/ }));
    fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-11-07T18:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(bar()).toBeNull());
    expect(saveSettings).toHaveBeenCalledWith(expect.objectContaining({ moraleEnabled: false, speed: 3 }));
    expect(saveEnd).toHaveBeenCalledWith(new Date('2026-11-07T18:00').toISOString());
  });

  it('keeps what failed in the bar and says the rest is saved', async () => {
    saveSettings.mockResolvedValue(undefined);
    saveEnd.mockRejectedValue(new Error('down'));
    openTab('victory');
    fireEvent.change(screen.getByLabelText('End date'), { target: { value: '2026-11-07T18:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Host ends it' }));
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(bar()).toContain('Saved, except the end date'));
    // the victory mode is saved (the stored settings would follow from the server's answer); the date stays
    expect(bar()).toContain('Victory and end: End date');
    expect(bar()).not.toContain('Victory condition');
  });

  it('keeps a number switch’s field while its number is typed through 0', () => {
    openTab('rules');
    const step = screen.getByLabelText<HTMLInputElement>('Added per rank');
    fireEvent.focusIn(step);
    // "0.3" passes through "0", which reads as off
    fireEvent.change(step, { target: { value: '0' } });
    expect(screen.getByLabelText('Added per rank')).toBe(step);
    fireEvent.change(step, { target: { value: '0.3' } });
    expect(bar()).toContain('Rules: Rising noble price');
    // left at 0, the switch is off and folds its number away
    fireEvent.change(step, { target: { value: '0' } });
    fireEvent.focusOut(step);
    expect(screen.queryByLabelText('Added per rank')).toBeNull();
    expect(screen.getByRole<HTMLInputElement>('switch', { name: /^Rising noble price/ }).checked).toBe(false);
  });

  it('removes the end date with its own button and never reads a half-typed one as none', async () => {
    saveEnd.mockResolvedValue(undefined);
    openTab('victory', { ...open, endsAt: '2026-11-07T17:00:00Z' });
    const field = screen.getByLabelText<HTMLInputElement>('End date');
    // a half-cleared date: the browser reports an empty value and bad input
    Object.defineProperty(field, 'validity', { configurable: true, get: () => ({ badInput: true }) });
    fireEvent.change(field, { target: { value: '' } });
    expect(bar()).toContain('Check: End date');
    expect(screen.getByRole<HTMLButtonElement>('button', { name: 'Save' }).disabled).toBe(true);
    fireEvent.click(screen.getByRole('button', { name: 'Discard' }));
    expect(bar()).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Remove end date' }));
    expect(bar()).toContain('Victory and end: End date');
    expect(bar()).not.toContain('Check');
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(saveEnd).toHaveBeenCalledWith(null));
    expect(saveSettings).not.toHaveBeenCalled();
  });
});
