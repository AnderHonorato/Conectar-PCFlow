export interface Point {
  x: number;
  y: number;
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

export function pointerDelta(from: Point, to: Point, speed: number): { dx: number; dy: number } {
  const factor = clamp(speed, 0.4, 2.4);
  return {
    dx: Math.round((to.x - from.x) * factor),
    dy: Math.round((to.y - from.y) * factor),
  };
}

export function railScroll(deltaY: number, scrollSpeed: number, natural: boolean): number {
  const factor = clamp(scrollSpeed, 0.5, 2);
  const magnitude = Math.round(deltaY * factor);
  return natural ? -magnitude : magnitude;
}

export function isTap(distance: number): boolean {
  return distance < 8;
}
