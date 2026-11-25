export class ApiClient {
  constructor() {
    const isLocal =
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1' ||
      window.location.protocol === 'file:';

    this.wsUrl = isLocal
      ? 'ws://127.0.0.1:8080'
      : 'wss://SEU-SERVIDOR-JAVA.onrender.com';

    this.ws = null;
    this.conectado = false;
    this.pendentes = [];
    this.onEstadoAtualizado = null;
  }

  conectar() {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      return Promise.resolve(true);
    }

    return new Promise((resolve) => {
      try {
        this.ws = new WebSocket(this.wsUrl);

        this.ws.onopen = () => {
          this.conectado = true;
          resolve(true);
        };

        this.ws.onerror = () => {
          this.conectado = false;
          resolve(false);
        };

        this.ws.onclose = () => {
          this.conectado = false;
          this.pendentes.forEach((fn) => fn(null));
          this.pendentes = [];
        };

        this.ws.onmessage = (ev) => {
          try {
            const resp = JSON.parse(ev.data);
            if (this.onEstadoAtualizado) {
              this.onEstadoAtualizado(resp);
            }
            const resolver = this.pendentes.shift();
            if (resolver) resolver(resp);
          } catch (e) {
            console.warn('Resposta inválida do cérebro:', ev.data);
          }
        };
      } catch (e) {
        resolve(false);
      }
    });
  }

  enviar(payload) {
    return new Promise((resolve) => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
        resolve(null);
        return;
      }
      this.pendentes.push(resolve);
      this.ws.send(JSON.stringify(payload));
    });
  }

  ping() {
    return this.enviar({ tipo: 'PING' });
  }

  estado() {
    return this.enviar({ tipo: 'ESTADO' });
  }

  novoJogo(nome = 'Arthur', classe = 'Guerreiro', vidaMaxima = 100) {
    return this.enviar({ tipo: 'NOVO_JOGO', nome, classe, vidaMaxima });
  }

  voltarAoMenu() {
    return this.enviar({ tipo: 'MENU' });
  }

  mover(x, y, areaId = 'FLORESTA_01') {
    return this.enviar({ tipo: 'MOVER', x, y, areaId });
  }

  agir(acao) {
    return this.enviar({ tipo: 'AGIR', acao });
  }

  comandoBatalha(comando, extra = {}) {
    return this.enviar({ tipo: 'BATALHA', comando, ...extra });
  }

  opcoesAtaque(categoria = 'FISICO') {
    return this.enviar({ tipo: 'OPCOES_ATAQUE', categoria });
  }

  opcoesItens() {
    return this.enviar({ tipo: 'OPCOES_ITENS' });
  }

  entrarNoHub() {
    return this.enviar({ tipo: 'HUB_ENTRAR' });
  }

  sairDoHub() {
    return this.enviar({ tipo: 'HUB_SAIR' });
  }

  purificarFragmentos() {
    return this.enviar({ tipo: 'PURIFICAR' });
  }

  salvar(slot = 1) {
    return this.enviar({ tipo: 'SALVAR', slot });
  }

  carregar(slot = 1) {
    return this.enviar({ tipo: 'CARREGAR', slot });
  }
}