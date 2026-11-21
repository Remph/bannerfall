import { describe, expect, it } from 'vitest';
import { kindsInWorld } from './worldKinds';

const kinds = (['ATTACK', 'MESSAGE', 'GUILD_INVITE', 'HELP_REQUEST'] as const).map((kind) => ({ kind, enabled: true }));
const names = (list: { kind: string }[]) => list.map(({ kind }) => kind);

describe('kindsInWorld (UI review 2.1, finding 11)', () => {
  it('lists requests from allies only in a solo world', () => {
    expect(names(kindsInWorld(kinds, { soloMode: true, guildsEnabled: true }))).toContain('HELP_REQUEST');
    expect(names(kindsInWorld(kinds, { soloMode: false, guildsEnabled: true }))).not.toContain('HELP_REQUEST');
  });

  it('lists guild invitations only where guilds are on', () => {
    expect(names(kindsInWorld(kinds, { soloMode: false, guildsEnabled: false }))).toEqual(['ATTACK', 'MESSAGE']);
  });

  it('keeps every kind while the world is unknown', () => {
    expect(kindsInWorld(kinds, undefined)).toEqual(kinds);
  });
});
