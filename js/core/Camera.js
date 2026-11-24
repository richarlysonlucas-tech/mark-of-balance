export class Camera {
  constructor(viewportWidth, viewportHeight, mapWidth, mapHeight) {
    this.x = 0;
    this.y = 0;
    this.viewportWidth = viewportWidth;
    this.viewportHeight = viewportHeight;
    this.mapWidth = mapWidth;
    this.mapHeight = mapHeight;
  }

  follow(target) {
    // Centraliza a câmera no personagem
    this.x = target.x + target.width / 2 - this.viewportWidth / 2;
    this.y = target.y + target.height / 2 - this.viewportHeight / 2;

    // Impede a câmera de mostrar área vazia fora das bordas do mapa
    const maxX = Math.max(0, this.mapWidth - this.viewportWidth);
    const maxY = Math.max(0, this.mapHeight - this.viewportHeight);

    this.x = Math.max(0, Math.min(maxX, this.x));
    this.y = Math.max(0, Math.min(maxY, this.y));
  }
}