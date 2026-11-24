export class CombatSystem {
  constructor(apiClient = null) {
    this.apiClient = apiClient;
  }

  rectsOverlap(a, b) {
    return (
      a.x < b.x + b.width &&
      a.x + a.width > b.x &&
      a.y < b.y + b.height &&
      a.y + a.height > b.y
    );
  }

  update(player, enemies, spellSystem) {
    // 1. Projéteis do jogador vs. Inimigos
    for (const proj of spellSystem.projectiles) {
      if (!proj.alive) continue;

      const projRect = {
        x: proj.x,
        y: proj.y,
        width: proj.size,
        height: proj.size,
      };

      for (const enemy of enemies) {
        if (enemy.state === 'DEAD') continue;

        const enemyRect = enemy.getHitboxAt(enemy.x, enemy.y);
        if (this.rectsOverlap(projRect, enemyRect)) {
          enemy.takeDamage(proj.damage, proj.x, proj.y);
          proj.alive = false;

          // Se o inimigo morreu agora, ganha XP e sincroniza com o Java
          if (enemy.state === 'DEAD' && !enemy.xpConcedido) {
            enemy.xpConcedido = true;
            this.processarMorteInimigo(player, enemy);
          }
          break;
        }
      }
    }

    // 2. Contato do Inimigo vs. Jogador
    if (player.state !== 'DEAD') {
      const playerRect = player.getHitboxAt(player.x, player.y);

      for (const enemy of enemies) {
        if (enemy.state === 'DEAD') continue;

        const enemyRect = enemy.getHitboxAt(enemy.x, enemy.y);
        if (this.rectsOverlap(playerRect, enemyRect)) {
          const danoInimigo = enemy.stats ? enemy.stats.getInt('FORCA') : enemy.stats.damage;
          player.takeDamage(danoInimigo);
        }
      }
    }
  }

  async processarMorteInimigo(player, enemy) {
    // Atualiza localmente na hora para não haver atraso visual
    const xp = enemy.xpPara ? enemy.xpPara(player.progressao?.nivel || 1) : 20;
    if (player.ganharXp) {
      player.ganharXp(xp);
    }

    // Se o servidor Java estiver rodando, sincroniza com os cálculos oficiais dele
    if (this.apiClient) {
      const dadosJava = await this.apiClient.registrarInimigoDerrotado(
        enemy.nome || enemy.name || 'Corrompido',
        enemy.nivel || 1
      );

      if (dadosJava && player.progressao) {
        player.progressao.nivel = dadosJava.nivel;
        player.progressao.xpAtual = dadosJava.xpAtual;
        player.pontosDeHabilidade = dadosJava.pontos;
        player.stats.setBase('VIDA_MAXIMA', dadosJava.vidaMaxima);
        player.stats.setBase('ENERGIA_MAXIMA', dadosJava.energiaMaxima);
        player.stats.setBase('FORCA', dadosJava.forca);
        player.stats.setBase('DEFESA', dadosJava.defesa);
        player.stats.setBase('VELOCIDADE', dadosJava.velocidade);
      }
    }
  }
}