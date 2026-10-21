// @vitest-environment jsdom
import { cleanup, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { meQueryKey, type Me } from '../../api/queries/auth';
import { renderPage } from '../../testing/renderPage';
import { RespawnPage } from './RespawnPage';

function me(respawnNearGuild: boolean): Me {
  return {
    accountId: 1,
    username: 'ben',
    role: 'PLAYER',
    playerId: 7,
    points: 0,
    protectedUntil: '2026-10-10T00:00:00Z',
    needsRespawn: true,
    linked: false,
    respawnNearGuild,
    villages: [],
  };
}

function open(respawnNearGuild: boolean) {
  renderPage({ path: '*', url: '/', element: <RespawnPage />, data: [[meQueryKey, me(respawnNearGuild)]] });
}

describe('RespawnPage: starting next to the guild (1.6)', () => {
  afterEach(cleanup);

  it('offers the guild as the main start while a guild mate has a village', () => {
    open(true);
    expect(screen.getByRole('button', { name: 'Start next to my guild' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Start somewhere new' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Start a new village' })).toBeNull();
  });

  it('keeps the one button without a guild mate to start next to', () => {
    open(false);
    expect(screen.getByRole('button', { name: 'Start a new village' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Start next to my guild' })).toBeNull();
  });
});
