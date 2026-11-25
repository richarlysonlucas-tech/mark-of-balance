export class BattleSystem {
  constructor(apiClient = null) {
    this.apiClient = apiClient;
    this.active = false;
    this.onlineMode = false;
    this.enemy = null;

    // Estados alinhados ao ProtocoloDoJogo.java
    this.menu = 'PRINCIPAL'; // 'PRINCIPAL' | 'ATAQUES' | 'MOCHILA'
    this.turno = 'JOGADOR';  // 'JOGADOR' | 'INIMIGO' | 'VITORIA' | 'DERROTA' | 'FUGA'
    this.selectedIndex = 0;
    this.logs = [];
    this.turnTimer = 0;
    this.aguardandoServidor = false;

    this.menuPrincipalOpcoes = [
      { id: 'ABRIR_ATAQUES', nome: 'Atacar' },
      { id: 'ABRIR_MOCHILA', nome: 'Mochila' },
      { id: 'FUGIR', nome: 'Fugir' },
    ];

    // Lista padrão (atualizada automaticamente pelo servidor via OPCOES_ATAQUE quando conectado)
    this.ataquesDisponiveis = [
      { id: 'golpe', categoria: 'FISICO', nome: 'Golpe Físico', dano: 10, precisao: 100, estaminaAtual: 10, estaminaMaxima: 10 },
      { id: 'bola_de_fogo', categoria: 'MAGICO', nome: 'Bola de Fogo', dano: 15, precisao: 85, estaminaAtual: 6, estaminaMaxima: 6 },
      { id: 'foco', categoria: 'BUFF', nome: 'Foco (+5 HP)', dano: 0, precisao: 100, estaminaAtual: 4, estaminaMaxima: 4 },
    ];

    this.itensDisponiveis = [
      { id: 'pocao_vida', nome: 'Poção de Vida', quantidade: 2 },
    ];
  }

  async startBattle(player, enemy, respostaServidor = null) {
    this.active = true;
    this.enemy = enemy;
    this.menu = 'PRINCIPAL';
    this.turno = 'JOGADOR';
    this.selectedIndex = 0;
    this.aguardandoServidor = false;
    this.onlineMode = Boolean(this.apiClient && this.apiClient.conectado && respostaServidor);

    for (const atk of this.ataquesDisponiveis) {
      atk.estaminaAtual = atk.estaminaMaxima;
    }

    if (this.onlineMode && respostaServidor) {
      this.aplicarRespostaServidor(player, respostaServidor);
      await this.carregarOpcoesDoServidor();
    } else {
      this.logs = [`Um ${enemy.nome} (Nv.${enemy.nivel}) apareceu!`];
    }
  }

  async carregarOpcoesDoServidor() {
    if (!this.onlineMode) return;

    const [fisicos, magicos, buffs, itens] = await Promise.all([
      this.apiClient.opcoesAtaque('FISICO'),
      this.apiClient.opcoesAtaque('MAGICO'),
      this.apiClient.opcoesAtaque('BUFF'),
      this.apiClient.opcoesItens(),
    ]);

    const listaAtaques = [];
    if (fisicos?.ok && Array.isArray(fisicos.opcoes)) {
      fisicos.opcoes.forEach((o) => listaAtaques.push({ id: o.id, categoria: 'FISICO', nome: o.nome }));
    }
    if (magicos?.ok && Array.isArray(magicos.opcoes)) {
      magicos.opcoes.forEach((o) => listaAtaques.push({ id: o.id, categoria: 'MAGICO', nome: o.nome }));
    }
    if (buffs?.ok && Array.isArray(buffs.opcoes)) {
      buffs.opcoes.forEach((o) => listaAtaques.push({ id: o.id, categoria: 'BUFF', nome: o.nome }));
    }
    if (listaAtaques.length > 0) {
      this.ataquesDisponiveis = listaAtaques;
    }

    if (itens?.ok && Array.isArray(itens.opcoes)) {
      this.itensDisponiveis = itens.opcoes.map((o) => ({ id: o.id, nome: o.nome, quantidade: 1 }));
    }
  }

  addLog(msg) {
    this.logs.push(msg);
    if (this.logs.length > 3) this.logs.shift();
  }

  aplicarRespostaServidor(player, resp) {
    if (!resp) return;

    if (Array.isArray(resp.eventos) && resp.eventos.length > 0) {
      resp.eventos.forEach((ev) => this.addLog(ev.texto));
    } else if (resp.mensagem) {
      this.addLog(resp.mensagem);
    }

    if (resp.inimigo && this.enemy) {
      this.enemy.nome = resp.inimigo.nome || this.enemy.nome;
      this.enemy.nivel = resp.inimigo.nivel || this.enemy.nivel;
    }

    if (resp.menu) this.menu = resp.menu;
    if (resp.turno) this.turno = resp.turno;

    if (resp.resultadoBatalha === 'VITORIA') {
      if (this.enemy) this.enemy.state = 'DEAD';
      this.turno = 'VITORIA';
      this.addLog('Vitória! Pressione [ENTER]');
    } else if (resp.resultadoBatalha === 'DERROTA') {
      player.state = 'DEAD';
      this.turno = 'DERROTA';
      this.addLog('Você foi derrotado... [ENTER]');
    } else if (resp.resultadoBatalha === 'FUGA') {
      this.turno = 'FUGA';
      this.addLog('Você fugiu! [ENTER]');
    } else if (this.turno === 'INIMIGO') {
      this.turnTimer = 0.9;
    }
  }

  getOpcoesAtuais() {
    if (this.menu === 'ATAQUES') {
      return [...this.ataquesDisponiveis, { id: 'VOLTAR', nome: 'Voltar' }];
    }
    if (this.menu === 'MOCHILA') {
      return [...this.itensDisponiveis, { id: 'VOLTAR', nome: 'Voltar' }];
    }
    return this.menuPrincipalOpcoes;
  }

  async confirmarSelecao(player) {
    if (this.aguardandoServidor || this.turno !== 'JOGADOR') return;

    const opcoes = this.getOpcoesAtuais();
    const escolha = opcoes[this.selectedIndex];
    if (!escolha) return;

    // --- MODO ONLINE (CÉREBRO JAVA) ---
    if (this.onlineMode) {
      this.aguardandoServidor = true;
      let resp = null;

      if (this.menu === 'PRINCIPAL') {
        resp = await this.apiClient.comandoBatalha(escolha.id);
      } else if (this.menu === 'ATAQUES') {
        if (escolha.id === 'VOLTAR') {
          resp = await this.apiClient.comandoBatalha('VOLTAR');
        } else {
          resp = await this.apiClient.comandoBatalha('ATACAR', {
            categoria: escolha.categoria,
            id: escolha.id,
          });
        }
      } else if (this.menu === 'MOCHILA') {
        if (escolha.id === 'VOLTAR') {
          resp = await this.apiClient.comandoBatalha('VOLTAR');
        } else {
          resp = await this.apiClient.comandoBatalha('USAR_ITEM', { id: escolha.id });
          await this.carregarOpcoesDoServidor();
        }
      }

      this.selectedIndex = 0;
      this.aplicarRespostaServidor(player, resp);
      this.aguardandoServidor = false;
      return;
    }

    // --- MODO OFFLINE (FALLBACK LOCAL) ---
    if (this.menu === 'PRINCIPAL') {
      if (escolha.id === 'ABRIR_ATAQUES') {
        this.menu = 'ATAQUES';
        this.selectedIndex = 0;
      } else if (escolha.id === 'ABRIR_MOCHILA') {
        this.menu = 'MOCHILA';
        this.selectedIndex = 0;
      } else if (escolha.id === 'FUGIR') {
        if (Math.random() < 0.5) {
          this.turno = 'FUGA';
          this.addLog('Você conseguiu fugir! [ENTER]');
        } else {
          this.addLog('A fuga falhou!');
          this.turno = 'INIMIGO';
          this.turnTimer = 0.9;
        }
      }
      return;
    }

    if (this.menu === 'ATAQUES') {
      if (escolha.id === 'VOLTAR') {
        this.menu = 'PRINCIPAL';
        this.selectedIndex = 0;
        return;
      }
      this.executarAtaqueOffline(player, escolha);
      return;
    }

    if (this.menu === 'MOCHILA') {
      if (escolha.id === 'VOLTAR') {
        this.menu = 'PRINCIPAL';
        this.selectedIndex = 0;
        return;
      }
      if (escolha.quantidade > 0) {
        escolha.quantidade--;
        const maxHp = player.stats ? player.stats.getInt('VIDA_MAXIMA') : 100;
        player.vida = Math.min(maxHp, player.vida + 20);
        this.addLog('Poção de Vida: +20 HP!');
        this.menu = 'PRINCIPAL';
        this.selectedIndex = 0;
        this.turno = 'INIMIGO';
        this.turnTimer = 0.9;
      }
    }
  }

  executarAtaqueOffline(player, ataque) {
    if (ataque.estaminaAtual <= 0) {
      this.addLog(`${ataque.nome} sem estamina!`);
      return;
    }
    ataque.estaminaAtual--;
    const rolagem = Math.floor(Math.random() * 100) + 1;

    if (ataque.categoria === 'BUFF') {
      const maxHp = player.stats ? player.stats.getInt('VIDA_MAXIMA') : 100;
      player.vida = Math.min(maxHp, player.vida + 5);
      this.addLog('Foco: recuperou 5 HP!');
    } else if (rolagem <= ataque.precisao) {
      const res = this.enemy.stats ? this.enemy.stats.getInt('DEFESA') : 2;
      const dano = Math.max(1, ataque.dano - res);
      this.enemy.vida = Math.max(0, this.enemy.vida - dano);
      this.addLog(`${ataque.nome}: -${dano} HP (Dado: ${rolagem})`);
    } else {
      this.addLog(`${ataque.nome} errou! (Dado: ${rolagem})`);
    }

    this.menu = 'PRINCIPAL';
    this.selectedIndex = 0;

    if (this.enemy.vida <= 0) {
      this.enemy.state = 'DEAD';
      this.turno = 'VITORIA';
      player.fragmentosColetados = (player.fragmentosColetados || 0) + (1 + this.enemy.nivel);
      if (player.ganharXp) player.ganharXp(20 * this.enemy.nivel);
      this.addLog(`Vitória! +${1 + this.enemy.nivel} Fragmentos [ENTER]`);
      return;
    }

    this.turno = 'INIMIGO';
    this.turnTimer = 0.9;
  }

  async executarTurnoInimigo(player) {
    if (this.onlineMode) {
      if (this.aguardandoServidor) return;
      this.aguardandoServidor = true;
      const resp = await this.apiClient.comandoBatalha('TURNO_INIMIGO');
      this.aplicarRespostaServidor(player, resp);
      this.aguardandoServidor = false;
      return;
    }

    const rolagem = Math.floor(Math.random() * 100) + 1;
    if (rolagem <= 85) {
      const danoBruto = this.enemy.stats ? this.enemy.stats.getInt('FORCA') : 6;
      const def = player.stats ? player.stats.getInt('DEFESA') : 5;
      const dano = Math.max(1, danoBruto - def);
      player.vida = Math.max(0, player.vida - dano);
      this.addLog(`${this.enemy.nome} atacou: -${dano} HP`);
    } else {
      this.addLog(`${this.enemy.nome} errou o ataque!`);
    }

    if (player.vida <= 0) {
      player.state = 'DEAD';
      this.turno = 'DERROTA';
      this.addLog('Você foi derrotado... [ENTER]');
    } else {
      this.turno = 'JOGADOR';
    }
  }

  update(deltaTime, player, input) {
    if (!this.active) return null;

    if (this.turno === 'JOGADOR') {
      const opcoes = this.getOpcoesAtuais();
      if (input.consumeKey('w') || input.consumeKey('arrowup')) {
        this.selectedIndex = (this.selectedIndex - 1 + opcoes.length) % opcoes.length;
      }
      if (input.consumeKey('s') || input.consumeKey('arrowdown')) {
        this.selectedIndex = (this.selectedIndex + 1) % opcoes.length;
      }
      if ((input.consumeKey('escape') || input.consumeKey('x')) && this.menu !== 'PRINCIPAL') {
        if (this.onlineMode) {
          this.apiClient.comandoBatalha('VOLTAR').then((r) => this.aplicarRespostaServidor(player, r));
        } else {
          this.menu = 'PRINCIPAL';
        }
        this.selectedIndex = 0;
      }
      if (input.consumeKey('enter') || input.consumeKey('j')) {
        this.confirmarSelecao(player);
      }
    } else if (this.turno === 'INIMIGO') {
      this.turnTimer -= deltaTime;
      if (this.turnTimer <= 0) {
        this.turnTimer = 999; // Evita disparo duplo enquanto aguarda resposta
        this.executarTurnoInimigo(player);
      }
    } else if (this.turno === 'VITORIA' || this.turno === 'FUGA') {
      if (input.consumeKey('enter') || input.consumeKey('j')) {
        this.active = false;
        return this.turno;
      }
    } else if (this.turno === 'DERROTA') {
      if (input.consumeKey('enter') || input.consumeKey('j')) {
        this.active = false;
        return 'DERROTA';
      }
    }

    return null;
  }

  render(ctx, canvas, player) {
    if (!this.active || !this.enemy) return;

    ctx.fillStyle = '#14141f';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Painel do Inimigo
    const enemyMaxHp = this.enemy.stats ? this.enemy.stats.getInt('VIDA_MAXIMA') : 40;
    const enemyHpRatio = Math.max(0, Math.min(1, this.enemy.vida / enemyMaxHp));
    ctx.fillStyle = '#222233';
    ctx.fillRect(110, 6, canvas.width - 116, 30);
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 9px monospace';
    ctx.fillText(`${this.enemy.nome} Nv.${this.enemy.nivel}`, 115, 17);
    ctx.fillStyle = '#331111';
    ctx.fillRect(115, 22, canvas.width - 126, 7);
    ctx.fillStyle = '#e63946';
    ctx.fillRect(115, 22, Math.round((canvas.width - 126) * enemyHpRatio), 7);

    // Sprite do Inimigo
    ctx.fillStyle = '#e63946';
    ctx.fillRect(195, 42, 24, 24);

    // Painel do Jogador
    const playerMaxHp = player.stats ? player.stats.getInt('VIDA_MAXIMA') : 100;
    const playerHpRatio = Math.max(0, Math.min(1, player.vida / playerMaxHp));
    const nivelPlayer = player.progressao ? player.progressao.nivel : 1;
    ctx.fillStyle = '#222233';
    ctx.fillRect(6, 50, 134, 30);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(`${player.nome || 'Arthur'} Nv.${nivelPlayer} (${player.vida}/${playerMaxHp})`, 11, 61);
    ctx.fillStyle = '#331111';
    ctx.fillRect(11, 66, 124, 7);
    ctx.fillStyle = '#2ec4b6';
    ctx.fillRect(11, 66, Math.round(124 * playerHpRatio), 7);

    // Sprite do Jogador
    ctx.fillStyle = '#4ecdc4';
    ctx.fillRect(48, 22, 22, 22);

    // Caixa Inferior
    ctx.fillStyle = '#0d0d14';
    ctx.fillRect(4, 84, canvas.width - 8, canvas.height - 88);
    ctx.strokeStyle = '#4ecdc4';
    ctx.lineWidth = 1;
    ctx.strokeRect(4, 84, canvas.width - 8, canvas.height - 88);

    // Opções do Menu Atual (PRINCIPAL / ATAQUES / MOCHILA)
    const opcoes = this.getOpcoesAtuais();
    ctx.font = 'bold 8px monospace';
    opcoes.forEach((op, i) => {
      const y = 96 + i * 11;
      const selected = i === this.selectedIndex && this.turno === 'JOGADOR';
      ctx.fillStyle = selected ? '#ffe66d' : '#e0e0e0';
      const prefix = selected ? '> ' : '  ';
      const extra = op.estaminaMaxima != null ? ` (${op.estaminaAtual}/${op.estaminaMaxima})` : '';
      ctx.fillText(`${prefix}${op.nome}${extra}`, 10, y);
    });

    // Divisória + Log
    ctx.strokeStyle = '#2b2b40';
    ctx.beginPath();
    ctx.moveTo(8, 142);
    ctx.lineTo(canvas.width - 8, 142);
    ctx.stroke();

    ctx.font = '8px monospace';
    ctx.fillStyle = '#a8dadc';
    this.logs.slice(-2).forEach((line, i) => {
      ctx.fillText(line, 10, 154 + i * 11);
    });
  }
}