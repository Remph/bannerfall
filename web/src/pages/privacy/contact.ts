/** Whether the host's contact (BANNERFALL_OPERATOR_CONTACT) is a plain email address, shown as a mail link. */
export function isEmailAddress(contact: string): boolean {
  return /^[^\s@<>"]+@[^\s@<>"]+\.[^\s@<>"]+$/.test(contact);
}
