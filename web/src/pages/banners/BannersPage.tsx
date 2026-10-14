import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router';
import { errorMessage } from '../../api/errors';
import {
  useBanners,
  useEquipBanner,
  useFuseBanners,
  useUnequipBanner,
  type BannerVillage,
  type Banners,
  type OwnBanner,
} from '../../api/queries/banners';
import { useVillage } from '../../api/queries/villages';
import { Banner } from '../../components/Banner';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import {
  FUSE_COUNT,
  effectOfTier,
  effectiveTier,
  fusableByTier,
  fuseTypes,
  percentPoints,
  stashOf,
  toggleFuseChoice,
  type BannerLike,
} from '../../game/banners';
import { formatNumber } from '../../game/format';
import type { BannerType } from '../../game/gameData';
import { useI18n } from '../../i18n';
import { parseVillageId } from '../../layout/currentVillage';
import { recruitPath } from '../recruit/recruitPaths';
import { VillageError } from '../village/VillageError';
import styles from './Banners.module.css';
import { bannersPath } from './bannerPaths';

/**
 * /v/:villageId/banners – the Banner Hall (M32a, 02-game-rules §21): the banner that flies in this
 * village, the stash of the player and his other villages. Reached from the village overview only;
 * it has no tab of its own.
 */
export function BannersPage() {
  const villageId = parseVillageId(useParams().villageId);
  if (villageId === undefined) {
    return <Navigate to="/" replace />;
  }
  // `key`: another village gets a fresh page
  return <BannersScreen key={villageId} villageId={villageId} />;
}

function BannersScreen({ villageId }: { villageId: number }) {
  const { t } = useI18n();
  const village = useVillage(villageId);
  const banners = useBanners();
  if (village.isError) {
    return <VillageError error={village.error} onRetry={() => void village.refetch()} />;
  }
  if (banners.isError) {
    return <VillageError error={banners.error} onRetry={() => void banners.refetch()} />;
  }
  if (village.isPending || banners.isPending) {
    return <p>{t.common.loading}</p>;
  }
  const here = banners.data.villages.find((entry) => entry.id === villageId);
  if (here === undefined) {
    // not one of his villages (the village query would have said so first)
    return <Navigate to="/" replace />;
  }
  return <BannersView villageId={villageId} data={banners.data} here={here} />;
}

/** "10 %" or "4,5 %": the effect of a tier as the texts show it. */
function usePercent() {
  const { language } = useI18n();
  return (fraction: number) => `${formatNumber(percentPoints(fraction), language)} %`;
}

function BannersView({ villageId, data, here }: { villageId: number; data: Banners; here: BannerVillage }) {
  const { t } = useI18n();
  const s = t.banners;
  const equip = useEquipBanner(villageId);
  const unequip = useUnequipBanner(villageId);
  const stash = stashOf(data.banners);
  const flying = data.banners.find((banner) => banner.villageId === villageId) ?? null;
  const busy = equip.isPending || unequip.isPending;
  const error = equip.error ?? unequip.error;

  return (
    <div className={styles.page}>
      <header className={styles.head}>
        <Link className={styles.back} to={`/v/${villageId}`}>
          {s.back}
        </Link>
        <h1>{s.title}</h1>
        <p className={styles.hint}>
          {here.hallLevel === 0 ? s.notBuilt : s.hallLevel(here.hallLevel, data.hallMaxLevel)}
        </p>
        {here.hallLevel === 0 && (
          <p>
            <Link className={styles.link} to={{ pathname: `/v/${villageId}/hq`, hash: 'BANNER_HALL' }}>
              {s.toTownHall}
            </Link>
          </p>
        )}
        <p className={styles.hint}>{data.enabled ? s.hallExplain : s.disabled}</p>
      </header>

      <Card title={s.slotTitle}>
        <div className={styles.body}>
          <Slot data={data} here={here} flying={flying} busy={busy} onUnequip={() => unequip.mutate()} />
          {here.locked && <p className={styles.warning}>{s.locked}</p>}
          {error !== null && <ErrorMessage>{errorMessage(t, error)}</ErrorMessage>}
        </div>
      </Card>

      <Card title={s.stashTitle(stash.length)}>
        <div className={styles.body}>
          <p className={styles.hint}>{s.stashExplain}</p>
        </div>
        {stash.length === 0 ? (
          <p className={styles.empty}>{s.stashEmpty}</p>
        ) : (
          <ul className={styles.stash}>
            {stash.map((banner) => (
              <StashRow
                key={banner.id}
                banner={banner}
                data={data}
                here={here}
                swapping={flying !== null}
                pending={equip.isPending && equip.variables === banner.id}
                disabled={busy || !data.enabled || here.hallLevel === 0 || here.locked}
                onEquip={() => equip.mutate(banner.id)}
              />
            ))}
          </ul>
        )}
      </Card>

      {data.enabled && <FuseCard stash={stash} />}

      {data.enabled && (
        <Card title={s.thievesTitle}>
          <div className={styles.body}>
            <p className={styles.hint}>{s.thievesExplain}</p>
            {here.hallLevel > 0 ? (
              <p>
                <Link className={styles.link} to={recruitPath(villageId, 'BANNER_HALL')}>
                  {s.toRecruit}
                </Link>
              </p>
            ) : (
              <p className={styles.hint}>{s.thievesNeedHall}</p>
            )}
          </div>
        </Card>
      )}

      <Card title={s.villagesTitle}>
        <ul className={styles.villages}>
          {data.villages.map((entry) => (
            <VillageRow key={entry.id} entry={entry} data={data} current={entry.id === villageId} />
          ))}
        </ul>
      </Card>
    </div>
  );
}

