import type { InputHTMLAttributes } from 'react';
import styles from './Switch.module.css';

type SwitchProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'role' | 'onChange' | 'className'> & {
  checked: boolean;
  onChange: (checked: boolean) => void;
};

/**
 * The sliding on/off switch (1.6), one for the whole game (1.7 design review: the settings rows,
 * push settings, automation, the setup and the admin's world tab each had a copy of it). A real
 * checkbox with role="switch", so the label around it, the keyboard and screen readers work.
 */
export function Switch({ checked, onChange, ...rest }: SwitchProps) {
  return (
    <input
      type="checkbox"
      role="switch"
      className={styles.switch}
      checked={checked}
      onChange={(event) => onChange(event.target.checked)}
      {...rest}
    />
  );
}
