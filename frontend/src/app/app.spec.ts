import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { App } from './app';
import { GameWebSocketService, SOCKET_FACTORY } from './services/game-web-socket.service';
import { GameStateService } from './services/game-state.service';

describe('App', () => {
  const sockets: any[] = [];

  beforeEach(async () => {
    sockets.length = 0;
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SOCKET_FACTORY,
          useValue: (url: string, protocols: string[]) => {
            const s = { url, protocols, close() {}, send() {}, readyState: 0 };
            sockets.push(s);
            return s;
          },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('shows the gate first, with Import present but disabled', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-gate')).toBeTruthy();
    expect(compiled.querySelector('app-game-component')).toBeNull();
    expect(compiled.querySelector('button.begin')).toBeTruthy();
    expect(compiled.querySelector('button.resume')).toBeTruthy();
    expect(compiled.querySelector<HTMLButtonElement>('button.import')?.disabled).toBeTrue();
    expect(sockets.length).toBe(0);
  });

  it('renders the game and the theme toggle after Begin', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.begin')!.click();
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(sockets.length).toBe(1);
    expect(sockets[0].protocols[0]).toBe('td.v1');
    expect(compiled.querySelector('app-game-component')).toBeTruthy();
    expect(compiled.querySelector('button.theme-toggle')).toBeTruthy();
  });

  it('returns to the gate with the reason when the key is refused', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.begin')!.click();
    sockets[0].onclose({ code: 4400 });
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-gate')).toBeTruthy();
    expect(compiled.querySelector('.gate-reason')?.textContent).toContain('refused');
  });

  it('tells the player another tab took over', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.begin')!.click();
    sockets[0].onclose({ code: 4001 });
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.connection-notice')?.textContent,
    ).toContain('Another tab');
    expect(TestBed.inject(GameWebSocketService).status()).toBe('stopped');
  });

  /** A whole frame: the initial state with some fields replaced, as the server sends it. */
  const frame = (over: object = {}) => ({ ...TestBed.inject(GameStateService).state(), ...over });

  function begin() {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('button.begin')!.click();
    fixture.detectChanges();
    return fixture;
  }

  it('shows no Race HUD outside a Trial', () => {
    const fixture = begin();
    sockets[0].onmessage?.({ data: JSON.stringify(frame()) });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-race-hud')).toBeNull();
  });

  it('shows the HUD in a Trial and the finished table once it is over', () => {
    const fixture = begin();
    TestBed.inject(GameWebSocketService).connectTrial({ raceId: 'season-1', next: false });
    const trial = {
      raceId: 'season-1',
      attempt: 1,
      finishFloor: 10,
      limitTicks: 5400,
      ticksSinceStart: 60,
      status: 'RACING',
      expeditions: 0,
      wipes: 0,
      arrivals: {},
    };
    sockets[1].onmessage?.({ data: JSON.stringify(frame({ trial })) });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('app-race-hud')).toBeTruthy();
    expect(el.querySelector('app-race-table')).toBeNull();
    sockets[1].onmessage?.({
      data: JSON.stringify(frame({ trial: { ...trial, status: 'FINISHED' } })),
    });
    fixture.detectChanges();
    expect(el.querySelector('app-race-table')).toBeTruthy();
  });

  it('states why a Trial was refused and offers the way back to the Campaign', () => {
    const fixture = begin();
    const ws = TestBed.inject(GameWebSocketService);
    ws.connectTrial({ raceId: 'season-1', next: true });
    sockets[1].onclose({ code: 4403, reason: 'NO_ATTEMPTS' });
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.connection-notice')?.textContent).toContain(
      'Every attempt has been used.',
    );
    [...el.querySelectorAll<HTMLButtonElement>('.connection-notice button')]
      .find((b) => b.textContent!.includes('Return to the Campaign'))!
      .click();
    expect(sockets[2].protocols.length).toBe(2);
  });
});
