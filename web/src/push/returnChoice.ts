import { useState } from 'react';
import { usePushSettings } from '../api/queries/push';

/** Which "Notify me when they're back" switch: a manual attack (or train), or a farm list run. */
export type ReturnChoice = 'attack' | 'farmRun';

const storageKeys: Record<ReturnChoice, string> = {
  attack: 'bannerfall.push.notifyReturn',
  farmRun: 'bannerfall.push.notifyRun',
};

/** The switch as the player left it last time on this device (1.6); off at first. */
export function rememberedChoice(choice: ReturnChoice): boolean {
  try {
    return localStorage.getItem(storageKeys[choice]) === 'on';
  } catch {
    return false;
  }
}

export function rememberChoice(choice: ReturnChoice, on: boolean): void {
  try {
    if (on) {
      localStorage.setItem(storageKeys[choice], 'on');
    } else {
      localStorage.removeItem(storageKeys[choice]);
    }
  } catch {
    // private mode: the switch starts off next time
  }
}

/** The switch's state, starting the way it was left last time; every change is remembered. */
export function useRememberedChoice(choice: ReturnChoice): [boolean, (on: boolean) => void] {
  const [on, setOn] = useState(() => rememberedChoice(choice));
  return [
    on,
    (next: boolean) => {
      setOn(next);
      rememberChoice(choice, next);
    },
  ];
}

/**
 * Whether to offer "Notify me when they're back" at all: only to a player who gets push on at
 * least one device in a world that sends it (the toast alone is no reason for the switch).
 */
export function useOffersReturnPush(): boolean {
  const settings = usePushSettings();
  return settings.data !== undefined && settings.data.available && settings.data.devices > 0;
}
