import type { NpcDifficultyValues } from '../../api/queries/admin';

/** The CUSTOM values as the form holds them: numbers as the text in the field. */
export interface CustomForm {
  productionFactor: string;
  boostBelow: string;
  boostFactor: string;
  leadMargin: string;
  attacksPerPlayerPerDay: string;
  attacksPerHumanPerDay: string;
  quietHours: boolean;
  spareLastVillage: boolean;
  buildQuietFrom: string;
  buildQuietTo: string;
}

export function customToForm(values: NpcDifficultyValues): CustomForm {
  return {
    productionFactor: String(values.productionFactor),
    boostBelow: String(values.boostBelow),
    boostFactor: String(values.boostFactor),
    leadMargin: String(values.leadMargin),
    attacksPerPlayerPerDay: String(values.attacksPerPlayerPerDay),
    attacksPerHumanPerDay: String(values.attacksPerHumanPerDay),
    quietHours: values.quietHours,
    spareLastVillage: values.spareLastVillage,
    buildQuietFrom: values.buildQuietFrom,
    buildQuietTo: values.buildQuietTo,
  };
}

/** Parses a field that must be a finite number of at least `min`; null if it isn't. */
function number(text: string, min: number, integer = false): number | null {
  if (text.trim() === '') {
    return null;
  }
  const value = Number(text);
  if (!Number.isFinite(value) || value < min || (integer && !Number.isInteger(value))) {
    return null;
  }
  return value;
}

const TIME = /^\d\d:\d\d$/;

/**
 * The values to send, or null while a field is not valid (the same ranges the server checks, 09 §8:
 * factors above 0, the boost threshold and the caps not negative, caps whole numbers, times HH:mm).
 */
export function customFromForm(form: CustomForm): NpcDifficultyValues | null {
  const productionFactor = number(form.productionFactor, Number.MIN_VALUE);
  const boostBelow = number(form.boostBelow, 0);
  const boostFactor = number(form.boostFactor, Number.MIN_VALUE);
  const leadMargin = number(form.leadMargin, Number.MIN_VALUE);
  const attacksPerPlayerPerDay = number(form.attacksPerPlayerPerDay, 0, true);
  const attacksPerHumanPerDay = number(form.attacksPerHumanPerDay, 0, true);
  if (
    productionFactor === null ||
    boostBelow === null ||
    boostFactor === null ||
    leadMargin === null ||
    attacksPerPlayerPerDay === null ||
    attacksPerHumanPerDay === null ||
    !TIME.test(form.buildQuietFrom) ||
    !TIME.test(form.buildQuietTo)
  ) {
    return null;
  }
  return {
    productionFactor,
    boostBelow,
    boostFactor,
    leadMargin,
    attacksPerPlayerPerDay,
    attacksPerHumanPerDay,
    quietHours: form.quietHours,
    spareLastVillage: form.spareLastVillage,
    buildQuietFrom: form.buildQuietFrom,
    buildQuietTo: form.buildQuietTo,
  };
}
