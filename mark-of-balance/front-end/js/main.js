import { InputHandler } from './core/InputHandler.js';
import { Camera } from './core/Camera.js';
import { SaveSystem } from './core/SaveSystem.js';
import { ApiClient } from './core/ApiClient.js';
import { TileMap } from './world/TileMap.js';
import { Player } from './entities/Player.js';
import { Enemy } from './entities/Enemy.js';
import { BattleSystem } from './systems/BattleSystem.js';

const canvas = document.getElementById('game-canvas');
const ctx = canvas.getContext('2d');
ctx.imageSmoothingEnabled = false;

// Resolução interna fixa 16:9 com ajuste automático para qualquer tela
const GAME_WIDTH = 320;
const GAME_HEIGHT = 180;
canvas.width = GAME_WIDTH;
canvas.height = GAME_HEIGHT;

function resizeCanvas() {
  const scaleX = window.innerWidth / GAME_WIDTH;
  const scaleY = window.innerHeight / GAME_HEIGHT;
  const scale = Math.min(scaleX, scaleY);
  canvas.style.width = `${Math.floor(GAME_WIDTH * scale)}px`;
  canvas.style.height = `${Math.floor(GAME_HEIGHT * scale)}px`;
}
window.addEventListener('resize', resizeCanvas);
resizeCanvas();

function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`Não achei a imagem em: ${src}`));
    img.src = src;
  });
}

function rectsOverlap(a, b) {
  return (
    a.x < b.x + b.width &&
    a.x + a.width > b.x &&
    a.y < b.y + b.height &&
    a.y + a.height > b.y
  );
}

// Registro dos 7 mapas do Tiled
const MAP_REGISTRY = {
  map01: { path: './data/map01.json', zona: 'FLORESTA_01', data: null },
  map02: { path: './data/map02.json', zona: 'FLORESTA_02', data: null },
  map03: { path: './data/map03.json', zona: 'FLORESTA_03', data: null },
  map04: { path: './data/map04.json', zona: 'FLORESTA_04', data: null },
  map05: { path: './data/map05.json', zona: 'FLORESTA_05', data: null },
  map06: { path: './data/map06.json', zona: 'FLORESTA_06', data: null },
  map07: { path: './data/map07.json', zona: 'FLORESTA_07', data: null },
};

async function getMapJson(mapKey) {
  const entry = MAP_REGISTRY[mapKey];
  if (!entry) return null;
  if (!entry.data) {
    const response = await fetch(entry.path);
    entry.data = await response.json();
  }
  return entry.data;
}

// Busca qual outro mapa possui um portal com o mesmo nome (ex: "portal 1", "portal 2")
async function findDestinationPortal(portalName, originMapKey) {
  for (const mapKey of Object.keys(MAP_REGISTRY)) {
    if (mapKey === originMapKey) continue;

    const json = await getMapJson(mapKey);
    if (!json || !json.layers) continue;

    for (const layer of json.layers) {
      if (layer.type !== 'objectgroup') continue;
      const layerNameLower = (layer.name || '').trim().toLowerCase();
      const layerEhPortal = layerNameLower.startsWith('portal');

      if (!layerEhPortal && layerNameLower !== 'portais') continue;

      for (const obj of layer.objects) {
        if (!obj.width || !obj.height) continue;

        const nome = layerEhPortal
          ? layerNameLower
          : (obj.name || '').trim().toLowerCase();

        if (nome === portalName.toLowerCase()) {
          const propSaida = obj.properties?.find((p) => p.name === 'direcaoSaida');
          return {
            targetMapKey: mapKey,
            targetMapJson: json,
            portal: {
              x: obj.x,
              y: obj.y,
              width: obj.width,
              height: obj.height,
              direcaoSaida: propSaida ? propSaida.value : null,
            },
          };
        }
      }
    }
  }
  return null;
}

