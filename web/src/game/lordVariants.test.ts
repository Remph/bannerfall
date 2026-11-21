import { describe, expect, it } from 'vitest';
import { isFemaleLordName } from './lordVariants';

describe('isFemaleLordName', () => {
  it('knows the female ranks of the lord names', () => {
    for (const name of ['Gräfin Hedda', 'Lady Rowena', 'Dame Isolde', 'Baronin Adela']) {
      expect(isFemaleLordName(name)).toBe(true);
    }
    for (const name of ['Graf Ottmar', 'Lord Aldric', 'Baron Wulfram', 'Sir Edmund', 'Ritter Kuno', 'Ladybird']) {
      expect(isFemaleLordName(name)).toBe(false);
    }
  });
});
