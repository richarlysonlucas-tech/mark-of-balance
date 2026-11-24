import { InputHandler } from './core/InputHandler.js';
import { Camera } from './core/Camera.js';
import { SaveSystem } from './core/SaveSystem.js';
import { ApiClient } from './core/ApiClient.js';
import { TileMap } from './world/TileMap.js';
import { Player } from './entities/Player.js';
import { Enemy } from './entities/Enemy.js';
import { SpellSystem } from './systems/SpellSystem.js';
import { CombatSystem } from './systems/CombatSystem.js';

const canvas = document.getElementById('game-canvas');
const ctx = canvas.getContext('2d');
ctx.imageSmoothingEnabled = false;

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`Não achei a imagem em: ${src}`));
    img.src = src;
  });
}

function renderHUD(ctx, player, spellSystem, saveFeedbackTimer) {
  // Painel de status (HP, Energia e XP)
  ctx.fillStyle = 'rgba(13, 13, 18, 0.78)';
  ctx.fillRect(6, 6, 86, 30);

  const maxVida = player.stats.getInt ? player.stats.getInt('VIDA_MAXIMA') : player.stats.maxHp;
  const maxEnergia = player.stats.getInt ? player.stats.getInt('ENERGIA_MAXIMA') : player.stats.maxMp;
  const vidaAtual = player.vida ?? player.stats.hp;
  const energiaAtual = player.energia ?? player.stats.mp;

  // Barra de Vida (Vermelha)
  const hpRatio = Math.max(0, Math.min(1, vidaAtual / maxVida));
  ctx.fillStyle = '#331111';
  ctx.fillRect(10, 10, 78, 5);
  ctx.fillStyle = '#e63946';
  ctx.fillRect(10, 10, Math.round(78 * hpRatio), 5);

  // Barra de Energia / Mana (Azul)
  const mpRatio = Math.max(0, Math.min(1, energiaAtual / maxEnergia));
  ctx.fillStyle = '#112233';
  ctx.fillRect(10, 18, 78, 5);
  ctx.fillStyle = '#4ea8de';
  ctx.fillRect(10, 18, Math.round(78 * mpRatio), 5);

  // Barra de XP (Dourada)
  const xpRatio = player.progressao ? player.progressao.progressoDoNivel() : 0;
  ctx.fillStyle = '#2b2611';
  ctx.fillRect(10, 26, 78, 3);
  ctx.fillStyle = '#ffe66d';
  ctx.fillRect(10, 26, Math.round(78 * xpRatio), 3);

  // Nível e Cooldowns das magias
  ctx.fillStyle = '#ffffff';
  ctx.font = '6px monospace';
  const nivel = player.progressao ? player.progressao.nivel : 1;
  const cd1 = (spellSystem.cooldowns['fireball'] || 0).toFixed(1);
  const cd2 = (spellSystem.cooldowns['ice_shard'] || 0).toFixed(1);
  ctx.fillText(`Nv.${nivel}  [J]:${cd1 > 0 ? cd1 : 'OK'} [K]:${cd2 > 0 ? cd2 : 'OK'}`, 6, 44);

  // Banner de Level Up
  if (player.levelUpBannerTimer > 0) {
    ctx.textAlign = 'center';
    ctx.fillStyle = '#ffe66d';
    ctx.font = 'bold 9px monospace';
    ctx.fillText(`SUBIU PARA O NIVEL ${nivel}!`, canvas.width / 2, 24);
    ctx.textAlign = 'left';
  }

  // Feedback de Save
  if (saveFeedbackTimer > 0) {
    ctx.fillStyle = '#4ecdc4';
    ctx.font = '6px monospace';
    ctx.fillText('JOGO SALVO!', canvas.width - 54, 14);
  }
}

function renderOverlayScreen(ctx, title, subtitle, extraLine = '') {
  ctx.fillStyle = 'rgba(10, 10, 16, 0.82)';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.textAlign = 'center';

  ctx.fillStyle = '#ffe66d';
  ctx.font = 'bold 14px monospace';
  ctx.fillText(title, canvas.width / 2, 72);

  ctx.fillStyle = '#ffffff';
  ctx.font = '7px monospace';
  ctx.fillText(subtitle, canvas.width / 2, 96);

  if (extraLine) {
    ctx.fillStyle = '#4ecdc4';
    ctx.fillText(extraLine, canvas.width / 2, 112);
  }

  ctx.textAlign = 'left';
}

