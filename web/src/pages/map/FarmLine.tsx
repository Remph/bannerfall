import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router';
import { errorMessage } from '../../api/errors';
import { useCurrentAccount } from '../../api/queries/auth';
import { troopsOfChoice, useAddFarmTarget, useFarmTargets } from '../../api/queries/farm';
import { useFarmTemplates } from '../../api/queries/farmTemplates';
import type { MapVillage } from '../../api/queries/map';
import { useCommandPreviews, useSendCommand, type CommandRequest } from '../../api/queries/troops';
import { useWorld } from '../../api/queries/world';
import type { Village } from '../../api/queries/villages';
import { Button } from '../../components/Button';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon } from '../../components/Icon';
import { Sheet } from '../../components/Sheet';
import { farmSendEndsProtection, fitsHome, hasUnits } from '../../game/farm';
import { unitsOfChoice, type FarmTemplate, type TemplateLetter } from '../../game/farmTemplates';
import { formatCoords, formatFactor, formatWhenExact } from '../../game/format';
import { formatMorale } from '../../game/morale';
import { nightBonusAt } from '../../game/nightBonus';
import { serverNow } from '../../game/serverTime';
import type { UnitMap } from '../../game/troops';
import { useI18n, useT } from '../../i18n';
import { TemplateButtons, TemplateLetterBadge } from '../farm/TemplateButtons';
import { TemplateChoice } from '../farm/TemplateChoice';
import { farmPath } from '../farm/farmPaths';
import styles from './MapPage.module.css';

/** How long a template button waits for the second tap (like the farm list's Send). */
const ARMED_MS = 4000;
/**
 * A template tap this soon after the panel opened is the second tap of a double tap on the village,
 * not a pick (1.6): the first one opened the panel with a button under the finger.
 */
const OPENING_GUARD_MS = 500;

/**
 * The panel's farm line (1.4) for a village that isn't the player's: one button per template sends
 * it as an attack from the current village right away, and the village goes onto the current
 * village's farm list through a sheet over the map, so the map stays where it is.
 */
export function FarmLine({ village, currentVillage }: { village: MapVillage; currentVillage: Village }) {
  const { t, language } = useI18n();
  const me = useCurrentAccount();
  const templateQuery = useFarmTemplates();
  const templates = templateQuery.data ?? [];
  const targets = useFarmTargets(currentVillage.id).data;
  const send = useSendCommand(currentVillage.id);
  const [armed, setArmed] = useState<TemplateLetter>();
  const [adding, setAdding] = useState(false);
  // keyed by the village: mounted when the panel opens for it
  const openedAt = useRef<number>(undefined);
  const listed = targets?.find((target) => target.target.id === village.id);
  const endsProtection = farmSendEndsProtection(Date.parse(me.protectedUntil), village.playerId, serverNow());
  const home = currentVillage.unitsHome;
  const short = templates.filter((template) => !fitsHome(template.units, home));
  // the night bonus (1.4, 02 §8.1): which templates would arrive in it, sent now; the server's travel
  // times (Haste included). Only asked for at an owned village in a world with the bonus.
  // Morale (1.6, 02 §8.2) comes with the same previews: one for all templates (it depends on the points only).
  const world = useWorld().data;
  const night = world?.nightBonus;
  const sendable = templates.filter((template) => fitsHome(template.units, home));
  const previews = useCommandPreviews(
    currentVillage.id,
    sendable.map((template) => attackOf(village, template)),
    (night != null || world?.moraleFloor != null) && village.playerId !== null,
  );
  const morale = previews.find((preview) => preview.data?.morale != null)?.data?.morale;
  const nightLetters = sendable
    .filter((_, index) => {
      const travelSeconds = previews[index]?.data?.travelSeconds;
      return travelSeconds != null && nightBonusAt(night, serverNow() + travelSeconds * 1000, true) !== null;
    })
    .map((template) => template.letter);

  useEffect(() => {
    if (armed === undefined) {
      return;
    }
    const timer = setTimeout(() => setArmed(undefined), ARMED_MS);
    return () => clearTimeout(timer);
  }, [armed]);

  useEffect(() => {
    openedAt.current = performance.now();
  }, []);

  function onPick(template: FarmTemplate) {
    if (openedAt.current === undefined || performance.now() - openedAt.current < OPENING_GUARD_MS) {
      return;
    }
    if (endsProtection && armed !== template.letter) {
      setArmed(template.letter);
      return;
    }
    setArmed(undefined);
    send.mutate(attackOf(village, template));
  }

  return (
    <section className={styles.farmLine} aria-label={t.farm.farmWith}>
      <p className={styles.farmLineHead}>{t.farm.farmWith}</p>
      {templateQuery.isPending ? null : templates.length === 0 ? (
        <p className={styles.farmLineNote}>
          {t.farm.noTemplates} <Link to={`${farmPath(currentVillage.id)}#templates`}>{t.farm.createTemplates}</Link>
        </p>
      ) : (
        <TemplateButtons
          templates={templates}
          home={home}
          needsHome
          armed={armed}
          armedLabel={t.farm.sendAnyway}
          disabled={send.isPending}
          label={t.farm.sendTemplate}
          onPick={onPick}
        />
      )}
      {endsProtection && <p className={styles.farmLineNote}>{t.rally.endsProtection}</p>}
      {night != null && nightLetters.length > 0 && (
        <p className={`${styles.farmLineNote} ${styles.farmLineNight}`}>
          <Icon name="moon" /> {t.farm.nightBonusWith(nightLetters.join(', '), formatFactor(night.factor, language))}
        </p>
      )}
      {morale != null && <p className={styles.farmLineNote}>{t.rally.morale(formatMorale(morale, language))}</p>}
      {short.length > 0 && !send.isSuccess && <p className={styles.farmLineNote}>{t.farm.templatesShort}</p>}
      {send.isSuccess && (
        <p className={styles.farmLineSent} role="status">
          <Icon name="check" /> {t.farm.sent(formatWhenExact(Date.parse(send.data.arriveAt), serverNow(), language))}
        </p>
      )}
      {send.isError && <ErrorMessage>{errorMessage(t, send.error)}</ErrorMessage>}
      {listed !== undefined ? (
        <Link className={styles.farmListed} to={farmPath(currentVillage.id)}>
          <TemplateLetterBadge
            letter={listed.template}
            label={listed.template === null ? t.farm.ownUnits : t.farm.templateLabel(listed.template)}
          />
          {t.farm.onList}
          <Icon name="chevronRight" />
        </Link>
      ) : (
        <button type="button" className={styles.farmAdd} onClick={() => setAdding(true)}>
          <Icon name="FARM" />
          {t.map.actions.farm}
        </button>
      )}
      <AddToFarmSheet
        key={String(adding)}
        open={adding}
        village={village}
        currentVillage={currentVillage}
        templates={templates}
        onClose={() => setAdding(false)}
      />
    </section>
  );
}

