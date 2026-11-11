import { describe, expect, it } from 'vitest';
import { edgeAt, opensOnRelease, reveal, startsDrag } from './nowSwipe';

describe('startsDrag', () => {
  it('needs a vertical move past the dead zone', () => {
    expect(startsDrag(0, 4, false)).toBe(false);
    expect(startsDrag(0, 12, false)).toBe(true);
  });
  it('ignores mostly sideways moves', () => {
    expect(startsDrag(30, 14, false)).toBe(false);
    expect(startsDrag(5, 14, false)).toBe(true);
  });
  it('opens downwards and closes upwards only', () => {
    expect(startsDrag(0, -20, false)).toBe(false);
    expect(startsDrag(0, 20, true)).toBe(false);
    expect(startsDrag(0, -20, true)).toBe(true);
  });
});

describe('edgeAt', () => {
  it('follows the finger inside the sheet', () => {
    expect(edgeAt(0, 120, 400)).toBe(120);
    expect(edgeAt(400, -150, 400)).toBe(250);
  });
  it('never leaves 0…height', () => {
    expect(edgeAt(0, -50, 400)).toBe(0);
    expect(edgeAt(300, 500, 400)).toBe(400);
  });
});

describe('opensOnRelease', () => {
  it('opens after a pull of 30% (at most 110 px)', () => {
    expect(opensOnRelease(false, 100, 300, 0)).toBe(true); // 30% of 300 = 90
    expect(opensOnRelease(false, 80, 300, 0)).toBe(false);
    expect(opensOnRelease(false, 111, 600, 0)).toBe(true); // capped at 110
    expect(opensOnRelease(false, 100, 600, 0)).toBe(false);
  });
  it('opens on a flick down, however short', () => {
    expect(opensOnRelease(false, 20, 300, 0.5)).toBe(true);
    expect(opensOnRelease(false, 20, 300, 0.2)).toBe(false);
  });
  it('closes after a pull up of a quarter (at most 60 px) or a flick up', () => {
    expect(opensOnRelease(true, 200, 300, 0)).toBe(false); // 100 px up, quarter is 75
    expect(opensOnRelease(true, 270, 300, 0)).toBe(true); // 30 px up
    expect(opensOnRelease(true, 290, 300, -0.5)).toBe(false);
    expect(opensOnRelease(true, 400, 600, 0)).toBe(false); // 200 px up, capped at 60
  });
});

describe('reveal', () => {
  it('runs from 0 to 1 and is 0 without a height', () => {
    expect(reveal(0, 300)).toBe(0);
    expect(reveal(150, 300)).toBe(0.5);
    expect(reveal(400, 300)).toBe(1);
    expect(reveal(10, 0)).toBe(0);
  });
});
