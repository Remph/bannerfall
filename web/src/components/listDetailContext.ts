import { createContext, useContext } from 'react';

/** True inside the detail column of a `ListDetail`: the page drops its back link, the list is next to it. */
export const InListDetailContext = createContext(false);

export function useInListDetail(): boolean {
  return useContext(InListDetailContext);
}
