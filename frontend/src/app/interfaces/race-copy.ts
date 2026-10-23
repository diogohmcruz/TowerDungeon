import { ContenderStatus } from './race';

/** Why entry to a Trial was refused, in plain words. The key is the server's refusal name. */
const REFUSALS: Record<string, string> = {
  UNKNOWN_RACE: 'No such Race is held.',
  NOT_OPEN: 'The Season is not open.',
  NOT_YET_OPEN: 'The Season has not opened.',
  CLOSED: 'The Season is closed.',
  SPRINT_NOT_OPEN: 'This Race is not a Season. It cannot be entered here.',
  PLAN_MISMATCH: 'The Tower has changed since this Season was set. It cannot be entered.',
  CONFIG_MISMATCH: 'The balance has changed since this Season was set. It cannot be entered.',
  NO_ATTEMPTS: 'Every attempt has been used.',
  ATTEMPT_OPEN: 'An attempt is still being run.',
  FULL: 'The Tower holds all the Contenders it allows. Try again later.',
};

export function refusalText(name: string | null | undefined): string {
  return (name && REFUSALS[name]) || 'The Trial was refused.';
}

/** How an attempt ended, in words (state is never by colour alone). */
export function statusText(status: ContenderStatus): string {
  switch (status) {
    case 'RACING':
      return 'Still on the Tower';
    case 'FINISHED':
      return 'Reached the Finish Floor';
    case 'TIMED_OUT':
      return 'The clock ran out';
    case 'DEFEATED':
      return 'No one was left to climb';
  }
}

/** Minutes and seconds; minutes are not folded into hours. */
export function clock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  return `${Math.floor(whole / 60)}:${String(whole % 60).padStart(2, '0')}`;
}

/** 1st, 2nd, 3rd, 4th, 11th, 12th, 13th, 21st. */
export function ordinal(n: number): string {
  const tens = n % 100;
  const suffix =
    tens >= 11 && tens <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' } as Record<number, string>)[n % 10] ?? 'th';
  return `${n}${suffix}`;
}
