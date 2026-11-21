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

  it('opens to the right under a "…" at the left of the window, so the edge does not cut it off', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(400);
    button.getBoundingClientRect = () => ({ top: 100, bottom: 144, left: 60, right: 104 }) as DOMRect;

    fireEvent.click(button);

    const style = item('Edit')!.parentElement!.style;
    expect(style).toMatchObject({ top: '148px', left: '60px' });
    expect(style.right).toBe('');
  });

  it('slides a list that would end past the right edge back into the window', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(320);
    button.getBoundingClientRect = () => ({ top: 100, bottom: 144, left: 84, right: 128 }) as DOMRect;
    // jsdom has no layout: the list is 238 px wide, as a long German label makes it
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect');
    rect.mockImplementation(function (this: HTMLElement) {
      const left = Number.parseFloat(this.style.left);
      return (
        this.style.left === ''
          ? { left: 0, right: 0, width: 0, top: 0, bottom: 0 }
          : { left, right: left + 238, width: 238, top: 0, bottom: 0 }
      ) as DOMRect;
    });

    fireEvent.click(button);

    // 320 - 8 - 238 = 74
    expect(item('Edit')!.parentElement!.style.left).toBe('74px');
    rect.mockRestore();
  });

  it('settles when the list is wider than the window or still drawn at its old place', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(320);
    button.getBoundingClientRect = () => ({ top: 100, bottom: 144, left: 84, right: 128 }) as DOMRect;
    // 400 px wide: never fits; a transition keeps it drawn at 84 px for a moment (reduced motion: 0.01 ms)
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect');
    rect.mockImplementation(function (this: HTMLElement) {
      const left = this.style.left === '' ? 0 : 84;
      return { left, right: left + 400, width: 400, top: 0, bottom: 0 } as DOMRect;
    });

    fireEvent.click(button);

    expect(item('Edit')!.parentElement!.style.left).toBe('8px');
    rect.mockRestore();
  });

  it('slides a list that opens to the left of a "…" near the left edge back into the window', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(800);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(320);
    // the "…" of a wrapped button row, right of the middle: the list opens to the left of it
    button.getBoundingClientRect = () => ({ top: 100, bottom: 144, left: 215, right: 259 }) as DOMRect;
    const rect = vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect');
    rect.mockImplementation(() => ({ left: 0, right: 290, width: 290, top: 0, bottom: 0 }) as DOMRect);

    fireEvent.click(button);

    // right 61 would start the 290 px list at -31: 320 - 8 - 290 = 22
    expect(item('Edit')!.parentElement!.style.right).toBe('22px');
    rect.mockRestore();
  });

  it('gives a list taller than the room on both sides the larger side, scrolling, and keeps it open', () => {
    const { button } = setup();
    vi.spyOn(document.documentElement, 'clientHeight', 'get').mockReturnValue(100);
    vi.spyOn(document.documentElement, 'clientWidth', 'get').mockReturnValue(400);
    // two items need 104 px; 60 px above the button, 0 below
    button.getBoundingClientRect = () => ({ top: 60, bottom: 100, left: 300, right: 344 }) as DOMRect;

    fireEvent.click(button);

    const list = item('Edit')!.parentElement!;
    expect(list.style).toMatchObject({ bottom: '44px', maxHeight: '56px', overflowY: 'auto' });
    fireEvent.scroll(list);
    expect(item('Edit')).not.toBeNull();
    fireEvent.scroll(window);
    expect(item('Edit')).toBeNull();
  });
});