/** The attack a template button sends. */
function attackOf(village: MapVillage, template: FarmTemplate): CommandRequest {
  return {
    kind: 'ATTACK',
    target: { x: village.x, y: village.y },
    units: template.units as Record<string, number>,
    catapultTarget: null,
    notifyReturn: null,
  };
}

interface AddToFarmSheetProps {
  open: boolean;
  village: MapVillage;
  currentVillage: Village;
  templates: FarmTemplate[];
  onClose: () => void;
}

/** "Zur Farmliste" on the map (1.4): pick the troops, add, and the map is still there. */
function AddToFarmSheet({ open, village, currentVillage, templates, onClose }: AddToFarmSheetProps) {
  const t = useT();
  const add = useAddFarmTarget(currentVillage.id);
  const [letter, setLetter] = useState<TemplateLetter | null>(templates[0]?.letter ?? null);
  const [counts, setCounts] = useState<UnitMap>({});
  const units = unitsOfChoice(letter, templates, counts);
  const name = `${village.name ?? t.map.barbarianVillage} ${formatCoords(village.x, village.y)}`;
  return (
    <Sheet open={open} onClose={onClose} title={t.farm.addSheetTitle(name)}>
      <div className={styles.farmSheet}>
        <p className={styles.farmLineNote}>{t.farm.listOf(currentVillage.name)}</p>
        <TemplateChoice
          templates={templates}
          value={letter}
          onChange={setLetter}
          counts={counts}
          onCounts={(type, count) => setCounts((current) => ({ ...current, [type]: count }))}
          home={currentVillage.unitsHome}
        />
        {add.isError && <ErrorMessage>{errorMessage(t, add.error)}</ErrorMessage>}
        <div className={styles.farmSheetActions}>
          <Button type="button" variant="secondary" onClick={onClose}>
            {t.common.cancel}
          </Button>
          <Button
            type="button"
            disabled={!hasUnits(units) || add.isPending}
            onClick={() =>
              add.mutate(
                { target: { x: village.x, y: village.y }, troops: troopsOfChoice(letter, counts) },
                { onSuccess: onClose },
              )
            }
          >
            {t.farm.add}
          </Button>
        </div>
      </div>
    </Sheet>
  );
}
