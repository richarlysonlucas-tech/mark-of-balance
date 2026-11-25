import { StatSheet } from '../systems/ProgressionSystem.js';

export class Enemy {
  constructor(x, y, nome = 'Corrompido', nivel = 1) {
    this.x = x;
    this.y = y;
    this.width = 16;
    this.height = 16;
    this.nome = nome;
    this.nivel = nivel;

    this.hitbox = { offsetX: 2, offsetY: 6, width: 12, height: 10 };

    // Atributos escalando igual ao Enemy.java do back-end
    const n = nivel - 1;
    this.stats = new StatSheet({
      VIDA_MAXIMA: 40 + 14 * n,
      ENERGIA_MAXIMA: 20 + 2 * n,
      FORCA: 6 + 1.8 * n,
      DEFESA: 2 + 0.8 * n,
      VELOCIDADE: 8 + 0.3 * n,
    });

    this.vida = this.stats.getInt('VIDA_MAXIMA');
    this.xpBase = 15 + 6 * nivel;
    this.xpConcedido = false;

    this.speed = 36;
    this.aggroRange = 95;
    this.state = 'IDLE'; // 'IDLE' | 'CHASE' | 'DEAD'
  }

  getHitboxAt(x, y) {
    return {
      x: x + this.hitbox.offsetX,
      y: y + this.hitbox.offsetY,
      width: this.hitbox.width,
      height: this.hitbox.height,
    };
  }

  // Cálculo de XP idêntico ao Enemy.java
  xpPara(nivelDoJogador) {
    let fator = 1 + 0.1 * (this.nivel - nivelDoJogador);
    fator = Math.max(0.1, Math.min(1.5, fator));
    return Math.max(1, Math.round(this.xpBase * fator));
  }

  update(deltaTime, player, tileMap) {
    if (this.state === 'DEAD') return;

    const dx = player.x - this.x;
    const dy = player.y - this.y;
    const dist = Math.hypot(dx, dy);

    // Persegue o jogador no mapa até encostar nele e iniciar a batalha por turnos
    if (dist <= this.aggroRange && dist > 2 && player.state !== 'DEAD') {
      this.state = 'CHASE';
      const moveX = (dx / dist) * this.speed;
      const moveY = (dy / dist) * this.speed;

      const nextX = this.x + moveX * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(nextX, this.y))) {
        this.x = nextX;
      }

      const nextY = this.y + moveY * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(this.x, nextY))) {
        this.y = nextY;
      }
    } else {
      this.state = 'IDLE';
    }
  }

  render(ctx, camera) {
    if (this.state === 'DEAD') return;

    const drawX = Math.round(this.x - camera.x);
    const drawY = Math.round(this.y - camera.y);

    // Corpo do inimigo no mapa
    ctx.fillStyle = '#e63946';
    ctx.fillRect(drawX + 2, drawY + 4, 12, 12);

    // Nível acima do inimigo no mapa
    ctx.fillStyle = '#ffffff';
    ctx.font = '5px monospace';
    ctx.fillText(`Nv.${this.nivel}`, drawX, drawY - 1);
  }
}