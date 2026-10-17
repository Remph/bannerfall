import { describe, expect, it } from 'vitest';
import type { GuildMember } from '../../api/queries/guilds';
import { memberLimit } from './memberLimit';

const world = { guildMaxMembers: 10, npcGuildMaxMembers: 3 };

function member(playerId: number, npc: boolean): GuildMember {
  return {
    playerId,
    name: `P${playerId}`,
    role: 'MEMBER',
    points: 100,
    villageCount: 1,
    joinedAt: '2026-10-17T12:00:00Z',
    npc,
  };
}

describe('member limit', () => {
  it('is the lords’ own limit for a guild of NPC lords', () => {
    expect(memberLimit({ members: [member(1, true), member(2, true)] }, world)).toBe(3);
  });

  it('is the guild limit for a guild of players', () => {
    expect(memberLimit({ members: [member(1, false), member(2, false)] }, world)).toBe(10);
    expect(memberLimit({ members: [] }, world)).toBe(10);
  });
});
