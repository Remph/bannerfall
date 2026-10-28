import { useState } from 'react';
import { errorMessage } from '../../api/errors';
import { useGuildBanner, useRaiseBanner, type GuildBanner } from '../../api/queries/guilds';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { ConfirmSheet } from '../../components/ConfirmSheet';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Icon, type IconName } from '../../components/Icon';
import { percentPoints } from '../../game/banners';
import { formatNumber, formatShare, formatWhen } from '../../game/format';
import { useServerNow } from '../../game/serverTime';
import { useI18n } from '../../i18n';
import { GuildBannerDonate } from './GuildBannerDonate';
import styles from './Guild.module.css';

const effectIcons: Record<GuildBanner['effects'][number]['kind'], IconName> = {
  PRODUCTION: 'effectProduction',
  SUPPORT: 'effectSupport',
  DEFENCE: 'effectDefence',
};

/**
 * The guild banner (1.7, 02-game-rules §16.4) for the members: the level with its three effects, the
 * fund towards the next level, who donated. One lead button: Donate while the fund lacks something,
 * Raise level once it covers the price (blocked for 24 hours after a member left).
 */
export function GuildBannerCard() {
  const { t, language } = useI18n();
  const labels = t.guild.banner;
  const query = useGuildBanner(true);
  const raise = useRaiseBanner();
  const now = useServerNow();
  const [donating, setDonating] = useState(false);
  const [raising, setRaising] = useState(false);

  if (query.isError) {
    return (
      <Card title={labels.title}>
        <div className={styles.body}>
          <ErrorMessage>{errorMessage(t, query.error)}</ErrorMessage>
        </div>
      </Card>
    );
  }
  const banner = query.data;
  if (banner === undefined || !banner.enabled) {
    return null;
  }

  const format = (amount: number) => formatNumber(amount, language);
  const percent = (fraction: number, sign: string) => `${sign}${formatShare(percentPoints(fraction), language)}`;
  const top = banner.level >= banner.maxLevel;
  const lockedUntil = banner.lockedUntil === null ? null : Date.parse(banner.lockedUntil);
  const locked = lockedUntil !== null && lockedUntil > now;
  const covered = !top && banner.missing === 0;
  const percentFull = top || banner.price === 0 ? 100 : Math.min(100, Math.floor((banner.fund / banner.price) * 100));

  return (
    <Card title={labels.title}>
      <div className={styles.body}>
        <p className={styles.hint}>{labels.hint}</p>
        <div className={styles.bannerHead}>
          <span className={styles.bannerArt} aria-hidden="true">
            <Icon name="banner" />
          </span>
          <div className={styles.bannerLevel}>
            <strong>{labels.level(banner.level, banner.maxLevel)}</strong>
            <span className={styles.pips} aria-hidden="true">
              {Array.from({ length: banner.maxLevel }, (_, index) => (
                <span key={index} className={index < banner.level ? `${styles.pip} ${styles.pipOn}` : styles.pip} />
              ))}
            </span>
          </div>
        </div>
        <ul className={styles.effects}>
          {banner.effects.map((effect) => {
            const reached = effect.level <= banner.level;
            const sign = effect.kind === 'SUPPORT' ? '' : '+';
            return (
              <li key={effect.level} className={reached ? `${styles.effect} ${styles.effectOn}` : styles.effect}>
                <span className={styles.effectIcon}>
                  <Icon name={effectIcons[effect.kind]} />
                </span>
                <span className={styles.effectText}>
                  <span>{labels.effects[effect.kind](percent(effect.bonus, sign))}</span>
                  <span className={styles.rowSub}>
                    {labels.levelOf(effect.level)}
                    {!reached && <> · {labels.pricePerMember(format(banner.memberPrices[effect.level - 1] ?? 0))}</>}
                  </span>
                </span>
              </li>
            );
          })}
        </ul>
        {top ? (
          <p className={styles.hint}>{labels.full}</p>
        ) : (
          <div className={styles.fund}>
            <div className={styles.fundLine}>
              <span className={styles.fundName}>
                <Icon name="coins" />
                {labels.fund}
              </span>
              <strong>{labels.fundOf(format(banner.fund), format(banner.price))}</strong>
            </div>
            <div
              className={styles.bar}
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={percentFull}
              aria-label={labels.fund}
            >
              <div className={styles.fill} style={{ width: `${percentFull}%` }} />
            </div>
            <p className={styles.rowSub}>
              {labels.priceSum(format(banner.memberPrices[banner.level] ?? 0), banner.members)}
              {covered && <> · {labels.fundFull}</>}
            </p>
          </div>
        )}
        {locked && <p className={styles.lockNote}>{labels.locked(formatWhen(lockedUntil, now, language))}</p>}
        {raise.isError && !raising && <ErrorMessage>{errorMessage(t, raise.error)}</ErrorMessage>}
        {!top && (
          <div className={styles.buttons}>
            {covered ? (
              !locked && (
                <Button type="button" onClick={() => setRaising(true)}>
                  {labels.raise}
                </Button>
              )
            ) : (
              <Button type="button" onClick={() => setDonating(true)}>
                {labels.donate}
              </Button>
            )}
          </div>
        )}
        <h3 className={styles.subTitleFlush}>{labels.donors}</h3>
        {banner.donors.length === 0 ? (
          <p className={styles.hint}>{labels.noDonors}</p>
        ) : (
          <ul className={styles.donors}>
            {banner.donors.map((donor) => (
              <li key={donor.playerId}>
                <span className={styles.rowName}>{donor.name}</span>
                <span className={styles.rowSub}>{format(donor.amount)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <GuildBannerDonate banner={banner} open={donating} onClose={() => setDonating(false)} />
      <ConfirmSheet
        open={raising}
        title={labels.raiseTitle(banner.level + 1)}
        text={labels.raiseText(format(banner.price))}
        confirmLabel={labels.raise}
        pending={raise.isPending}
        error={raise.error}
        onConfirm={() => raise.mutate(undefined, { onSuccess: () => setRaising(false) })}
        onClose={() => {
          setRaising(false);
          raise.reset();
        }}
      />
    </Card>
  );
}
