import type { ButtonHTMLAttributes } from 'react';
import { useI18n } from '../i18n';
import { useActionsFrozen } from '../layout/worldEnd';
import styles from './Button.module.css';

// All normal <button> props (onClick, disabled, type …) plus our own `variant` and `size`.
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /**
   * `danger`: looks like `secondary` and turns red when pointed at (hover, keyboard focus, press) –
   * for buttons that undo something, e.g. cancelling a command.
   * `attack`: solid red like `primary` – for sending an attack, the one place a main action is red.
   */
  variant?: 'primary' | 'secondary' | 'danger' | 'attack';
  /** `small` for buttons repeated in lists: lower with a mouse, still 44 px tall on touch screens. */
  size?: 'normal' | 'small';
};

export function Button({ variant = 'primary', size = 'normal', className, ...rest }: ButtonProps) {
  const { t } = useI18n();
  const frozen = useActionsFrozen();
  const classes = [styles.button, styles[variant], size === 'small' && styles.small, className]
    .filter(Boolean)
    .join(' ');
  // An ended world is frozen (v1.3): the game's actions are off, except the parts that stay open
  // (ActionsStayOn). Secondary buttons stay: they open, show or close something. The strip under the
  // top bar says why; the title and the description repeat it for a pointer and a screen reader.
  if (frozen && variant !== 'secondary') {
    return (
      <button className={classes} {...rest} disabled title={t.worldEnd.frozen} aria-description={t.worldEnd.frozen} />
    );
  }
  return <button className={classes} {...rest} />;
}
