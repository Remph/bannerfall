// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderPage } from '../testing/renderPage';
import { UnitCountInput } from './UnitCountInput';

function show(onEnter?: () => void) {
  renderPage({
    path: '/',
    url: '/',
    element: <UnitCountInput value={5} onChange={() => {}} max={20} limit={100} label="Spearmen" onEnter={onEnter} />,
    data: [],
  });
  return screen.getByRole('textbox', { name: 'Spearmen' });
}

describe('UnitCountInput', () => {
  afterEach(cleanup);

  it('hands Enter in the field on (the recruit page starts the order)', () => {
    const onEnter = vi.fn();
    fireEvent.keyDown(show(onEnter), { key: 'Enter' });
    expect(onEnter).toHaveBeenCalledTimes(1);
  });

  it('ignores other keys', () => {
    const onEnter = vi.fn();
    fireEvent.keyDown(show(onEnter), { key: 'a' });
    expect(onEnter).not.toHaveBeenCalled();
  });

  it('does nothing on Enter where the field has no use for it', () => {
    expect(() => fireEvent.keyDown(show(), { key: 'Enter' })).not.toThrow();
  });
});
