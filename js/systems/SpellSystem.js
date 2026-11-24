export class SpellSystem {
  constructor(spellsData) {
    this.spellsData = spellsData;
    this.cooldowns = {}; // Guarda quanto tempo falta para cada magia recarregar
    this.projectiles = []; // Projéteis ativos voando pelo mapa
  }

  cast(spellId, caster) {
    const spell = this.spellsData[spellId];
    if (!spell) return false;

    // 1. Verifica se está em cooldown
    if ((this.cooldowns[spellId] || 0) > 0) return false;

    // 2. Verifica se o jogador tem mana suficiente
    if (caster.stats.mp < spell.manaCost) return false;

    // Consome mana e inicia cooldown
    caster.stats.mp -= spell.manaCost;
    this.cooldowns[spellId] = spell.cooldown;

    // Define o vetor de direção baseado para onde o jogador está olhando
    const dirVectors = {
      up: { x: 0, y: -1 },
      down: { x: 0, y: 1 },
      left: { x: -1, y: 0 },
      right: { x: 1, y: 0 },
    };
    const dir = dirVectors[caster.direction] || { x: 0, y: 1 };

    // Cria o projétil saindo do centro do personagem
    this.projectiles.push({
      spellId: spell.id,
      x: caster.x + caster.width / 2 - spell.size / 2,
      y: caster.y + caster.height / 2 - spell.size / 2,
      vx: dir.x * spell.speed,
      vy: dir.y * spell.speed,
      size: spell.size,
      damage: spell.damage,
      color: spell.color,
      traveled: 0,
      maxRange: spell.range,
      alive: true,
    });

    return true;
  }

  update(deltaTime, tileMap) {
    // Atualiza timers de cooldown
    for (const id in this.cooldowns) {
      if (this.cooldowns[id] > 0) {
        this.cooldowns[id] = Math.max(0, this.cooldowns[id] - deltaTime);
      }
    }

    // Atualiza posição dos projéteis
    for (const proj of this.projectiles) {
      if (!proj.alive) continue;

      const stepX = proj.vx * deltaTime;
      const stepY = proj.vy * deltaTime;

      proj.x += stepX;
      proj.y += stepY;
      proj.traveled += Math.hypot(stepX, stepY);

      // Destrói se atingir o alcance máximo ou bater numa parede do TileMap
      const hitWall = tileMap.checkCollision({
        x: proj.x,
        y: proj.y,
        width: proj.size,
        height: proj.size,
      });

      if (proj.traveled >= proj.maxRange || hitWall) {
        proj.alive = false;
      }
    }

    // Remove projéteis destruídos da lista
    this.projectiles = this.projectiles.filter((p) => p.alive);
  }

  render(ctx, camera) {
    for (const proj of this.projectiles) {
      const drawX = Math.round(proj.x - camera.x);
      const drawY = Math.round(proj.y - camera.y);

      ctx.fillStyle = proj.color;
      ctx.fillRect(drawX, drawY, proj.size, proj.size);

      // Núcleo brilhante no centro da magia
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(drawX + 2, drawY + 2, Math.max(2, proj.size - 4), Math.max(2, proj.size - 4));
    }
  }
}