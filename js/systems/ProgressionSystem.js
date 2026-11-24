export class StatSheet {
  constructor(baseStats = {}) {
    this.base = {
      VIDA_MAXIMA: baseStats.VIDA_MAXIMA ?? 100,
      ENERGIA_MAXIMA: baseStats.ENERGIA_MAXIMA ?? 50,
      FORCA: baseStats.FORCA ?? 10,
      DEFESA: baseStats.DEFESA ?? 5,
      VELOCIDADE: baseStats.VELOCIDADE ?? 10,
    };
    this.modifiers = []; // { source, stat, type: 'FLAT' | 'PERCENT', value }
  }

  setBase(stat, value) {
    this.base[stat] = value;
  }

  addBase(stat, delta) {
    this.base[stat] = (this.base[stat] || 0) + delta;
  }

  addModifier(source, stat, type, value) {
    this.modifiers.push({ source, stat, type, value });
  }

  removeModifiersFrom(source) {
    this.modifiers = this.modifiers.filter((m) => m.source !== source);
  }

  get(stat) {
    let flat = 0;
    let percent = 0;
    for (const m of this.modifiers) {
      if (m.stat !== stat) continue;
      if (m.type === 'FLAT') flat += m.value;
      else if (m.type === 'PERCENT') percent += m.value;
    }
    return Math.max(0, (this.base[stat] + flat) * (1 + percent));
  }

  getInt(stat) {
    return Math.round(this.get(stat));
  }
}

export class Progression {
  constructor(nivelInicial = 1, nivelMaximo = 50) {
    this.nivel = nivelInicial;
    this.nivelMaximo = nivelMaximo;
    this.xpAtual = 0;
    this.xpTotal = 0;
  }

  // Mesma curva polinomial do ExperienceCurve.java: 100 * nivel ^ 1.5
  xpParaProximoNivel() {
    if (this.nivel >= this.nivelMaximo) return 0;
    return Math.round(100 * Math.pow(this.nivel, 1.5));
  }

  addXp(quantidade) {
    const novosNiveis = [];
    if (this.nivel >= this.nivelMaximo || quantidade <= 0) return novosNiveis;

    this.xpTotal += quantidade;
    this.xpAtual += quantidade;

    while (this.nivel < this.nivelMaximo && this.xpAtual >= this.xpParaProximoNivel()) {
      this.xpAtual -= this.xpParaProximoNivel();
      this.nivel++;
      novosNiveis.push(this.nivel);
    }

    if (this.nivel >= this.nivelMaximo) {
      this.xpAtual = 0;
    }

    return novosNiveis;
  }

  progressoDoNivel() {
    if (this.nivel >= this.nivelMaximo) return 1;
    return this.xpAtual / this.xpParaProximoNivel();
  }
}