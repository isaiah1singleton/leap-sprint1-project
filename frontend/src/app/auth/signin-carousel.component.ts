import { Component, signal } from '@angular/core';

@Component({
  selector: 'app-signin-carousel',
  templateUrl: './signin-carousel.component.html',
  styleUrl: './signin-carousel.component.css',
})
export class SigninCarouselComponent {
  readonly activeSlide = signal(0);
  readonly slides = [
    { title: 'See the bigger picture.', description: 'Keep your holdings, cash and portfolio value in view.', label: 'Your portfolio' },
    { title: 'Find your next opportunity.', description: 'Explore instruments and keep the stocks you follow close.', label: 'Your markets' },
    { title: 'Follow every move.', description: 'Track your orders from submission to execution, all in one place.', label: 'Your orders' },
  ] as const;

  selectSlide(index: number): void {
    this.activeSlide.set((index + this.slides.length) % this.slides.length);
  }
}
