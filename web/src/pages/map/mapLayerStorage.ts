import { parseMapLayer, type MapLayer } from '../../game/politicalMap';

/** Which layer the overview map shows, stored per device (M15c). The owner colours are the default. */
const STORAGE_KEY = 'bannerfall.mapLayer';

export function loadMapLayer(): MapLayer {
  try {
    return parseMapLayer(localStorage.getItem(STORAGE_KEY));
  } catch {
    return 'owner'; // storage blocked (private mode)
  }
}

export function saveMapLayer(layer: MapLayer): void {
  try {
    if (layer === 'guild') {
      localStorage.setItem(STORAGE_KEY, layer);
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // ignore: the choice still holds until the page is reloaded
  }
}
