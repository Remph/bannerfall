// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import type { PlanBuilding, PlanLine } from '../../game/buildPlans';
import fixture from '../../game/buildPlans.fixture.json';
import { renderPage } from '../../testing/renderPage';
import { PlanCodeSheet, type CodeMode } from './PlanCodeSheet';

const buildings = fixture.buildings as PlanBuilding[];
const lines: PlanLine[] = [
  { kind: 'build', building: 'HEADQUARTERS', level: 3 },
  { kind: 'recruit', template: 'B' },
];

function open(mode: CodeMode) {
  const onApply = vi.fn();
  renderPage({
    path: '/',
    url: '/',
    element: (
      <PlanCodeSheet mode={mode} name="Eco" lines={lines} buildings={buildings} onApply={onApply} onClose={() => {}} />
    ),
    data: [],
  });
  return onApply;
}

describe('PlanCodeSheet', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
  });

  afterEach(cleanup);

  it('shows the code of the editor’s plan to copy', () => {
    open('share');

    expect((screen.getByRole('textbox', { name: 'Plan code' }) as HTMLTextAreaElement).value).toBe(
      'BFP1|Eco|HEADQUARTERS:3,RECRUIT:B',
    );
  });

  it('hands a pasted code on as a name and lines', () => {
    const onApply = open('import');

    fireEvent.change(screen.getByRole('textbox', { name: 'Plan code' }), {
      target: { value: ' BFP1|Mine|HEADQUARTERS:5,\nRECRUIT:C ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Use this plan' }));

    expect(onApply).toHaveBeenCalledWith('Mine', [
      { kind: 'build', building: 'HEADQUARTERS', level: 5 },
      { kind: 'recruit', template: 'C' },
    ]);
  });

  it('says what is wrong with a code instead of taking it over', () => {
    const onApply = open('import');
    const apply = screen.getByRole('button', { name: 'Use this plan' });
    expect((apply as HTMLButtonElement).disabled).toBe(true);

    fireEvent.change(screen.getByRole('textbox', { name: 'Plan code' }), { target: { value: 'BFP1|Mine|TOWER:5' } });
    fireEvent.click(apply);

    expect(screen.getByText('The code names a building this game does not have.')).toBeTruthy();
    expect(onApply).not.toHaveBeenCalled();
  });
});
