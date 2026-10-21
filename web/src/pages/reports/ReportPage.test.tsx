// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { reportQueryKey, type Report } from '../../api/queries/reports';
import { InListDetailContext } from '../../components/listDetailContext';
import { renderPage } from '../../testing/renderPage';
import { ReportPage } from './ReportPage';

const me: Me = {
  accountId: 1,
  username: 'ben',
  role: 'PLAYER',
  playerId: 7,
  points: 100,
  protectedUntil: '2026-10-10T00:00:00Z',
  needsRespawn: false,
  linked: false,
  respawnNearGuild: false,
  villages: [],
};

// a report whose payload can't be read any more: the page shows its title, the way back and Delete
const report: Report = { id: 3, kind: 'ATTACK', happenedAt: '2026-10-16T09:12:00Z', payload: null, sharedBy: null };

function open(url: string, inListDetail = false) {
  return renderPage({
    path: '/reports/:reportId',
    url,
    element: (
      <InListDetailContext value={inListDetail}>
        <ReportPage />
      </InListDetailContext>
    ),
    data: [
      [meQueryKey, me],
      [reportQueryKey(3), report],
    ],
  });
}

describe('ReportPage', () => {
  afterEach(cleanup);

  it('leads back to the list the report was opened from', () => {
    open('/reports/3?filter=attacks&page=2');

    expect(screen.getByText('This report can’t be shown any more. You can delete it.')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'All reports' }).getAttribute('href')).toBe(
      '/reports?filter=attacks&page=2',
    );
  });

  it('has no way back next to the list (desktop)', () => {
    open('/reports/3', true);

    expect(screen.getByText('This report can’t be shown any more. You can delete it.')).toBeTruthy();
    expect(screen.queryByRole('link', { name: 'All reports' })).toBeNull();
  });
});
