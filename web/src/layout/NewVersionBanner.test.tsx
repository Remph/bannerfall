// @vitest-environment jsdom
import { act, cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { announceNewVersion, forgetNewVersion } from '../api/newVersion';
import { serverVersionQueryKey } from '../api/queries/world';
import { renderPage } from '../testing/renderPage';
import { NewVersionBanner } from './NewVersionBanner';

function show() {
  renderPage({
    path: '/',
    url: '/',
    element: <NewVersionBanner />,
    data: [[serverVersionQueryKey, '1.6.2']],
  });
}

describe('NewVersionBanner', () => {
  afterEach(() => {
    cleanup();
    forgetNewVersion();
  });

  it('says nothing until the API client has seen a newer patch', () => {
    show();

    expect(screen.queryByRole('status')).toBeNull();
  });

  it('offers a reload once it has', () => {
    show();

    act(() => announceNewVersion('1.6.3'));

    expect(screen.getByRole('status').textContent).toContain('1.6.3');
    expect(screen.getByRole('button', { name: 'Reload' })).toBeTruthy();
  });

  it('stays hidden after it was closed, until another version comes', () => {
    show();
    act(() => announceNewVersion('1.6.3'));
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('status')).toBeNull();

    act(() => announceNewVersion('1.6.4'));

    expect(screen.getByRole('status').textContent).toContain('1.6.4');
  });
});
