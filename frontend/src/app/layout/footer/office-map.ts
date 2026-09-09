import { Component, DestroyRef, ElementRef, afterNextRender, inject, signal, viewChild } from '@angular/core';
import type { Map as LeafletMap } from 'leaflet';

/** Trụ sở, geocoded once via Nominatim and hardcoded - a fixed pin needs no geocoding at runtime. */
const OFFICE = {
  lat: 10.8068,
  lng: 106.7117,
  title: '35/21 Đường D5',
  address: '35/21 đường D5, P.25, Bình Thạnh, TP.HCM',
};

/** Matches the pin the mocked map used to draw, and sidesteps Leaflet's default marker PNGs (their relative URLs break under the bundler). */
const PIN_SVG = `
  <svg viewBox="0 0 24 24" fill="#dc2626" width="36" height="36" style="filter:drop-shadow(0 1px 2px rgb(0 0 0 / .3))">
    <path d="M12 2a7 7 0 0 0-7 7c0 5.25 7 13 7 13s7-7.75 7-13a7 7 0 0 0-7-7Zm0 9.5A2.5 2.5 0 1 1 12 6.5a2.5 2.5 0 0 1 0 5Z" />
  </svg>`;

/**
 * The office map in the footer: Leaflet on OpenStreetMap raster tiles - free,
 * no API key, no billing account, unlike the Google embed the real KiotViet
 * footer uses.
 *
 * Leaflet is dynamically imported so its ~40kB stays out of the initial
 * bundle; the map lives at the very bottom of the landing page and nothing
 * above the fold needs it. afterNextRender keeps it browser-only.
 */
@Component({
  selector: 'app-office-map',
  standalone: true,
  template: `
    <div class="relative min-h-[300px] overflow-hidden rounded-lg bg-[#e8eaed] ring-1 ring-gray-200">
      <div
        #canvas
        class="h-full min-h-[300px] w-full"
        role="application"
        [attr.aria-label]="'Bản đồ tới ' + office.address"
      ></div>
      @if (failed()) {
        <div class="absolute inset-0 flex flex-col items-center justify-center gap-1 px-4 text-center">
          <p class="text-sm font-semibold text-gray-900">{{ office.title }}</p>
          <p class="text-xs text-gray-500">{{ office.address }}</p>
        </div>
      }
    </div>
  `,
})
export class OfficeMap {
  readonly office = OFFICE;

  /** Tiles blocked or the chunk failed to load - the address is shown as plain text instead. */
  readonly failed = signal(false);

  private readonly canvas = viewChild.required<ElementRef<HTMLElement>>('canvas');
  private map?: LeafletMap;

  constructor() {
    afterNextRender(() => void this.render());
    inject(DestroyRef).onDestroy(() => this.map?.remove());
  }

  private async render(): Promise<void> {
    try {
      // Leaflet is CommonJS, so the namespace may carry everything under .default
      // depending on how the bundler interops it - accept either shape.
      const mod = await import('leaflet');
      const L = ((mod as unknown as { default?: typeof mod }).default ?? mod) as typeof mod;

      this.map = L.map(this.canvas().nativeElement, {
        center: [OFFICE.lat, OFFICE.lng],
        zoom: 16,
        // A footer map must never eat the page scroll on the way past it.
        scrollWheelZoom: false,
        attributionControl: true,
      });

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      }).addTo(this.map);

      L.marker([OFFICE.lat, OFFICE.lng], {
        icon: L.divIcon({
          html: PIN_SVG,
          className: '',
          iconSize: [36, 36],
          iconAnchor: [18, 36],
          popupAnchor: [0, -34],
        }),
        title: OFFICE.address,
      })
        .addTo(this.map)
        .bindPopup(
          `<p class="text-sm font-semibold text-gray-900">${OFFICE.title}</p>` +
            `<p class="text-xs text-gray-500">${OFFICE.address}</p>`,
        )
        .openPopup();
    } catch {
      this.failed.set(true);
    }
  }
}
