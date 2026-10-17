import { describe, expect, it } from 'vitest';
import type { ReportSummary } from '../../api/queries/reports';
import type { VillageRef } from '../../api/queries/troops';
import { en } from '../../i18n/en';
import { ApiError } from '../../api/errors';
import { reportTitle, sendError, villageName } from './reportText';

const ben: VillageRef = { id: 1, name: 'Benhausen', x: 48, y: 52, playerId: 7, playerName: 'Ben' };
const tom: VillageRef = { id: 2, name: 'Tomburg', x: 50, y: 50, playerId: 8, playerName: 'Tom' };
const barbarians: VillageRef = { id: 3, name: null, x: 40, y: 40, playerId: null, playerName: null };

function summary(from: VillageRef, to: VillageRef): ReportSummary {
  return { from, to, outcome: null, bannersGained: [], loot: null, losses: {}, fullHaul: false };
}

describe('reportTitle', () => {
  it('names attacker and target of a battle, the same for both sides', () => {
    const battle = { ...summary(ben, tom), outcome: 'ATTACKER_WON' as const };
    expect(reportTitle(en, 'ATTACK', battle, 7)).toBe('Benhausen attacks Tomburg');
    expect(reportTitle(en, 'DEFENSE', battle, 8)).toBe('Benhausen attacks Tomburg');
    const scouts = { ...summary(ben, barbarians), outcome: 'SCOUTS_ONLY' as const };
    expect(reportTitle(en, 'SCOUT', scouts, 7)).toBe('Benhausen scouts Barbarian village');
    expect(reportTitle(en, 'DEFENSE', { ...scouts, to: tom }, 8)).toBe('Benhausen scouts Tomburg');
  });

  it('has a title for a report the server can’t read any more', () => {
    expect(reportTitle(en, 'ATTACK', null, 7)).toBe('Report that can’t be shown');
  });

  it('names a support', () => {
    expect(reportTitle(en, 'SUPPORT_ARRIVED', summary(ben, tom), 8)).toBe('Support from Benhausen arrived in Tomburg');
  });

  it('names the merchants of a delivery', () => {
    expect(reportTitle(en, 'TRADE_ARRIVED', summary(ben, tom), 8)).toBe('Merchants from Benhausen arrived in Tomburg');
  });

  it('tells who sent a support home', () => {
    // Ben's units in Tomburg: Tom sent them back, Ben reads it
    expect(reportTitle(en, 'SUPPORT_RETURNED', summary(ben, tom), 7)).toBe('Tomburg sent your support home');
    // Ben withdrew them, Tom reads it
    expect(reportTitle(en, 'SUPPORT_RETURNED', summary(ben, tom), 8)).toBe(
      'Benhausen withdrew its support from Tomburg',
    );
  });
});

describe('reportTitle of a conquest', () => {
  it('reads the same for the conqueror and the previous owner', () => {
    expect(reportTitle(en, 'CONQUEST', summary(ben, tom), 7)).toBe('Benhausen conquered Tomburg');
    expect(reportTitle(en, 'VILLAGE_LOST', summary(ben, tom), 8)).toBe('Benhausen conquered Tomburg');
    expect(reportTitle(en, 'CONQUEST', summary(ben, barbarians), 7)).toBe('Benhausen conquered Barbarian village');
  });
});

describe('villageName', () => {
  it('names barbarian villages', () => {
    expect(villageName(en, barbarians)).toBe('Barbarian village');
    expect(villageName(en, tom)).toBe('Tomburg');
  });
});

describe('sendError (M40)', () => {
  it('names the player the send stopped at', () => {
    const error = new ApiError(409, 'INBOX_FULL', 'full', [], null, null, 'Anna');
    expect(sendError(en, error)).toBe(
      'Anna has too many unread reports from you. Wait until they read some. Nothing was sent.',
    );
    expect(sendError(en, new ApiError(404, 'PLAYER_NOT_FOUND', 'x', [], null, null, 'Bob'))).toContain(
      'There is no player called Bob.',
    );
  });

  it('falls back to the plain message without a name', () => {
    expect(sendError(en, new ApiError(429, 'MESSAGE_RATE_LIMIT', 'slow'))).toBe(en.errors.MESSAGE_RATE_LIMIT);
  });
});
