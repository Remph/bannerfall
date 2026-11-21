// @vitest-environment jsdom
import { cleanup, fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { NpcLord } from '../../api/queries/admin';
import { renderPage } from '../../testing/renderPage';
import { NpcLordsCard } from './NpcLordsCard';

function lord(playerId: number, name: string, personality: NpcLord['personality'], template: string): NpcLord {
  return {
    playerId,
    name,
    personality,
    template,
    points: 1200,
    villageCount: 1,
    lastThinkAt: null,
    nextThinkAt: null,
    braked: false,
    boosted: false,
    productionFactor: 0.9,
    guildId: null,
    guildName: null,
    guildTag: null,
    guildByAdmin: false,
    paladinLevel: 1,
    bannerVillages: [],
    stash: [],
    learning: null,
  };
}

function open(lords: NpcLord[]) {
  // the world settings stay unloaded: guilds and banners count as off
  vi.spyOn(api, 'GET').mockReturnValue(new Promise(() => {}) as never);
  return renderPage({
    path: '/admin',
    url: '/admin',
    element: <NpcLordsCard />,
    data: [[['admin', 'npc-lords'], lords]],
  });
}

/** The NPC lords card with the lord variants (2.1, 09-npc-lords §16.8). */
describe('NpcLordsCard', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('shows the variant next to the personality, and only the personality for a classic lord', () => {
    open([lord(1, 'Baron Wulfram', 'OFFENSIVE', 'HOTHEAD'), lord(2, 'Lady Rowena', 'DEFENSIVE', 'DEFENSIVE')]);

    const sides = screen.getAllByRole('listitem').map((item) => item.querySelector('span')?.textContent);
    expect(sides).toEqual(['Hothead (Offensive)', 'Defensive']);
  });

  it('spawns a drawn lord with a personality button and a chosen variant behind "…"', async () => {
    const post = vi
      .spyOn(api, 'POST')
      .mockResolvedValue({ data: lord(3, 'Graf Hagen', 'BALANCED', 'VULTURE'), response: new Response() } as never);
    open([]);

    fireEvent.click(screen.getByRole('button', { name: 'Offensive' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/admin/npc-lords', { body: { personality: 'OFFENSIVE', variant: null } }),
    );
    // the list reloads after a spawn (and never answers here): a fresh card for the next one
    cleanup();
    open([]);

    fireEvent.click(screen.getByRole('button', { name: 'Spawn a chosen variant' }));
    fireEvent.click(screen.getByRole('button', { name: 'Vulture (Balanced)' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/admin/npc-lords', {
        body: { personality: 'BALANCED', variant: 'VULTURE' },
      }),
    );
    cleanup();
    open([]);

    fireEvent.click(screen.getByRole('button', { name: 'Spawn a chosen variant' }));
    fireEvent.click(screen.getByRole('button', { name: 'Defensive, classic' }));
    await waitFor(() =>
      expect(post).toHaveBeenCalledWith('/api/admin/npc-lords', {
        body: { personality: 'DEFENSIVE', variant: 'PLAIN' },
      }),
    );
  });
});
