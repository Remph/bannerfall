/** Same limit as the server (RenameVillageRequest, SpawnService.MAX_NAME_LENGTH). */
export const MAX_VILLAGE_NAME_LENGTH = 32;

export type VillageNameProblem = 'EMPTY' | 'TOO_LONG';

/** Checks the name the way the server will see it: with spaces at both ends removed. */
export function validateVillageName(name: string): VillageNameProblem | null {
  const trimmed = name.trim();
  if (trimmed === '') {
    return 'EMPTY';
  }
  // .length counts UTF-16 units, exactly like Java's String.length() behind @Size
  return trimmed.length > MAX_VILLAGE_NAME_LENGTH ? 'TOO_LONG' : null;
}
