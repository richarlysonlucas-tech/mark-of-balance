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

    // Escala idêntica ao Enemy.java
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
    this.xpConcedido = false; // Garante que só dá XP uma vez ao morrer

    this.speed = 36;
    this.aggroRange = 95;
    this.state = 'IDLE'; // IDLE | CHASE | DEAD
    this.hitFlashTimer = 0;
    this.knockback = { x: 0, y: 0 };
  }

  getHitboxAt(x, y) {
    return {
      x: x + this.hitbox.offsetX,
      y: y + this.hitbox.offsetY,
      width: this.hitbox.width,
      height: this.hitbox.height,
    };
  }

  // Mesma fórmula do Entity.java: Math.max(1, danoBruto - DEFESA)
  takeDamage(danoBruto, sourceX, sourceY) {
    if (this.state === 'DEAD') return 0;

    const danoReal = Math.max(1, Math.round(danoBruto - this.stats.getInt('DEFESA')));
    this.vida = Math.max(0, this.vida - danoReal);
    this.hitFlashTimer = 0.12;

    const dx = this.x - sourceX;
    const dy = this.y - sourceY;
    const dist = Math.hypot(dx, dy) || 1;
    this.knockback.x = (dx / dist) * 110;
    this.knockback.y = (dy / dist) * 110;

    if (this.vida === 0) {
      this.state = 'DEAD';
    }
    return danoReal;
  }

  // Mesma fórmula do Enemy.java
  xpPara(nivelDoJogador) {
    let fator = 1 + 0.1 * (this.nivel - nivelDoJogador);
    fator = Math.max(0.1, Math.min(1.5, fator));
    return Math.max(1, Math.round(this.xpBase * fator));
  }

  update(deltaTime, player, tileMap) {
    if (this.state === 'DEAD') return;

    if (this.hitFlashTimer > 0) this.hitFlashTimer -= deltaTime;

    let moveX = 0;
    let moveY = 0;

    if (Math.hypot(this.knockback.x, this.knockback.y) > 5) {
      moveX = this.knockback.x;
      moveY = this.knockback.y;
      this.knockback.x *= 0.82;
      this.knockback.y *= 0.82;
    } else {
      const dx = player.x - this.x;
      const dy = player.y - this.y;
      const dist = Math.hypot(dx, dy);

      if (dist <= this.aggroRange && dist > 4 && player.state !== 'DEAD') {
        this.state = 'CHASE';
        moveX = (dx / dist) * this.speed;
        moveY = (dy / dist) * this.speed;
      } else {
        this.state = 'IDLE';
      }
    }

    const nextX = this.x + moveX * deltaTime;
    if (!tileMap.checkCollision(this.getHitboxAt(nextX, this.y))) this.x = nextX;

    const nextY = this.y + moveY * deltaTime;
    if (!tileMap.checkCollision(this.getHitboxAt(this.x, nextY))) this.y = nextY;
  }

  render(ctx, camera) {
    if (this.state === 'DEAD') return;

    const drawX = Math.round(this.x - camera.x);
    const drawY = Math.round(this.y - camera.y);

    ctx.fillStyle = this.hitFlashTimer > 0 ? '#ffffff' : '#e63946';
    ctx.fillRect(drawX + 2, drawY + 4, 12, 12);

    const hpPercent = this.vida / this.stats.getInt('VIDA_MAXIMA');
    ctx.fillStyle = '#111111';
    ctx.fillRect(drawX, drawY - 3, 16, 3);
    ctx.fillStyle = '#2ec4b6';
    ctx.fillRect(drawX + 1, drawY - 2, Math.round(14 * hpPercent), 1);
  }
}