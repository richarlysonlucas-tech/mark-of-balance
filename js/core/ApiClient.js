export class ApiClient {
  constructor(baseUrl = 'http://localhost:8080/api') {
    this.baseUrl = baseUrl;
  }

  async getPlayer() {
    try {
      const res = await fetch(`${this.baseUrl}/player`);
      if (!res.ok) return null;
      return await res.json();
    } catch {
      return null; // Back-end offline: segue rodando localmente
    }
  }

  async registrarInimigoDerrotado(nome, nivel = 1) {
    try {
      const url = `${this.baseUrl}/defeat?nome=${encodeURIComponent(nome)}&nivel=${nivel}`;
      const res = await fetch(url, { method: 'POST' });
      if (!res.ok) return null;
      return await res.json(); // Retorna o nível, XP e atributos atualizados pelo Java
    } catch {
      return null;
    }
  }
}