/** What the banner in this village is and does, or the empty slot. */
function Slot({
  data,
  here,
  flying,
  busy,
  onUnequip,
}: {
  data: Banners;
  here: BannerVillage;
  flying: OwnBanner | null;
  busy: boolean;
  onUnequip: () => void;
}) {
  const { t } = useI18n();
  const s = t.banners;
  const percent = usePercent();
  if (flying === null) {
    return (
      <div className={styles.slot}>
        <div className={styles.emptySlot} aria-hidden="true" />
        <div className={styles.slotText}>
          <strong>{s.empty}</strong>
          <span className={styles.hint}>{here.hallLevel === 0 ? s.needsHall : s.emptyHint}</span>
        </div>
      </div>
    );
  }
  const type = flying.type as BannerType;
  // the server's number: it is also 0 while the host has banners switched off
  const effective = here.effectiveTier;
  return (
    <div className={styles.slot}>
      <Banner type={type} tier={flying.tier} size={56} dormant={effective === 0} />
      <div className={styles.slotText}>
        <strong>{s.types[type]}</strong>
        <span>{s.tier(flying.tier)}</span>
        {effective === 0 ? (
          <span className={styles.hint}>{s.sleeping}</span>
        ) : (
          <>
            <span>{s.effects[type](percent(effectOfTier(data.effects, type, effective)))}</span>
            <span className={styles.hint}>{effective < flying.tier ? s.cappedAt(effective) : s.working}</span>
          </>
        )}
      </div>
      <Button type="button" size="small" variant="secondary" disabled={busy || here.locked} onClick={onUnequip}>
        {s.unequip}
      </Button>
    </div>
  );
}

interface StashRowProps {
  banner: OwnBanner;
  data: Banners;
  here: BannerVillage;
  swapping: boolean;
  pending: boolean;
  disabled: boolean;
  onEquip: () => void;
}

/** One banner of the stash: what it does at its tier, what it would do in this village's hall, and the button. */
function StashRow({ banner, data, here, swapping, pending, disabled, onEquip }: StashRowProps) {
  const { t } = useI18n();
  const s = t.banners;
  const percent = usePercent();
  const type = banner.type as BannerType;
  const effective = effectiveTier(banner.tier, here.hallLevel);
  const name = s.types[type];
  return (
    <li className={styles.stashRow}>
      <Banner type={type} tier={banner.tier} size={72} />
      <div className={styles.stashText}>
        <strong>{name}</strong>
        <span>{s.tier(banner.tier)}</span>
        <span>{s.effects[type](percent(effectOfTier(data.effects, type, banner.tier)))}</span>
        {here.hallLevel > 0 && effective < banner.tier && <span className={styles.hint}>{s.cappedAt(effective)}</span>}
      </div>
      <Button
        type="button"
        size="small"
        disabled={disabled}
        aria-label={swapping ? s.swapLabel(name) : s.equipLabel(name)}
        onClick={onEquip}
      >
        {pending ? s.equipping : swapping ? s.swap : s.equip}
      </Button>
    </li>
  );
}

/**
 * Fusing (M32c, 02-game-rules §21.4.2): choose three stash banners of one tier, pick the type the
 * new one takes, confirm. The banners are buttons that toggle (`aria-pressed`), grouped by tier.
 */
