import { describe, expect, it } from 'vitest';
import type { NpcDifficultyValues } from '../../api/queries/admin';
import { customFromForm, customToForm } from './customForm';

const normal: NpcDifficultyValues = {
  productionFactor: 0.9,
  boostBelow: 0.5,
  boostFactor: 1.25,
  leadMargin: 1.25,
  attacksPerPlayerPerDay: 4,
  attacksPerHumanPerDay: 8,
  quietHours: true,
  spareLastVillage: true,
  buildQuietFrom: '23:00',
  buildQuietTo: '07:00',
};

describe('customFromForm', () => {
  it('gives the values back after a round trip through the form', () => {
    expect(customFromForm(customToForm(normal))).toEqual(normal);
  });

  it('accepts a boost threshold of 0 (the boost is off)', () => {
    expect(customFromForm({ ...customToForm(normal), boostBelow: '0' })?.boostBelow).toBe(0);
  });

  it('refuses a factor of 0 or less, an empty field and text', () => {
    for (const productionFactor of ['0', '-1', '', 'abc']) {
      expect(customFromForm({ ...customToForm(normal), productionFactor })).toBeNull();
    }
  });

  it('refuses caps that are negative or not whole numbers', () => {
    expect(customFromForm({ ...customToForm(normal), attacksPerHumanPerDay: '-1' })).toBeNull();
    expect(customFromForm({ ...customToForm(normal), attacksPerPlayerPerDay: '2.5' })).toBeNull();
    expect(customFromForm({ ...customToForm(normal), attacksPerPlayerPerDay: '0' })?.attacksPerPlayerPerDay).toBe(0);
  });

  it('refuses times that are not HH:mm', () => {
    expect(customFromForm({ ...customToForm(normal), buildQuietFrom: '' })).toBeNull();
    expect(customFromForm({ ...customToForm(normal), buildQuietTo: '7:00' })).toBeNull();
  });
});
