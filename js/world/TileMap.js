export class TileMap {
  constructor(mapData, tilesetImage) {
    this.mapData = mapData;
    this.tilesetImage = tilesetImage;

    this.tileSize = mapData.tilewidth; // 16
    this.cols = mapData.width; // 40
    this.rows = mapData.height; // 40

    this.pixelWidth = this.cols * this.tileSize; // 640
    this.pixelHeight = this.rows * this.tileSize; // 640
    this.tilesetCols = Math.floor(tilesetImage.width / this.tileSize); // 50

    // Guarda todas as camadas de blocos e os retângulos da camada "Colisoes"
    this.tileLayers = [];
    this.collisionBoxes = [];

    for (const layer of mapData.layers) {
      if (layer.type === 'tilelayer' && layer.visible) {
        this.tileLayers.push(layer);
      } else if (layer.type === 'objectgroup' && layer.name === 'Colisoes') {
        for (const obj of layer.objects) {
          // Ignora objetos sem largura ou altura (pontos soltos no Tiled)
          if (obj.width > 0 && obj.height > 0) {
            this.collisionBoxes.push({
              x: obj.x,
              y: obj.y,
              width: obj.width,
              height: obj.height,
            });
          }
        }
      }
    }
  }

  // Checa colisão AABB entre a hitbox do personagem e os retângulos da camada "Colisoes"
  checkCollision(rect) {
    // 1. Impede sair das bordas do mapa 640x640
    if (
      rect.x < 0 ||
      rect.y < 0 ||
      rect.x + rect.width > this.pixelWidth ||
      rect.y + rect.height > this.pixelHeight
    ) {
      return true;
    }

    // 2. Testa interseção com cada retângulo desenhado na camada "Colisoes"
    for (const box of this.collisionBoxes) {
      const overlapped =
        rect.x < box.x + box.width &&
        rect.x + rect.width > box.x &&
        rect.y < box.y + box.height &&
        rect.y + rect.height > box.y;

      if (overlapped) {
        return true;
      }
    }

    return false;
  }

  renderAllLayers(ctx, camera) {
    const camX = Math.round(camera.x);
    const camY = Math.round(camera.y);

    const startCol = Math.max(0, Math.floor(camX / this.tileSize));
    const endCol = Math.min(this.cols - 1, Math.ceil((camX + camera.viewportWidth) / this.tileSize));
    const startRow = Math.max(0, Math.floor(camY / this.tileSize));
    const endRow = Math.min(this.rows - 1, Math.ceil((camY + camera.viewportHeight) / this.tileSize));

    const FLIP_HORIZONTAL = 0x80000000;
    const FLIP_VERTICAL = 0x40000000;

    for (const layer of this.tileLayers) {
      const data = layer.data;

      for (let row = startRow; row <= endRow; row++) {
        for (let col = startCol; col <= endCol; col++) {
          const rawGid = data[row * this.cols + col];
          if (rawGid === 0) continue;

          // Verifica se o tile foi espelhado no Tiled
          const flipH = (rawGid & FLIP_HORIZONTAL) !== 0;
          const flipV = (rawGid & FLIP_VERTICAL) !== 0;

          // Remove flags de rotação/espelhamento e ajusta caso tenha usado o tileset duplicado (11001+)
          let cleanGid = rawGid & 0x1fffffff;
          if (cleanGid >= 11001) {
            cleanGid -= 11000;
          }
          const tileId = cleanGid - 1;

          const sx = (tileId % this.tilesetCols) * this.tileSize;
          const sy = Math.floor(tileId / this.tilesetCols) * this.tileSize;
          const dx = col * this.tileSize - camX;
          const dy = row * this.tileSize - camY;

          if (flipH || flipV) {
            ctx.save();
            ctx.translate(dx + (flipH ? this.tileSize : 0), dy + (flipV ? this.tileSize : 0));
            ctx.scale(flipH ? -1 : 1, flipV ? -1 : 1);
            ctx.drawImage(
              this.tilesetImage,
              sx, sy, this.tileSize, this.tileSize,
              0, 0, this.tileSize, this.tileSize
            );
            ctx.restore();
          } else {
            ctx.drawImage(
              this.tilesetImage,
              sx, sy, this.tileSize, this.tileSize,
              dx, dy, this.tileSize, this.tileSize
            );
          }
        }
      }
    }
  }
}