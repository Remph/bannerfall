// @vitest-environment jsdom
import { act, cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { Icon } from './Icon';
import { ICON_STYLE_STORAGE_KEY, getIconStyle, parseIconStyle, setIconStyle } from './iconStyle';
import { MEDIEVAL_ICONS } from './medievalIcons';

describe('Icon: medieval or classic icons (1.6)', () => {
  afterEach(() => {
    cleanup();
    act(() => setIconStyle('medieval'));
  });

  it('shows the medieval silhouette by default', () => {
    const { container } = render(<Icon name="BARRACKS" />);
    const svg = container.querySelector('svg');
    expect(getIconStyle()).toBe('medieval');
    expect(svg?.dataset.iconStyle).toBe('medieval');
    expect(svg?.querySelector('path')?.getAttribute('d')).toBe(MEDIEVAL_ICONS.BARRACKS);
  });

  it('keeps the resource colours', () => {
    const { container } = render(<Icon name="wood" />);
    expect(container.querySelector('path')?.getAttribute('fill')).toBe('#9a6a3a');
  });

  it('switches every icon on the page to the classic drawing at once, and back', () => {
    const { container } = render(
      <>
        <Icon name="BARRACKS" />
        <Icon name="SPEARMAN" />
      </>,
    );
    act(() => setIconStyle('classic'));
    expect(container.querySelectorAll('svg[data-icon-style="medieval"]')).toHaveLength(0);
    expect(localStorage.getItem(ICON_STYLE_STORAGE_KEY)).toBe('classic');
    act(() => setIconStyle('medieval'));
    expect(container.querySelectorAll('svg[data-icon-style="medieval"]')).toHaveLength(2);
    expect(localStorage.getItem(ICON_STYLE_STORAGE_KEY)).toBeNull();
  });

  it('keeps the classic drawing for icons without a medieval one (chevrons, close …)', () => {
    const { container } = render(<Icon name="chevronRight" />);
    expect(container.querySelector('svg')?.dataset.iconStyle).toBeUndefined();
    expect(container.querySelector('svg')?.getAttribute('viewBox')).toBe('0 0 24 24');
  });

  it('reads anything but "classic" as medieval', () => {
    expect(parseIconStyle('classic')).toBe('classic');
    expect(parseIconStyle(null)).toBe('medieval');
    expect(parseIconStyle('pixel')).toBe('medieval');
  });
});
