/**
 * Applies to every spec (a top-level hook in any spec file runs for all): a spec fails when the
 * app or angular-three logs one of these errors, instead of the error scrolling by in the log.
 * - NG0101: ApplicationRef.tick was called inside a tick.
 * - angular-three could not destroy a Canvas Root.
 */
const WATCHED = [/NG0101/, /\[NGT\] Unexpected error while destroying Canvas Root/];

let seen: string[] = [];

beforeEach(() => {
  seen = [];
  if (jasmine.isSpy(console.error)) return;
  const log = console.error;
  spyOn(console, 'error').and.callFake((...args: unknown[]) => {
    const text = args.map((a) => (a instanceof Error ? a.message : String(a))).join(' ');
    if (WATCHED.some((w) => w.test(text))) seen.push(text);
    log.apply(console, args);
  });
});

afterEach(() => {
  if (seen.length) fail(`console.error logged: ${seen.join(' | ')}`);
});
