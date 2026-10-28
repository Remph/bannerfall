// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest';
import type { VillageSummary } from '../api/queries/villages';
import {
  cleanVillageTag,
  loadVillageTagFilter,
  saveVillageTagFilter,
  tagOfVillage,
  usedVillageTags,
  villagesWithTag,
} from './villageTags';

function summary(id: number, tag: string | null): VillageSummary {
  return { id, name: `Dorf ${id}`, x: id, y: id, points: 100, incomingAttacks: 0, tag };
}

const villages = [summary(1, 'OFF'), summary(2, null), summary(3, 'DEF'), summary(4, 'OFF')];

describe('cleanVillageTag', () => {
  it('writes capitals, keeps letters and digits only and stops at five characters', () => {
    expect(cleanVillageTag('off')).toBe('OFF');
    expect(cleanVillageTag(' d-e f2 ')).toBe('DEF2');
    expect(cleanVillageTag('äö!')).toBe('');
    expect(cleanVillageTag('abcdefgh')).toBe('ABCDE');
  });
});

describe('the tags of the villages', () => {
  it('lists each tag in use once, alphabetically', () => {
    expect(usedVillageTags(villages)).toEqual(['DEF', 'OFF']);
    expect(usedVillageTags([summary(1, null)])).toEqual([]);
  });

  it('filters by a tag, and keeps all villages without one', () => {
    expect(villagesWithTag(villages, 'OFF').map((village) => village.id)).toEqual([1, 4]);
    expect(villagesWithTag(villages, null)).toHaveLength(4);
    expect(villagesWithTag(villages, 'XYZ')).toEqual([]);
  });

  it('finds the tag of one village', () => {
    expect(tagOfVillage(villages, 3)).toBe('DEF');
    expect(tagOfVillage(villages, 2)).toBeNull();
    expect(tagOfVillage(villages, 99)).toBeNull();
  });
});

describe('the remembered chip', () => {
  afterEach(() => sessionStorage.clear());

  it('is kept for the browser session and cleared by "All"', () => {
    expect(loadVillageTagFilter()).toBeNull();
    saveVillageTagFilter('OFF');
    expect(loadVillageTagFilter()).toBe('OFF');
    saveVillageTagFilter(null);
    expect(loadVillageTagFilter()).toBeNull();
  });
});
