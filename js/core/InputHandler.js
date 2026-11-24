export class InputHandler {
  constructor() {
    this.keys = new Set();
    this.justPressed = new Set();

    window.addEventListener('keydown', (e) => {
      const k = e.key.toLowerCase();
      if (!this.keys.has(k)) {
        this.justPressed.add(k);
      }
      this.keys.add(k);
    });

    window.addEventListener('keyup', (e) => {
      const k = e.key.toLowerCase();
      this.keys.delete(k);
      this.justPressed.delete(k);
    });

    window.addEventListener('blur', () => {
      this.keys.clear();
      this.justPressed.clear();
    });
  }

  isDown(key) {
    return this.keys.has(key.toLowerCase());
  }

  // Lê o clique único da tecla e já limpa para não repetir no próximo frame
  consumeKey(key) {
    const k = key.toLowerCase();
    if (this.justPressed.has(k)) {
      this.justPressed.delete(k);
      return true;
    }
    return false;
  }

  getMovementAxis() {
    let x = 0;
    let y = 0;

    if (this.isDown('a') || this.isDown('arrowleft')) x -= 1;
    if (this.isDown('d') || this.isDown('arrowright')) x += 1;
    if (this.isDown('w') || this.isDown('arrowup')) y -= 1;
    if (this.isDown('s') || this.isDown('arrowdown')) y += 1;

    if (x !== 0 && y !== 0) {
      const invSqrt2 = 0.70710678;
      x *= invSqrt2;
      y *= invSqrt2;
    }

    return { x, y };
  }
}