// Calcula a posição segura fora da hitbox do portal de destino para evitar loop
function getSafeSpawnPosition(portal, mapJson, player) {
  const mapWidthPx = mapJson.width * mapJson.tilewidth;
  const mapHeightPx = mapJson.height * mapJson.tileheight;
  const offset = 24;

  const hitbox = player.getHitboxAt(player.x, player.y);
  const offsetHitboxX = hitbox.x - player.x;
  const offsetHitboxY = hitbox.y - player.y;

  let targetHitboxX = portal.x + portal.width / 2 - hitbox.width / 2;
  let targetHitboxY = portal.y + portal.height / 2 - hitbox.height / 2;

  if (portal.direcaoSaida === 'baixo') {
    targetHitboxY = portal.y + portal.height + offset;
  } else if (portal.direcaoSaida === 'cima') {
    targetHitboxY = portal.y - hitbox.height - offset;
  } else if (portal.direcaoSaida === 'direita') {
    targetHitboxX = portal.x + portal.width + offset;
  } else if (portal.direcaoSaida === 'esquerda') {
    targetHitboxX = portal.x - hitbox.width - offset;
  } else {
    // Detecção automática pela borda do mapa onde o portal está
    if (portal.x <= 32) {
      targetHitboxX = portal.x + portal.width + offset;
    } else if (portal.x + portal.width >= mapWidthPx - 32) {
      targetHitboxX = portal.x - hitbox.width - offset;
    } else if (portal.y <= 48) {
      targetHitboxY = portal.y + portal.height + offset;
    } else if (portal.y + portal.height >= mapHeightPx - 48) {
      targetHitboxY = portal.y - hitbox.height - offset;
    }
  }

  // Garante que não nasça fora dos limites do mapa
  targetHitboxX = Math.max(4, Math.min(mapWidthPx - hitbox.width - 4, targetHitboxX));
  targetHitboxY = Math.max(4, Math.min(mapHeightPx - hitbox.height - 4, targetHitboxY));

  return {
    x: targetHitboxX - offsetHitboxX,
    y: targetHitboxY - offsetHitboxY,
  };
}

function renderHUD(ctx, player, apiClient, feedbackMsg, feedbackTimer) {
  ctx.fillStyle = 'rgba(13, 13, 18, 0.85)';
  ctx.fillRect(6, 6, 126, 42);

  const maxVida = player.stats.getInt('VIDA_MAXIMA');
  const maxEnergia = player.stats.getInt('ENERGIA_MAXIMA');

  // Barra de Vida
  const hpRatio = Math.max(0, Math.min(1, player.vida / maxVida));
  ctx.fillStyle = '#331111';
  ctx.fillRect(10, 10, 118, 6);
  ctx.fillStyle = '#e63946';
  ctx.fillRect(10, 10, Math.round(118 * hpRatio), 6);

  // Barra de Energia
  const mpRatio = Math.max(0, Math.min(1, player.energia / maxEnergia));
  ctx.fillStyle = '#112233';
  ctx.fillRect(10, 19, 118, 6);
  ctx.fillStyle = '#4ea8de';
  ctx.fillRect(10, 19, Math.round(118 * mpRatio), 6);

  // Barra de XP
  const xpRatio = player.progressao.progressoDoNivel();
  ctx.fillStyle = '#2b2611';
  ctx.fillRect(10, 28, 118, 4);
  ctx.fillStyle = '#ffe66d';
  ctx.fillRect(10, 28, Math.round(118 * xpRatio), 4);

  // Fragmentos (Coletados / Purificados)
  ctx.fillStyle = '#c77dff';
  ctx.font = 'bold 7px monospace';
  ctx.fillText(
    `Frag: ${player.fragmentosColetados} | Purif: ${player.fragmentosPurificados}`,
    10,
    43
  );

  // Barra de Atalhos
  ctx.fillStyle = 'rgba(13, 13, 18, 0.85)';
  ctx.fillRect(6, 50, 210, 14);
  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 7px monospace';
  ctx.fillText(`Nv.${player.progressao.nivel} [G]Salvar [H]Hub [E]Baú [P]Pausa`, 10, 60);

  // Indicador Online/Offline
  ctx.fillStyle = apiClient.conectado ? '#2ecc71' : '#f39c12';
  ctx.fillText(apiClient.conectado ? 'JAVA: ON' : 'OFFLINE', canvas.width - 48, 14);

  if (feedbackTimer > 0 && feedbackMsg) {
    ctx.fillStyle = 'rgba(13, 13, 18, 0.9)';
    ctx.fillRect(6, canvas.height - 20, canvas.width - 12, 14);
    ctx.fillStyle = '#4ecdc4';
    ctx.font = 'bold 8px monospace';
    ctx.fillText(feedbackMsg, 10, canvas.height - 10);
  }
}

