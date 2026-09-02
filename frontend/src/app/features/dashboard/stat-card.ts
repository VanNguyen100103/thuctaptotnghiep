import { Component, input } from '@angular/core';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  templateUrl: './stat-card.html',
})
export class StatCard {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
}
