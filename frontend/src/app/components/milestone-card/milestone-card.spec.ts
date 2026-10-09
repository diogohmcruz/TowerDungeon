import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MilestoneOffer } from '../../interfaces/game-state';
import { MilestoneCard } from './milestone-card';

describe('MilestoneCard', () => {
  let fixture: ComponentFixture<MilestoneCard>;

  const offer = (overrides: Partial<MilestoneOffer>): MilestoneOffer => ({
    id: 'ID',
    name: 'Name',
    description: 'Description',
    trigger: 'Reach floor 5',
    achieved: false,
    companion: false,
    ...overrides,
  });

  const statusFor = (milestone: MilestoneOffer): string => {
    fixture.componentRef.setInput('milestone', milestone);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('.milestone-status').textContent.trim();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [MilestoneCard] });
    fixture = TestBed.createComponent(MilestoneCard);
  });

  it('shows the trigger while the milestone is not achieved', () => {
    expect(statusFor(offer({}))).toBe('Reach floor 5');
  });

  it('says "joined" once a companion milestone is achieved', () => {
    expect(statusFor(offer({ achieved: true, companion: true }))).toBe('✓ joined');
  });

  it('says "earned" once a buff-only milestone is achieved', () => {
    expect(statusFor(offer({ achieved: true, companion: false }))).toBe('✓ earned');
  });
});
