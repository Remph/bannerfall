import { useState } from 'react';
import { loadChatDockOpen, saveChatDockOpen } from './chatDockStorage';

/**
 * Whether the desktop's chat panel is open, and the way to change it. The choice is remembered.
 * `onMap`: on the map it starts closed, so it doesn't cover the side column (M11b review); opening
 * or closing it there isn't remembered, the other pages keep the stored choice.
 */
export function useChatDock(onMap: boolean): [boolean, (open: boolean) => void] {
  const [open, setOpenState] = useState(() => !onMap && loadChatDockOpen());
  // Entering or leaving the map: adjust while rendering (react.dev "Storing information from previous renders").
  const [wasOnMap, setWasOnMap] = useState(onMap);
  if (onMap !== wasOnMap) {
    setWasOnMap(onMap);
    setOpenState(!onMap && loadChatDockOpen());
  }
  function setOpen(next: boolean) {
    setOpenState(next);
    if (!onMap) {
      saveChatDockOpen(next);
    }
  }
  return [open, setOpen];
}
