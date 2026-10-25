// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { I18nContext } from '../i18n';
import { en } from '../i18n/en';
import { Sheet } from './Sheet';

function Nested({ onOuterClose }: { onOuterClose: () => void }) {
  const [inner, setInner] = useState(true);
  return (
    <Sheet open onClose={onOuterClose} title="Outer">
      <button type="button" onClick={() => setInner(true)}>
        Open inner
      </button>
      <Sheet open={inner} onClose={() => setInner(false)} title="Inner">
        <button type="button" onClick={() => setInner(false)}>
          Close inner
        </button>
      </Sheet>
    </Sheet>
  );
}

describe('Sheet', () => {
  beforeAll(() => {
    // jsdom has no modal dialogs
    HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
      this.open = false;
      this.dispatchEvent(new Event('close'));
    };
  });

  afterEach(cleanup);

  it('stays open when a sheet inside it closes (the plan editor after "Use this plan")', () => {
    const onOuterClose = vi.fn();
    render(
      <I18nContext value={{ language: 'en', t: en, setLanguage: () => {} }}>
        <Nested onOuterClose={onOuterClose} />
      </I18nContext>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Close inner', hidden: true }));

    expect(onOuterClose).not.toHaveBeenCalled();
  });
});
