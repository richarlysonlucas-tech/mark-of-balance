import { StatSheet, Progression } from '../systems/ProgressionSystem.js';

export class Player {
  constructor(x, y, nome = 'Heroi de Ousia', spriteImage = null) {
    this.x = x;
    this.y = y;
    this.width = 16;
    this.height = 16;
    this.nome = nome;
    this.sprite = spriteImage;

    // Configuração da sprite sheet 32x48 (2 colunas x 3 linhas de 16x16)
    this.frameWidth = 16;
    this.frameHeight = 16;
    this.frameX = 0;
    this.maxFrames = 2;
    this.animTimer = 0;
    this.animSpeed = 0.16;

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

    const moveSpeed = 54 + this.stats.get('VELOCIDADE');
    const axis = input.getMovementAxis();

    if (axis.x !== 0 || axis.y !== 0) {
      this.state = 'WALK';
      this.updateDirection(axis.x, axis.y);

      const nextX = this.x + axis.x * moveSpeed * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(nextX, this.y))) this.x = nextX;

      const nextY = this.y + axis.y * moveSpeed * deltaTime;
      if (!tileMap.checkCollision(this.getHitboxAt(this.x, nextY))) this.y = nextY;

      this.animTimer += deltaTime;
      if (this.animTimer >= this.animSpeed) {
        this.animTimer = 0;
        this.frameX = (this.frameX + 1) % this.maxFrames;
      }
    } else {
      this.state = 'IDLE';
      this.frameX = 0;
      this.animTimer = 0;
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

    if (this.sprite && this.sprite.complete && this.sprite.naturalWidth > 0) {
      // Calcula o tamanho real de cada célula com base na imagem carregada (ex: 32x48 -> 16x16)
      const cols = this.maxFrames; // 2 colunas
      const rows = 3;              // 3 linhas
      const cellW = Math.floor(this.sprite.naturalWidth / cols);
      const cellH = Math.floor(this.sprite.naturalHeight / rows);

      // Linha 0: Frente (down) | Linha 1: Costas (up) | Linha 2: Lado (left/right)
      let frameY = 0;
      if (this.direction === 'up') frameY = 1;
      else if (this.direction === 'right' || this.direction === 'left') frameY = 2;

      const flipHorizontal = this.direction === 'left';

      ctx.save();
      if (flipHorizontal) {
        ctx.translate(drawX + cellW, drawY);
        ctx.scale(-1, 1);
        ctx.drawImage(
          this.sprite,
          this.frameX * cellW,
          frameY * cellH,
          cellW,
          cellH,
          0,
          0,
          cellW,
          cellH
        );
      } else {
        ctx.drawImage(
          this.sprite,
          this.frameX * cellW,
          frameY * cellH,
          cellW,
          cellH,
          drawX,
          drawY,
          cellW,
          cellH
        );
      }
      ctx.restore();
    } else {
      ctx.fillStyle = this.state === 'DEAD' ? '#555555' : '#4ecdc4';
      ctx.fillRect(drawX, drawY, this.width, this.height);
    }
  }

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
    this.frameX = 0;
    this.invulnTimer = 0;
  }
}