function renderOverlayScreen(ctx, title, subtitle, extraLine = '') {
  ctx.fillStyle = 'rgba(10, 10, 16, 0.88)';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  ctx.textAlign = 'center';
  ctx.fillStyle = '#ffe66d';
  ctx.font = 'bold 14px monospace';
  ctx.fillText(title, canvas.width / 2, 66);

  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 8px monospace';
  ctx.fillText(subtitle, canvas.width / 2, 92);

  if (extraLine) {
    ctx.fillStyle = '#4ecdc4';
    ctx.fillText(extraLine, canvas.width / 2, 110);
  }
  ctx.textAlign = 'left';
}

async function init() {
  try {
    const [initialMapData, tilesetImage, playerSprite] = await Promise.all([
      getMapJson('map01'),
      loadImage('./assets/tileset.png'),
      loadImage('./assets/heroi_padrao.png'),
    ]);

    const input = new InputHandler();
    const saveSystem = new SaveSystem();
    const apiClient = new ApiClient();

    let currentMapKey = 'map01';
    let isTransitioningMap = false;
    let tileMap = new TileMap(initialMapData, tilesetImage);
    let camera = new Camera(canvas.width, canvas.height, tileMap.pixelWidth, tileMap.pixelHeight);

    const SPAWN_X = 300;
    const SPAWN_Y = 200;

    const player = new Player(SPAWN_X, SPAWN_Y, 'Arthur', playerSprite);
    player.fragmentosColetados = 0;
    player.fragmentosPurificados = 0;

    const battleSystem = new BattleSystem(apiClient);
    let enemies = [];

    let gameState = 'TITLE';
    let feedbackMsg = '';
    let feedbackTimer = 0;
    let moveSyncTimer = 0;

    function avisar(texto, duracao = 2.5) {
      feedbackMsg = texto;
      feedbackTimer = duracao;
    }

    function spawnEnemiesForCurrentMap() {
      if (tileMap.enemySpawns && tileMap.enemySpawns.length > 0) {
        enemies = tileMap.enemySpawns.map((s) => new Enemy(s.x, s.y, s.name, 1));
      } else if (currentMapKey === 'map01') {
        enemies = [
          new Enemy(240, 220, 'Slime Verde', 1),
          new Enemy(350, 240, 'Morcego Sombrio', 2),
          new Enemy(310, 320, 'Corrompido', 2),
        ];
      } else {
        enemies = [];
      }
    }

    async function checkAndUsePortals() {
      if (isTransitioningMap || !tileMap.portals || tileMap.portals.length === 0) return;

      const playerRect = player.getHitboxAt(player.x, player.y);

      for (const portal of tileMap.portals) {
        if (rectsOverlap(playerRect, portal)) {
          isTransitioningMap = true;

          const dest = await findDestinationPortal(portal.nome, currentMapKey);
          if (dest) {
            currentMapKey = dest.targetMapKey;
            tileMap = new TileMap(dest.targetMapJson, tilesetImage);
            camera = new Camera(canvas.width, canvas.height, tileMap.pixelWidth, tileMap.pixelHeight);

            const spawn = getSafeSpawnPosition(dest.portal, dest.targetMapJson, player);
            player.x = spawn.x;
            player.y = spawn.y;

            spawnEnemiesForCurrentMap();
            camera.follow(player);
            avisar(`Entrou em ${currentMapKey.toUpperCase()} (${portal.nome})`, 1.5);
          } else {
            avisar(`Portal "${portal.nome}" sem destino conectado.`, 1.5);
          }

          setTimeout(() => {
            isTransitioningMap = false;
          }, 400);
          break;
        }
      }
    }

    apiClient.onEstadoAtualizado = (resp) => {
      if (!resp || !resp.heroi) return;
      const h = resp.heroi;
      player.nome = h.nome || player.nome;
      player.vida = h.vida;
      player.stats.setBase('VIDA_MAXIMA', h.vidaMaxima);
      if (player.progressao) player.progressao.nivel = h.nivel;
      player.fragmentosColetados = h.fragmentosColetados ?? player.fragmentosColetados;
      player.fragmentosPurificados = h.fragmentosPurificados ?? player.fragmentosPurificados;
    };

    async function setupWorld(loadFromSave = false) {
      currentMapKey = 'map01';
      const map01Data = await getMapJson('map01');
      tileMap = new TileMap(map01Data, tilesetImage);
      camera = new Camera(canvas.width, canvas.height, tileMap.pixelWidth, tileMap.pixelHeight);

      player.reset(SPAWN_X, SPAWN_Y);
      player.fragmentosColetados = 0;
      player.fragmentosPurificados = 0;

      const online = await apiClient.conectar();

      if (online) {
        if (loadFromSave) {
          const r = await apiClient.carregar(1);
          if (r && r.ok && r.heroi) {
            player.x = r.heroi.x || SPAWN_X;
            player.y = r.heroi.y || SPAWN_Y;
            avisar(r.mensagem);
          } else {
            await apiClient.novoJogo(player.nome, 'Guerreiro', player.stats.getInt('VIDA_MAXIMA'));
          }
        } else {
          const r = await apiClient.novoJogo(player.nome, 'Guerreiro', player.stats.getInt('VIDA_MAXIMA'));
          if (r) avisar(r.mensagem);
        }
      } else if (loadFromSave && saveSystem.hasSave()) {
        const saved = saveSystem.load();
        player.applySaveData(saved);
        avisar('Save local carregado (Offline).');
      }

      spawnEnemiesForCurrentMap();
      camera.follow(player);
    }

    let lastTime = performance.now();

    function gameLoop(timestamp) {
      const deltaTime = Math.min((timestamp - lastTime) / 1000, 0.05);
      lastTime = timestamp;

      if (feedbackTimer > 0) {
        feedbackTimer = Math.max(0, feedbackTimer - deltaTime);
      }

      // --- LÓGICA POR ESTADO ---
      if (gameState === 'TITLE') {
        if (input.consumeKey('enter')) {
          setupWorld(false).then(() => { gameState = 'PLAYING'; });
        } else if (input.consumeKey('c')) {
          setupWorld(true).then(() => { gameState = 'PLAYING'; });
        }
      } else if (gameState === 'PLAYING') {
        if (input.consumeKey('escape') || input.consumeKey('p')) {
          gameState = 'PAUSED';
        }

        if (input.consumeKey('g')) {
          saveSystem.save(player);
          if (apiClient.conectado) {
            apiClient.salvar(1).then((r) => avisar(r ? r.mensagem : 'Jogo salvo!'));
          } else {
            avisar('Jogo salvo localmente!');
          }
        }

        if (input.consumeKey('h')) {
          if (apiClient.conectado) {
            apiClient.entrarNoHub().then((r) => {
              if (r && r.ok) {
                gameState = 'HUB';
                avisar(r.mensagem);
              }
            });
          } else {
            gameState = 'HUB';
          }
        }

        if (input.consumeKey('e')) {
          if (apiClient.conectado) {
            apiClient.agir('ABRIR_BAU').then((r) => {
              if (r && r.estado === 'BATALHA' && r.inimigo) {
                const guardiao = new Enemy(player.x, player.y, r.inimigo.nome, r.inimigo.nivel);
                battleSystem.startBattle(player, guardiao, r);
                gameState = 'BATTLE';
              } else if (r) {
                avisar(r.mensagem);
              }
            });
          } else {
            avisar('Nenhum baú próximo no modo offline.');
          }
        }

        player.update(deltaTime, input, tileMap);

        // Verifica se o jogador encostou em algum portal
        checkAndUsePortals();

        const zonaAtual = MAP_REGISTRY[currentMapKey]?.zona || 'FLORESTA_01';

        moveSyncTimer += deltaTime;
        if (apiClient.conectado && moveSyncTimer >= 0.25) {
          moveSyncTimer = 0;
          apiClient.mover(player.x, player.y, zonaAtual).then((r) => {
            if (r && r.estado === 'BATALHA' && r.inimigo && gameState === 'PLAYING') {
              const encInimigo = new Enemy(player.x, player.y, r.inimigo.nome, r.inimigo.nivel);
              battleSystem.startBattle(player, encInimigo, r);
              gameState = 'BATTLE';
            }
          });
        }

        const playerRect = player.getHitboxAt(player.x, player.y);
        for (const enemy of enemies) {
          if (enemy.state === 'DEAD') continue;
          enemy.update(deltaTime, player, tileMap);

          const enemyRect = enemy.getHitboxAt(enemy.x, enemy.y);
          if (rectsOverlap(playerRect, enemyRect)) {
            if (apiClient.conectado) {
              apiClient.mover(player.x + 20, player.y, zonaAtual).then((r) => {
                battleSystem.startBattle(player, enemy, r && r.estado === 'BATALHA' ? r : null);
                gameState = 'BATTLE';
              });
            } else {
              battleSystem.startBattle(player, enemy, null);
              gameState = 'BATTLE';
            }
            break;
          }
        }

        camera.follow(player);
      } else if (gameState === 'HUB') {
        if (input.consumeKey('enter') || input.consumeKey('j')) {
          if (apiClient.conectado) {
            apiClient.purificarFragmentos().then((r) => {
              if (r) avisar(r.mensagem);
            });
          } else if (player.fragmentosColetados > 0) {
            const qtd = player.fragmentosColetados;
            player.fragmentosPurificados += qtd;
            player.fragmentosColetados = 0;
            avisar(`${qtd} fragmento(s) purificado(s)!`);
          } else {
            avisar('Não há fragmentos para purificar.');
          }
        }
        if (input.consumeKey('escape') || input.consumeKey('h')) {
          if (apiClient.conectado) {
            apiClient.sairDoHub().then(() => { gameState = 'PLAYING'; });
          } else {
            gameState = 'PLAYING';
          }
        }
      } else if (gameState === 'BATTLE') {
        const resultado = battleSystem.update(deltaTime, player, input);
        if (resultado === 'VITORIA' || resultado === 'FUGA') {
          gameState = 'PLAYING';
        } else if (resultado === 'DERROTA') {
          gameState = 'GAME_OVER';
        }
      } else if (gameState === 'PAUSED') {
        if (input.consumeKey('escape') || input.consumeKey('p')) {
          gameState = 'PLAYING';
        } else if (input.consumeKey('g')) {
          saveSystem.save(player);
          if (apiClient.conectado) apiClient.salvar(1);
          avisar('Progresso salvo no Slot 1!');
        }
      } else if (gameState === 'GAME_OVER') {
        if (input.consumeKey('r')) {
          if (apiClient.conectado) {
            apiClient.voltarAoMenu().then(() => {
              setupWorld(true).then(() => { gameState = 'PLAYING'; });
            });
          } else {
            setupWorld(saveSystem.hasSave()).then(() => { gameState = 'PLAYING'; });
          }
        }
      }

      // --- RENDERIZAÇÃO ---
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      if (gameState === 'TITLE') {
        renderOverlayScreen(
          ctx,
          'MARK OF BALANCE',
          'Pressione [ENTER] para Novo Jogo',
          'Pressione [C] para Carregar Slot 1'
        );
      } else if (gameState === 'BATTLE') {
        battleSystem.render(ctx, canvas, player);
      } else if (gameState === 'HUB') {
        renderOverlayScreen(
          ctx,
          'SANTUÁRIO (HUB)',
          `Coletados: ${player.fragmentosColetados}   |   Purificados: ${player.fragmentosPurificados}`,
          '[ENTER] Purificar Fragmentos   |   [H] ou [ESC] Voltar ao Mapa'
        );
      } else {
        tileMap.renderAllLayers(ctx, camera);
        for (const enemy of enemies) {
          enemy.render(ctx, camera);
        }
        player.render(ctx, camera);
        renderHUD(ctx, player, apiClient, feedbackMsg, feedbackTimer);

        if (gameState === 'PAUSED') {
          renderOverlayScreen(
            ctx,
            'JOGO PAUSADO',
            '[P] ou [ESC] Continuar   |   [G] Salvar Slot 1',
            feedbackTimer > 0 ? feedbackMsg : ''
          );
        } else if (gameState === 'GAME_OVER') {
          renderOverlayScreen(
            ctx,
            'VOCÊ CAIU EM COMBATE',
            'Pressione [R] para Carregar Último Save'
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