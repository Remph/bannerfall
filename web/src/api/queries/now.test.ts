import { QueryClient } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';
import { followWhatFeedsNow, nowQueryKey } from './now';

describe('followWhatFeedsNow', () => {
  function setUp() {
    const queryClient = new QueryClient();
    followWhatFeedsNow(queryClient);
    queryClient.setQueryData(nowQueryKey, { villages: [] });
    queryClient.setQueryData(['village', 1], {});
    queryClient.setQueryData(['troops', 1], {});
    queryClient.setQueryData(['recruitment', 1], []);
    queryClient.setQueryData(['reports', 'unread'], 0);
    // what the list costs: each call asks the server again
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const invalidated = () => invalidate.mock.calls.filter(([filters]) => filters?.queryKey?.[0] === 'now').length;
    return { queryClient, invalidated };
  }

  it('invalidates the list once when a mutation invalidates several of the queries it is made of', async () => {
    const { queryClient, invalidated } = setUp();
    void queryClient.invalidateQueries({ queryKey: ['village', 1] });
    void queryClient.invalidateQueries({ queryKey: ['troops'] });
    void queryClient.invalidateQueries({ queryKey: ['recruitment', 1] });
    await Promise.resolve();
    expect(invalidated()).toBe(1);
    expect(queryClient.getQueryState(nowQueryKey)?.isInvalidated).toBe(true);
  });

  it('follows again on the next change', async () => {
    const { queryClient, invalidated } = setUp();
    void queryClient.invalidateQueries({ queryKey: ['village', 1] });
    await Promise.resolve();
    void queryClient.invalidateQueries({ queryKey: ['troops', 1] });
    await Promise.resolve();
    const onePerChange = 2;
    expect(invalidated()).toBe(onePerChange);
  });

  it('leaves the list alone for queries it is not made of', async () => {
    const { queryClient, invalidated } = setUp();
    void queryClient.invalidateQueries({ queryKey: ['reports'] });
    await Promise.resolve();
    expect(invalidated()).toBe(0);
  });
});
