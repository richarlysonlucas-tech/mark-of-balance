export class SaveSystem {
  constructor(storageKey = 'mark_of_balance_save_v1') {
    this.storageKey = storageKey;
  }

  hasSave() {
    return localStorage.getItem(this.storageKey) !== null;
  }

  save(player) {
    const data = {
      x: player.x,
      y: player.y,
      direction: player.direction,
      stats: { ...player.stats },
      savedAt: new Date().toISOString(),
    };

    localStorage.setItem(this.storageKey, JSON.stringify(data));
  }

  load() {
    const raw = localStorage.getItem(this.storageKey);
    if (!raw) return null;

    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  clear() {
    localStorage.removeItem(this.storageKey);
  }
}