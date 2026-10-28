// @vitest-environment jsdom
import { cleanup, render as renderPlain, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';
import { Button } from '../components/Button';
import { IconButton } from '../components/IconButton';
import { I18nProvider } from '../i18n/I18nProvider';
import { ActionsStayOn } from './ActionsStayOn';
import { RouteError } from './RouteStates';
import { WorldEndContext } from './worldEnd';

function buttons() {
  return (
    <>
      <Button type="button">Recruit</Button>
      <Button type="button" variant="secondary">
        Details
      </Button>
      <IconButton icon="close" variant="danger" label="Cancel" />
      <IconButton icon="locate" label="Centre" />
    </>
  );
}

/** The provider shows its children once the texts of the language have loaded. */
async function render(children: ReactNode) {
  const { container } = renderPlain(<I18nProvider>{children}</I18nProvider>);
  await waitFor(() => expect(container.childElementCount).toBeGreaterThan(0));
}

function renderEnded(children: ReactNode) {
  return render(<WorldEndContext value={{ ended: true, frozen: true }}>{children}</WorldEndContext>);
}

const button = (name: string) => screen.getByRole<HTMLButtonElement>('button', { name });

describe('the frozen buttons of an ended world', () => {
  afterEach(cleanup);

  it('turns the game actions and the cancels off and leaves the buttons that only show something', async () => {
    await renderEnded(buttons());

    expect(button('Recruit').disabled).toBe(true);
    expect(button('Recruit').title).not.toBe('');
    expect(button('Details').disabled).toBe(false);
    expect(button('Cancel').disabled).toBe(true);
    expect(button('Centre').disabled).toBe(false);
  });

  it('keeps the actions on in the parts that stay open, and only there', async () => {
    await renderEnded(
      <>
        <ActionsStayOn>
          <Button type="button">Send</Button>
        </ActionsStayOn>
        <ActionsStayOn open={false}>
          <Button type="button">Attack</Button>
        </ActionsStayOn>
      </>,
    );

    expect(button('Send').disabled).toBe(false);
    expect(button('Attack').disabled).toBe(true);
  });

  it('keeps a retry on, since reading again changes nothing in the world', async () => {
    await renderEnded(<RouteError />);

    expect(button('Try again').disabled).toBe(false);
  });

  it('leaves everything on while the world runs', async () => {
    await render(buttons());

    expect(button('Recruit').disabled).toBe(false);
    expect(button('Cancel').disabled).toBe(false);
  });
});