function FuseCard({ stash }: { stash: OwnBanner[] }) {
  const { t } = useI18n();
  const s = t.banners;
  const fuse = useFuseBanners();
  const [chosen, setChosen] = useState<BannerLike[]>([]);
  const [type, setType] = useState<BannerType | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [done, setDone] = useState<{ type: BannerType; tier: number } | null>(null);
  const groups = fusableByTier(stash.map(asBannerLike));
  // a chosen banner that left the stash (fused, raised, stolen) is no longer chosen
  const present = chosen.filter((entry) => stash.some((banner) => banner.id === entry.id));
  const types = fuseTypes(present);
  const pickedType = type !== null && types.includes(type) ? type : (types[0] ?? null);
  // the type the new banner takes once three are chosen; null until then
  const target = present.length === FUSE_COUNT ? pickedType : null;
  const newTier = (present[0]?.tier ?? 0) + 1;

  function toggle(banner: BannerLike) {
    setChosen(toggleFuseChoice(present, banner));
    setDone(null);
  }

  function confirm() {
    if (target === null) {
      return;
    }
    fuse.mutate(
      { bannerIds: present.map((banner) => banner.id), type: target },
      {
        onSuccess: () => {
          setDone({ type: target, tier: newTier });
          setChosen([]);
          setType(null);
          setConfirming(false);
        },
      },
    );
  }

  const anyGroup = groups.some((group) => group.banners.length >= FUSE_COUNT);
  return (
    <Card title={s.fuseTitle}>
      <div className={styles.body}>
        <p className={styles.hint}>{s.fuseExplain}</p>
        {done !== null && (
          <p className={styles.success} role="status">
            {s.fused(s.types[done.type], done.tier)}
          </p>
        )}
        {!anyGroup ? (
          <p className={styles.hint}>{s.fuseNone}</p>
        ) : (
          <>
            <p className={styles.hint}>{s.fusePick(present.length, FUSE_COUNT)}</p>
            {groups.map((group) => (
              <div key={group.tier} className={styles.fuseGroup}>
                <h3 className={styles.fuseTier}>{s.fuseTier(group.tier, group.banners.length)}</h3>
                {group.banners.length < FUSE_COUNT ? (
                  <p className={styles.hint}>{s.fuseTooFew(FUSE_COUNT)}</p>
                ) : (
                  <ul className={styles.fuseList}>
                    {group.banners.map((banner) => {
                      const selected = present.some((entry) => entry.id === banner.id);
                      const name = s.types[banner.type];
                      return (
                        <li key={banner.id}>
                          <button
                            type="button"
                            className={selected ? `${styles.fuseChoice} ${styles.fuseChosen}` : styles.fuseChoice}
                            aria-pressed={selected}
                            aria-label={s.selectLabel(s.label(name, banner.tier))}
                            onClick={() => toggle(banner)}
                          >
                            <Banner type={banner.type} tier={banner.tier} size={40} />
                            <span>{name}</span>
                          </button>
                        </li>
                      );
                    })}
                  </ul>
                )}
              </div>
            ))}
            {present.length === FUSE_COUNT && (
              <fieldset className={styles.fuseTypes}>
                <legend>{s.fuseTypeLegend}</legend>
                {types.map((option) => (
                  <label key={option} className={styles.fuseType}>
                    <input
                      type="radio"
                      name="fuse-type"
                      value={option}
                      checked={option === pickedType}
                      onChange={() => setType(option)}
                    />
                    <Banner type={option} tier={newTier} size={20} />
                    {s.types[option]}
                  </label>
                ))}
              </fieldset>
            )}
            <div>
              <Button type="button" disabled={target === null || fuse.isPending} onClick={() => setConfirming(true)}>
                {target !== null ? s.fuseButton(s.types[target], newTier) : s.fuseTitle}
              </Button>
            </div>
          </>
        )}
        {!confirming && fuse.error !== null && <ErrorMessage>{errorMessage(t, fuse.error)}</ErrorMessage>}
      </div>
      {target !== null && (
        <ConfirmSheet
          open={confirming}
          title={s.fuseConfirmTitle}
          text={s.fuseConfirmText(s.types[target], newTier)}
          confirmLabel={fuse.isPending ? s.fusing : s.fuseConfirm}
          pending={fuse.isPending}
          error={fuse.error}
          onConfirm={confirm}
          onClose={() => {
            setConfirming(false);
            fuse.reset();
          }}
        />
      )}
    </Card>
  );
}

/** The server's banner with its type as the client's union (the schema says string). */
function asBannerLike(banner: OwnBanner): BannerLike {
  return { id: banner.id, type: banner.type as BannerType, tier: banner.tier, villageId: banner.villageId ?? null };
}

/** One of the player's villages: its hall, its banner, and a link to its page. */
function VillageRow({ entry, data, current }: { entry: BannerVillage; data: Banners; current: boolean }) {
  const { t } = useI18n();
  const s = t.banners;
  const banner = data.banners.find((candidate) => candidate.id === entry.bannerId) ?? null;
  const content = (
    <>
      {banner === null ? (
        <span className={styles.noBanner} aria-hidden="true" />
      ) : (
        <Banner type={banner.type as BannerType} tier={banner.tier} size={20} dormant={entry.effectiveTier === 0} />
      )}
      <span className={styles.villageName}>{entry.name}</span>
      <span className={styles.hint}>
        {banner === null ? s.noBanner : s.types[banner.type as BannerType]} · {s.hallShort(entry.hallLevel)}
      </span>
    </>
  );
  return (
    <li className={current ? `${styles.villageRow} ${styles.currentRow}` : styles.villageRow}>
      {current ? (
        <span className={styles.villageLink} aria-current="page">
          {content}
        </span>
      ) : (
        <Link className={styles.villageLink} to={bannersPath(entry.id)} aria-label={s.open(entry.name)}>
          {content}
        </Link>
      )}
    </li>
  );
}