async function init() {
  try {
    const [mapResponse, spellsResponse, tilesetImage] = await Promise.all([
      fetch('./data/map01.json'),
      fetch('./data/spells.json'),
      loadImage('./assets/tileset.png'),
    ]);

    const mapData = await mapResponse.json();
    const spellsData = await spellsResponse.json();

    const input = new InputHandler();
    const saveSystem = new SaveSystem();
    const apiClient = new ApiClient();
    const tileMap = new TileMap(mapData, tilesetImage);
    const camera = new Camera(canvas.width, canvas.height, tileMap.pixelWidth, tileMap.pixelHeight);

    const SPAWN_X = 300;
    const SPAWN_Y = 200;

    const player = new Player(SPAWN_X, SPAWN_Y);
    let spellSystem = new SpellSystem(spellsData);
    const combatSystem = new CombatSystem(apiClient);
    let enemies = [];

    // Estados: 'TITLE' | 'PLAYING' | 'PAUSED' | 'GAME_OVER'
    let gameState = 'TITLE';
    let saveFeedbackTimer = 0;

    async function setupWorld(loadFromSave = false) {
      player.reset(SPAWN_X, SPAWN_Y);

      if (loadFromSave) {
        const saved = saveSystem.load();
        player.applySaveData(saved);
      }

      // Se o servidor Java estiver rodando, sincroniza com os dados oficiais dele
      const dadosJava = await apiClient.getPlayer();
      if (dadosJava && player.progressao) {
        player.progressao.nivel = dadosJava.nivel;
        player.progressao.xpAtual = dadosJava.xpAtual;
        player.pontosDeHabilidade = dadosJava.pontos;
        player.stats.setBase('VIDA_MAXIMA', dadosJava.vidaMaxima);
        player.stats.setBase('ENERGIA_MAXIMA', dadosJava.energiaMaxima);
        player.stats.setBase('FORCA', dadosJava.forca);
        player.stats.setBase('DEFESA', dadosJava.defesa);
        player.stats.setBase('VELOCIDADE', dadosJava.velocidade);
        player.vida = dadosJava.vidaMaxima;
        player.energia = dadosJava.energiaMaxima;
      }

      spellSystem = new SpellSystem(spellsData);

      if (tileMap.enemySpawns && tileMap.enemySpawns.length > 0) {
        enemies = tileMap.enemySpawns.map((s) => new Enemy(s.x, s.y, s.name, 1));
      } else {
        enemies = [
          new Enemy(240, 220, 'Corrompido #1', 1),
          new Enemy(350, 240, 'Corrompido #2', 1),
          new Enemy(310, 320, 'Corrompido #3', 2),
        ];
      }

      camera.follow(player);
    }

    let lastTime = performance.now();

    function gameLoop(timestamp) {
      const deltaTime = Math.min((timestamp - lastTime) / 1000, 0.05);
      lastTime = timestamp;

      if (saveFeedbackTimer > 0) {
        saveFeedbackTimer = Math.max(0, saveFeedbackTimer - deltaTime);
      }

      // --- LÓGICA POR ESTADO ---
      if (gameState === 'TITLE') {
        if (input.consumeKey('enter')) {
          setupWorld(false).then(() => {
            gameState = 'PLAYING';
          });
        } else if (input.consumeKey('c') && saveSystem.hasSave()) {
          setupWorld(true).then(() => {
            gameState = 'PLAYING';
          });
        }
      } else if (gameState === 'PLAYING') {
        if (input.consumeKey('escape') || input.consumeKey('p')) {
          gameState = 'PAUSED';
        }

        if (input.consumeKey('g')) {
          saveSystem.save(player);
          saveFeedbackTimer = 2.0;
        }

        if (input.isDown('j') || input.isDown('1')) {
          spellSystem.cast('fireball', player);
        }
        if (input.isDown('k') || input.isDown('2')) {
          spellSystem.cast('ice_shard', player);
        }

        player.update(deltaTime, input, tileMap);
        spellSystem.update(deltaTime, tileMap);
        for (const enemy of enemies) {
          enemy.update(deltaTime, player, tileMap);
        }
        combatSystem.update(player, enemies, spellSystem);
        camera.follow(player);

        if (player.state === 'DEAD') {
          gameState = 'GAME_OVER';
        }
      } else if (gameState === 'PAUSED') {
        if (input.consumeKey('escape') || input.consumeKey('p')) {
          gameState = 'PLAYING';
        } else if (input.consumeKey('g')) {
          saveSystem.save(player);
          saveFeedbackTimer = 2.0;
        }
      } else if (gameState === 'GAME_OVER') {
        if (input.consumeKey('r')) {
          setupWorld(saveSystem.hasSave()).then(() => {
            gameState = 'PLAYING';
          });
        }
      }

      // --- RENDERIZAÇÃO ---
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      if (gameState === 'TITLE') {
        renderOverlayScreen(
          ctx,
          'MARK OF BALANCE',
          'Pressione [ENTER] para Novo Jogo',
          saveSystem.hasSave() ? 'Pressione [C] para Continuar Save' : ''
        );
      } else {
        tileMap.renderAllLayers(ctx, camera);
        for (const enemy of enemies) {
          enemy.render(ctx, camera);
        }
        player.render(ctx, camera);
        spellSystem.render(ctx, camera);
        renderHUD(ctx, player, spellSystem, saveFeedbackTimer);

        if (gameState === 'PAUSED') {
          renderOverlayScreen(
            ctx,
            'JOGO PAUSADO',
            '[P] ou [ESC] Continuar   |   [G] Salvar',
            saveFeedbackTimer > 0 ? 'Progresso salvo no navegador!' : ''
          );
        } else if (gameState === 'GAME_OVER') {
          renderOverlayScreen(
            ctx,
            'VOCÊ CAIU EM COMBATE',
            'Pressione [R] para Tentar Novamente'
          );
        }
      }

      requestAnimationFrame(gameLoop);
    }

    requestAnimationFrame(gameLoop);
  } catch (erro) {
    console.error('Erro ao iniciar o jogo:', erro.message);
  }
}

init();