// @vitest-environment jsdom
import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Link } from 'react-router';
import { renderPage } from '../testing/renderPage';
import { OPENS_AT_TOP } from './opensAtTop';
import { ScrollToTop } from './ScrollToTop';

function show() {
  renderPage({
    path: '*',
    url: '/v/1/smithy',
    element: (
      <>
        <ScrollToTop />
        <Link to="/v/1" state={OPENS_AT_TOP}>
          Village
        </Link>
        <Link to="/v/1/smithy">Plain</Link>
      </>
    ),
    data: [],
  });
}

describe('ScrollToTop', () => {
  const scrollTo = vi.fn();

  beforeEach(() => {
    window.scrollTo = scrollTo;
  });

  afterEach(() => {
    cleanup();
    scrollTo.mockReset();
  });

  it('scrolls to the top after a link that opens its page at the top', () => {
    show();
    expect(scrollTo).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('link', { name: 'Village' }));

    expect(scrollTo).toHaveBeenCalledTimes(1);
    expect(scrollTo).toHaveBeenCalledWith({ top: 0, left: 0, behavior: 'instant' });
  });

  it('leaves the position alone after any other link', () => {
    show();

    fireEvent.click(screen.getByRole('link', { name: 'Plain' }));

    expect(scrollTo).not.toHaveBeenCalled();
  });
});
