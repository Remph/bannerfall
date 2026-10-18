import type { UnitMap } from './troops';

/** A player's farm templates (1.4): up to five, named by letter, shared by all his villages. */
export const templateLetters = ['A', 'B', 'C', 'D', 'E'] as const;
export type TemplateLetter = (typeof templateLetters)[number];

export interface FarmTemplate {
  letter: TemplateLetter;
  units: UnitMap;
}

/** The first letter the player hasn't used yet (the one "Create template D" offers); none at five. */
export function nextFreeLetter(templates: readonly FarmTemplate[]): TemplateLetter | undefined {
  return templateLetters.find((letter) => !templates.some((template) => template.letter === letter));
}

/**
 * What a farm list entry sends for a choice: the template's units, or the own counts for `null`
 * ({} for a template that is gone, so nothing can be added with it).
 */
export function unitsOfChoice(
  letter: TemplateLetter | null,
  templates: readonly FarmTemplate[],
  counts: UnitMap,
): UnitMap {
  if (letter === null) {
    return counts;
  }
  return templates.find((template) => template.letter === letter)?.units ?? {};
}
