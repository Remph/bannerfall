import type { ButtonHTMLAttributes } from 'react';
import styles from './Button.module.css';

// All normal <button> props (onClick, disabled, type …) plus our own `variant` and `size`.
type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  /**
   * `danger`: looks like `secondary` and turns red when pointed at (hover, keyboard focus, press) –
   * for buttons that undo something, e.g. cancelling a command.
   */
  variant?: 'primary' | 'secondary' | 'danger';
  /** `small` for buttons repeated in lists: lower with a mouse, still 44 px tall on touch screens. */
  size?: 'normal' | 'small';
};

export function Button({ variant = 'primary', size = 'normal', className, ...rest }: ButtonProps) {
  const classes = [styles.button, styles[variant], size === 'small' && styles.small, className]
    .filter(Boolean)
    .join(' ');
  return <button className={classes} {...rest} />;
}
