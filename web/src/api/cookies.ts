/** Reads one cookie from a `document.cookie`-style string ("a=1; b=2"). */
export function readCookie(cookies: string, name: string): string | undefined {
  for (const part of cookies.split(';')) {
    const [key, ...rest] = part.trim().split('=');
    if (key === name) {
      return decodeURIComponent(rest.join('='));
    }
  }
  return undefined;
}
