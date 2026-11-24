import { StatSheet, Progression } from '../systems/ProgressionSystem.js';

export class Player {
  constructor(x, y, nome = 'Heroi de Ousia') {
    this.x = x;
    this.y = y;
    this.width = 16;
    this.height = 16;
    this.nome = nome;

    this.hitbox = { offsetX: 3, offsetY: 8, width: 10, height: 8 };

    this.progressao = new Progression(1, 50);
    this.pontosDeHabilidade = 0;

    this.stats = new StatSheet({
      VIDA_MAXIMA: 100,
      ENERGIA_MAXIMA: 50,
      FORCA: 10,
      DEFESA: 5,
      VELOCIDADE: 10,
    });

    this.vida = this.stats.getInt('VIDA_MAXIMA');
    this.energia = this.stats.getInt('ENERGIA_MAXIMA');
    this.mpRegen = 4;

    this.state = 'IDLE';
    this.direction = 'down';
    this.invulnTimer = 0;
    this.levelUpBannerTimer = 0;
  }

  getHitboxAt(x, y) {
    return {
      x: x + this.hitbox.offsetX,
      y: y + this.hitbox.offsetY,
      width: this.hitbox.width,
      height: this.hitbox.height,
    };
  }

  // Mesma fórmula do Entity.java
  takeDamage(danoBruto) {
    if (this.invulnTimer > 0 || this.state === 'DEAD') return 0;

    const danoReal = Math.max(1, Math.round(danoBruto - this.stats.getInt('DEFESA')));
    this.vida = Math.max(0, this.vida - danoReal);
    this.invulnTimer = 0.8;

    if (this.vida === 0) {
      this.state = 'DEAD';
    }
    return danoReal;
  }

  // Mesma fórmula do Player.java
  ganharXp(xp) {
    const novosNiveis = this.progressao.addXp(xp);
    for (let i = 0; i < novosNiveis.length; i++) {
      this.stats.addBase('VIDA_MAXIMA', 12);
      this.stats.addBase('ENERGIA_MAXIMA', 4);
      this.stats.addBase('FORCA', 2);
      this.stats.addBase('DEFESA', 1.5);
      this.stats.addBase('VELOCIDADE', 0.5);
      this.pontosDeHabilidade++;
    }

    if (novosNiveis.length > 0) {
      this.vida = this.stats.getInt('VIDA_MAXIMA');
      this.energia = this.stats.getInt('ENERGIA_MAXIMA');
      this.levelUpBannerTimer = 2.5;
    }
    return novosNiveis.length;
  }

  update(deltaTime, input, tileMap) {
    if (this.state === 'DEAD') return;

    if (this.invulnTimer > 0) this.invulnTimer = Math.max(0, this.invulnTimer - deltaTime);
    if (this.levelUpBannerTimer > 0) this.levelUpBannerTimer = Math.max(0, this.levelUpBannerTimer - deltaTime);

    const maxEnergia = this.stats.getInt('ENERGIA_MAXIMA');
    if (this.energia < maxEnergia) {
      this.energia = Math.min(maxEnergia, this.energia + this.mpRegen * deltaTime);
    }

    // Converte Stat.VELOCIDADE (base 10) em pixels/segundo (64px/s no nível 1)
    const moveSpeed = 54 + this.stats.get('VELOCIDADE');
    const axis = input.getMovementAxis();

    if (axis.x !== 0 || axis.y !== 0) {
      this.state = 'WALK';
      this.updateDirection(axis.x, axis.y);

      const nextX = this.x + axis.x * moveSpeed * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(nextX, this.y))) this.x = nextX;

      const nextY = this.y + axis.y * moveSpeed * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(this.x, nextY))) this.y = nextY;
    } else {
      this.state = 'IDLE';
    }
  }

  updateDirection(dx, dy) {
    if (Math.abs(dx) > Math.abs(dy)) {
      this.direction = dx > 0 ? 'right' : 'left';
    } else {
      this.direction = dy > 0 ? 'down' : 'up';
    }
  }

  render(ctx, camera) {
    if (this.invulnTimer > 0 && Math.floor(this.invulnTimer * 20) % 2 === 0) return;

    const drawX = Math.round(this.x - camera.x);
    const drawY = Math.round(this.y - camera.y);

    ctx.fillStyle = this.state === 'DEAD' ? '#555555' : '#4ecdc4';
    ctx.fillRect(drawX, drawY, this.width, this.height);

    ctx.fillStyle = '#ffe66d';
    if (this.direction === 'down') ctx.fillRect(drawX + 6, drawY + 12, 4, 4);
    if (this.direction === 'up') ctx.fillRect(drawX + 6, drawY, 4, 4);
    if (this.direction === 'left') ctx.fillRect(drawX, drawY + 6, 4, 4);
    if (this.direction === 'right') ctx.fillRect(drawX + 12, drawY + 6, 4, 4);
  }

  // Serializa exatamente no formato que o Back-end Java espera
  toDTO() {
    return {
      nome: this.nome,
      nivel: this.progressao.nivel,
      xpAtual: this.progressao.xpAtual,
      xpTotal: this.progressao.xpTotal,
      pontosDeHabilidade: this.pontosDeHabilidade,
      vida: this.vida,
      energia: Math.round(this.energia),
      x: this.x,
      y: this.y,
      direction: this.direction,
      baseStats: { ...this.stats.base },
      modifiers: [...this.stats.modifiers],
    };
  }

  applySaveData(data) {
    if (!data) return;
    this.x = data.x ?? this.x;
    this.y = data.y ?? this.y;
    this.direction = data.direction || 'down';
    this.progressao.nivel = data.nivel ?? 1;
    this.progressao.xpAtual = data.xpAtual ?? 0;
    this.progressao.xpTotal = data.xpTotal ?? 0;
    this.pontosDeHabilidade = data.pontosDeHabilidade ?? 0;
    if (data.baseStats) this.stats.base = { ...data.baseStats };
    if (data.modifiers) this.stats.modifiers = [...data.modifiers];
    this.vida = data.vida ?? this.stats.getInt('VIDA_MAXIMA');
    this.energia = data.energia ?? this.stats.getInt('ENERGIA_MAXIMA');
    this.state = this.vida > 0 ? 'IDLE' : 'DEAD';
  }

  reset(spawnX, spawnY) {
    this.x = spawnX;
    this.y = spawnY;
    this.direction = 'down';
    this.progressao = new Progression(1, 50);
    this.pontosDeHabilidade = 0;
    this.stats = new StatSheet({
      VIDA_MAXIMA: 100,
      ENERGIA_MAXIMA: 50,
      FORCA: 10,
      DEFESA: 5,
      VELOCIDADE: 10,
    });
    this.vida = this.stats.getInt('VIDA_MAXIMA');
    this.energia = this.stats.getInt('ENERGIA_MAXIMA');
    this.state = 'IDLE';
    this.invulnTimer = 0;
  }
}