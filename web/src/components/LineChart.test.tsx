// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { LineChart, type ChartLine } from './LineChart';

const ada: ChartLine = {
  key: 1,
  label: 'ada',
  points: [
    { day: '2026-10-17', value: 100 },
    { day: '2026-10-18', value: 150 },
    { day: '2026-10-19', value: 220 },
  ],
};
const lord: ChartLine = {
  key: 2,
  label: 'Sir Godwin',
  points: [
    { day: '2026-10-18', value: 90 },
    { day: '2026-10-19', value: 130 },
  ],
};

function draw(lines: ChartLine[]) {
  return render(
    <LineChart
      label="Points"
      lines={lines}
      formatValue={(value) => `${value} pts`}
      formatDay={(day) => day.slice(5)}
      dayLabel="Day"
    />,
  );
}

function readout(): string {
  return document.querySelector('[aria-live]')?.textContent ?? '';
}

afterEach(cleanup);

describe('LineChart', () => {
  it('reads out the last day until another is picked, with a line per entity', () => {
    const { container } = draw([ada, lord]);

    expect(readout()).toBe('10-19ada220 ptsSir Godwin130 pts');
    expect(container.querySelectorAll('polyline')).toHaveLength(2);
    // two lines: a legend names them, so colour is never the only key
    expect(screen.getAllByRole('listitem').map((item) => item.textContent)).toEqual(['ada', 'Sir Godwin']);
  });

  it('picks a day with the arrow keys and shows a dash for a line without it', () => {
    draw([ada, lord]);
    const plot = screen.getByRole('img', { name: 'Points' });

    fireEvent.keyDown(plot, { key: 'ArrowLeft' });
    fireEvent.keyDown(plot, { key: 'ArrowLeft' });
    fireEvent.keyDown(plot, { key: 'ArrowLeft' });

    expect(readout()).toBe('10-17ada100 ptsSir Godwin–');
  });

  it('draws one day as a dot and carries every number in a table for screen readers', () => {
    const { container } = draw([{ key: 1, label: 'ada', points: [{ day: '2026-10-19', value: 5 }] }]);

    expect(container.querySelectorAll('polyline')).toHaveLength(0);
    expect(container.querySelectorAll('circle')).toHaveLength(1);
    // one line: no legend, the chart's title names it
    expect(screen.queryByRole('list')).toBeNull();
    const table = screen.getByRole('table');
    expect(within(table).getByRole('row', { name: '10-19 5 pts' })).toBeTruthy();
  });
});
