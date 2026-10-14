import { useEffect, useEffectEvent, useId, useRef } from 'react';
import { Link } from 'react-router';
import type { MapMovement, MapVillage } from '../../api/queries/map';
import type { Village } from '../../api/queries/villages';
import { IconButton } from '../../components/IconButton';
import { distance } from '../../game/coords';
import { formatCoords, formatDecimal, formatDateTime, formatNumber, formatWhenExact } from '../../game/format';
import { bracketTag } from '../../game/guilds';
import type { Cover, VillageKind } from '../../game/mapView';
import { useI18n } from '../../i18n';
import { movementMarkers } from '../../game/mapMovements';
import { serverNow } from '../../game/serverTime';
import { mapActions, type MapAction } from './mapActions';
import styles from './MapPage.module.css';
import { MarkerIcon } from './MarkerIcon';

interface VillagePanelProps {
  village: MapVillage;
  /** The player's own movements to this village (M26), if any and if the markers are shown. */
  movement?: MapMovement;
  kind: VillageKind;
  /** The village of the URL: distances are measured from it, attacks will start there. */
  currentVillage: Village;
  onClose: () => void;
  /** The part of the map the panel covers, whenever it appears or changes its size (not when docked). */
  onCover: (cover: Cover) => void;
  /** Desktop: a card in the map's side column instead of floating over the map (M11b). */
  docked?: boolean;
}

/**
 * The tapped village (05-frontend §6): across the bottom of the map on phones, a card at the bottom
 * left on tablets, a card in the side column on the desktop. Not modal – the map stays usable and
 * another tap picks another village.
 */
export function VillagePanel({
  village,
  movement,
  kind,
  currentVillage,
  onClose,
  onCover,
  docked = false,
}: VillagePanelProps) {
  const { t, language } = useI18n();
  const titleId = useId();
  const ref = useRef<HTMLElement>(null);
  const isCurrent = village.id === currentVillage.id;
  const actions = mapActions(village, kind, currentVillage.id);
  const ready = actions.filter((action): action is Required<MapAction> => action.to !== undefined);
  const later = actions.filter((action) => action.to === undefined);

  // useEffectEvent: always calls the latest onCover, without restarting the observer on every render.
  const reportCover = useEffectEvent((panel: HTMLElement) =>
    onCover({ top: panel.offsetTop, right: panel.offsetLeft + panel.offsetWidth }),
  );
  // Report again for every village: the map moves it out from under the panel if needed.
  useEffect(() => {
    const panel = ref.current;
    if (panel === null || docked) {
      return; // in the side column it covers nothing
    }
    const observer = new ResizeObserver(() => reportCover(panel));
    observer.observe(panel);
    return () => observer.disconnect();
  }, [village.id, docked]);

  return (
    <section
      ref={ref}
      className={docked ? `${styles.panel} ${styles.panelDocked}` : styles.panel}
      aria-labelledby={titleId}
      // Escape closes the panel wherever the focus is in it (the map's own Escape needs the canvas focused)
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          onClose();
        }
      }}
    >
      <header className={styles.panelHead}>
        <span className={styles.swatch} data-kind={kind} aria-hidden="true" />
        <h2 id={titleId} className={styles.panelTitle}>
          {village.name ?? t.map.barbarianVillage}{' '}
          <span className={styles.coords}>{formatCoords(village.x, village.y)}</span>
        </h2>
        <IconButton icon="close" label={t.common.close} onClick={onClose} />
      </header>
      <dl className={styles.facts}>
        <div>
          <dt>{t.map.owner}</dt>
          <dd>
            {village.guildTag != null && <>{bracketTag(village.guildTag)} </>}
            {village.playerName ?? t.map.noOwner}
            {kind === 'own' && <span className={styles.tag}>{t.map.youTag}</span>}
            {kind === 'npc' && <span className={styles.tag}>{t.map.npcTag}</span>}
          </dd>
        </div>
        <div>
          <dt>{t.map.points}</dt>
          <dd>{formatNumber(village.points, language)}</dd>
        </div>
        {/* barbarians included: nobles take them too (02-game-rules §10). Other villages show what the
            player saw when he last attacked them, or nothing (M21). */}
        <div>
          <dt>{t.map.loyalty}</dt>
          <dd>
            {village.loyalty === null ? (
              <span className={styles.tag}>{t.map.loyaltyUnknown}</span>
            ) : (
              <>
                {formatNumber(village.loyalty, language)}
                {village.loyaltySeenAt !== null && (
                  <span className={styles.tag}>
                    {t.map.loyaltySeen(formatDateTime(Date.parse(village.loyaltySeenAt), language))}
                  </span>
                )}
              </>
            )}
          </dd>
        </div>
        <div>
          <dt>{t.map.distance}</dt>
          <dd>
            {isCurrent
              ? t.map.currentVillage
              : t.map.fields(formatDecimal(distance(currentVillage, village), language))}
          </dd>
        </div>
        {movement !== undefined && movement.nextArriveAt !== null && (
          // the markers of the canvas in words (M26): screen readers and phones get them here
          <div>
            <dt>{t.map.movements.onTheWay}</dt>
            <dd>
              <span className={styles.movementList}>
                {movementMarkers(movement).map((marker) => (
                  <span key={marker.kind}>
                    <MarkerIcon kind={marker.kind} />
                    {marker.kind === 'support'
                      ? t.map.movements.supports(marker.count)
                      : t.map.movements.attacks(marker.count)}
                    {marker.kind === 'noble' && ` ${t.map.movements.withNobles(movement.nobleAttacks)}`}
                  </span>
                ))}
              </span>
              <span className={styles.tag}>
                {t.map.movements.next(formatWhenExact(Date.parse(movement.nextArriveAt), serverNow(), language))}
              </span>
            </dd>
          </div>
        )}
        {movement !== undefined && movement.nextReturnAt !== null && (
          // the return badge in words (M36), with when the first troops are back
          <div>
            <dt>{t.map.movements.comingHome}</dt>
            <dd>
              <span className={styles.movementList}>
                <span>
                  <MarkerIcon kind="return" />
                  {t.map.movements.returns(movement.returns)}
                </span>
              </span>
              <span className={styles.tag}>
                {t.map.movements.nextHome(formatWhenExact(Date.parse(movement.nextReturnAt), serverNow(), language))}
              </span>
            </dd>
          </div>
        )}
      </dl>
      {village.loyalty === null && <p className={styles.hint}>{t.map.loyaltyUnknownHint}</p>}
      {kind === 'barbarian' && <p className={styles.hint}>{t.map.barbarianHint}</p>}
      {ready.length > 0 && (
        <div className={styles.actions}>
          {ready.map((action) => (
            <Link key={action.key} to={action.to} className={styles.action}>
              {t.map.actions[action.key]}
            </Link>
          ))}
        </div>
      )}
      {later.length > 0 && (
        // actions whose screen is not there (a village without an owner id): named, not yet buttons
        <p className={styles.later}>{t.map.comingSoon(later.map((action) => t.map.actions[action.key]).join(' · '))}</p>
      )}
    </section>
  );
}
