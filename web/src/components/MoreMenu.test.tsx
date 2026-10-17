// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MoreMenu } from './MoreMenu';

function setup() {
  const edit = vi.fn();
  const remove = vi.fn();
  render(
    <>
      <MoreMenu
        label="More for Tomburg"
        items={[
          { label: 'Edit', icon: 'pencil', onSelect: edit },
          { label: 'Remove', icon: 'trash', danger: true, onSelect: remove },
        ]}
      />
      <p>elsewhere</p>
    </>,
  );
  return { edit, remove, button: screen.getByRole('button', { name: 'More for Tomburg' }) };
}

const item = (name: string) => screen.queryByRole('button', { name });

describe('MoreMenu', () => {
  afterEach(cleanup);

  it('shows its items after a tap and runs the chosen one', () => {
    const { edit, remove, button } = setup();
    expect(item('Edit')).toBeNull();
    expect(button.getAttribute('aria-expanded')).toBe('false');

    fireEvent.click(button);
    expect(button.getAttribute('aria-expanded')).toBe('true');
    fireEvent.click(item('Remove')!);

    expect(remove).toHaveBeenCalledOnce();
    expect(edit).not.toHaveBeenCalled();
    expect(item('Remove')).toBeNull();
    // not lost with the list: a sheet the item opens returns the focus here when it closes
    expect(document.activeElement).toBe(button);
  });

  it('closes on Escape (focus back on the button), a tap elsewhere and a second tap', () => {
    const { button } = setup();

    fireEvent.click(button);
    fireEvent.keyDown(window, { key: 'Escape' });
    expect(item('Edit')).toBeNull();
    expect(document.activeElement).toBe(button);

    fireEvent.click(button);
    fireEvent.pointerDown(screen.getByText('elsewhere'));
    expect(item('Edit')).toBeNull();

    fireEvent.click(button);
    fireEvent.click(button);
    expect(item('Edit')).toBeNull();
  });

  it('opens above the button when there is no room below it', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(400);
    const at = (top: number) => () => ({ top, bottom: top + 44, left: 300, right: 344 }) as DOMRect;

    button.getBoundingClientRect = at(100);
    fireEvent.click(button);
    expect(item('Edit')!.parentElement!.style).toMatchObject({ top: '148px', right: '56px' });
    fireEvent.click(button);

    button.getBoundingClientRect = at(700);
    fireEvent.click(button);
    expect(item('Edit')!.parentElement!.style).toMatchObject({ bottom: '104px', right: '56px' });
  });
});
