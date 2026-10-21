import { useState } from 'react';

/** The rally point's movement cards the player can fold away (1.6.1). */
export type MovesCard = 'incoming' | 'outgoing';

const storageKeys: Record<MovesCard, string> = {
  incoming: 'bannerfall.rally.foldIncoming',
  outgoing: 'bannerfall.rally.foldOutgoing',
};

/** Whether the player left the card folded last time on this device; open at first. */
export function rememberedFold(card: MovesCard): boolean {
  try {
    return localStorage.getItem(storageKeys[card]) === 'folded';
  } catch {
    return false;
  }
}

function rememberFold(card: MovesCard, folded: boolean): void {
  try {
    if (folded) {
      localStorage.setItem(storageKeys[card], 'folded');
    } else {
      localStorage.removeItem(storageKeys[card]);
    }
  } catch {
    // private mode: the card starts open next time
  }
}

/** The card an address opens: `#incoming`, `#outgoing`. */
export function cardOfHash(hash: string): MovesCard | undefined {
  return hash === '#incoming' ? 'incoming' : hash === '#outgoing' ? 'outgoing' : undefined;
}

/**
 * Which cards are folded, as the player left them (remembered on this device), and the card an
 * address asks for, which stays open however it was left until the player folds it again.
 */
export function useFoldedCards(asked: MovesCard | undefined): {
  folded: (card: MovesCard) => boolean;
  toggle: (card: MovesCard) => void;
  ask: (card: MovesCard) => void;
} {
  const [stored, setStored] = useState<Record<MovesCard, boolean>>(() => ({
    incoming: rememberedFold('incoming'),
    outgoing: rememberedFold('outgoing'),
  }));
  const [peek, setPeek] = useState<MovesCard | undefined>(asked);
  const folded = (card: MovesCard) => stored[card] && peek !== card;
  return {
    folded,
    toggle: (card) => {
      const next = !folded(card);
      setPeek(undefined);
      setStored({ ...stored, [card]: next });
      rememberFold(card, next);
    },
    ask: setPeek,
  };
}
