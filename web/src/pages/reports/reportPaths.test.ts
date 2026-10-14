import { describe, expect, it } from 'vitest';
import { pageFromSearch, parseReportId, reportPath, reportsPath } from './reportPaths';

describe('reportsPath', () => {
  it('leaves the first page without a parameter and counts from 1 in the URL', () => {
    expect(reportsPath(0)).toBe('/reports');
    expect(reportsPath(2)).toBe('/reports?page=3');
  });
});

describe('pageFromSearch', () => {
  it('reads the 1-based page and gives the 0-based one', () => {
    expect(pageFromSearch(new URLSearchParams('page=3'))).toBe(2);
    expect(pageFromSearch(new URLSearchParams('page=1'))).toBe(0);
  });

  it('falls back to the first page', () => {
    expect(pageFromSearch(new URLSearchParams(''))).toBe(0);
    expect(pageFromSearch(new URLSearchParams('page=0'))).toBe(0);
    expect(pageFromSearch(new URLSearchParams('page=-2'))).toBe(0);
    expect(pageFromSearch(new URLSearchParams('page=abc'))).toBe(0);
    expect(pageFromSearch(new URLSearchParams('page=999999'))).toBe(0);
  });

  it('stops at the server’s highest page', () => {
    expect(pageFromSearch(new URLSearchParams('page=10001'))).toBe(10_000);
    expect(pageFromSearch(new URLSearchParams('page=20000'))).toBe(10_000);
  });

  it('is the inverse of reportsPath', () => {
    const path = reportsPath(4);
    expect(pageFromSearch(new URLSearchParams(path.split('?')[1]))).toBe(4);
  });
});

describe('reportPath / parseReportId', () => {
  it('builds and reads the detail path', () => {
    expect(reportPath(12)).toBe('/reports/12');
    expect(parseReportId('12')).toBe(12);
    expect(parseReportId('0')).toBeUndefined();
    expect(parseReportId('12a')).toBeUndefined();
    expect(parseReportId(undefined)).toBeUndefined();
  });
});
