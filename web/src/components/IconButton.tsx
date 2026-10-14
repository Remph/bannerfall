import type { ButtonHTMLAttributes } from 'react';
import { Icon, type IconName } from './Icon';
import styles from './IconButton.module.css';

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  icon: IconName;
  /** What the button does – read by screen readers and shown as tooltip (the icon has no text). */
  label: string;
  variant?: 'normal' | 'danger';
};

/** A round button with only an icon, big enough for a thumb (44 × 44 px). */
export function IconButton({ icon, label, variant = 'normal', className, ...rest }: IconButtonProps) {
  const classes = [styles.button, variant === 'danger' && styles.danger, className].filter(Boolean).join(' ');
  return (
    <button type="button" className={classes} aria-label={label} title={label} {...rest}>
      <Icon name={icon} />
    </button>
  );
}
