// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { useParams } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { renderPage } from '../../testing/renderPage';
import { JoinPage } from './JoinPage';

/** /join and /login side by side, so the test sees where the join page sends the player. */
function Pages() {
  const { page } = useParams();
  return page === 'join' ? <JoinPage /> : <h1>Login page</h1>;
}

function open(options: unknown, external: unknown = { name: null, problem: null }) {
  return renderPage({
    path: '/:page',
    url: '/join',
    element: <Pages />,
    data: [
      [['authOptions'], options],
      [['externalLogin'], external],
    ],
  });
}

describe('JoinPage', () => {
  afterEach(cleanup);

  it('sends a world without the account server to its login page (2.0 UI review: "Log in with  first.")', async () => {
    open({ mode: 'OFF', accountServerName: null });
    expect(await screen.findByRole('heading', { name: 'Login page' })).toBeTruthy();
  });

  it('asks for the account server login first where the world has one', () => {
    open({ mode: 'LINKING', accountServerName: 'bannerfall.de' });
    expect(screen.getByText('Log in with bannerfall.de first.')).toBeTruthy();
  });
});
