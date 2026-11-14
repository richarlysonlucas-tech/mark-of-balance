# Protocolo entre o jogo (front-end) e o cérebro

O front-end abre **uma conexão WebSocket** com o cérebro e troca mensagens de **texto JSON**.
Todo pedido recebe exatamente **uma resposta**, na mesma ordem em que foi enviado.

- Endereço: `ws://127.0.0.1:8080` (porta configurável). O servidor só escuta neste computador.
- O progresso do jogo fica **no servidor**: se a página for recarregada, o front reconecta e envia `ESTADO`.
- O front nunca decide regras. Ele envia a **intenção** do jogador e desenha o que a resposta mandar.

## Formato dos pedidos

```json
{ "tipo": "MOVER", "x": 120, "y": 80, "areaId": "FLORESTA_01", "idPedido": 7 }
```

`tipo` é obrigatório. `idPedido` é opcional (inteiro, ou texto de até 100 caracteres) e volta igual na resposta.

## Formato das respostas

Toda resposta tem estes campos:

| Campo | Significado |
|---|---|
| `ok` | `true` se o pedido foi aceito; `false` se foi recusado (a mensagem diz o motivo) |
| `estado` | `MENU`, `EXPLORACAO`, `BATALHA`, `HUB` ou `GAME_OVER` (já depois do pedido) |
| `mensagem` | Texto para mostrar ao jogador ou registrar |
| `eventos` | Lista de `{ "tipo", "texto" }` com o que aconteceu (principalmente na batalha) |
| `tipo` | O tipo do pedido respondido |
| `idPedido` | Presente se o pedido trouxe um |
| `heroi` | Presente quando há jogo em andamento (veja abaixo) |

`heroi`: `nome`, `classe`, `vida`, `vidaMaxima`, `nivel`, `fragmentosColetados`, `fragmentosPurificados`,
`marcaDoEquilibrio`, `x`, `y`, `areaId`, `inventario` (mapa `id → quantidade`).

Quando há batalha, a resposta também traz `menu` (`PRINCIPAL`, `ATAQUES`, `MOCHILA`) e `turno` (`JOGADOR` ou `INIMIGO`).

## Pedidos

| `tipo` | Campos | Quando usar |
|---|---|---|
| `PING` | — | Testar a conexão |
| `ESTADO` | — | Saber onde o jogo está (ao conectar ou recarregar) |
| `NOVO_JOGO` | `nome`, `classe`, `vidaMaxima` (inteiro) | Só no `MENU` |
| `MOVER` | `x`, `y` (números), `areaId` | A cada movimento do jogador, pode ser a cada frame |
| `AGIR` | `acao` (ex.: `"ABRIR_BAU"`) | Interação deliberada do jogador na posição atual |
| `BATALHA` | `comando` (veja abaixo) | Durante a batalha |
| `OPCOES_ATAQUE` | `categoria` (`FISICO`, `MAGICO`, `BUFF`) | Montar o menu de ataques |
| `OPCOES_ITENS` | — | Montar a mochila da batalha |
| `HUB_ENTRAR` / `HUB_SAIR` | — | Entrar e sair do Hub (conselho) |
| `PURIFICAR` | — | Purificar fragmentos (só no Hub) |
| `SALVAR` / `CARREGAR` | `slot` (1 a 3) | Salvar só em `EXPLORACAO` ou `HUB`, nunca em batalha |
| `MENU` | — | Voltar ao menu |

### Comandos de `BATALHA`

| `comando` | Campos extras | Efeito |
|---|---|---|
| `ABRIR_ATAQUES` / `ABRIR_MOCHILA` / `VOLTAR` | — | Navega nos menus |
| `ATACAR` | `categoria`, `id` | Usa um ataque (ids vêm de `OPCOES_ATAQUE`) |
| `USAR_ITEM` | `id` | Usa um item (ids vêm de `OPCOES_ITENS`) |
| `FUGIR` | — | Tenta fugir |
| `TURNO_INIMIGO` | — | Executa o turno do inimigo. O front envia depois de animar o golpe do jogador |

Ação fora de hora (por exemplo, atacar com o menu errado) volta com `ok: true` e um evento `ACAO_INVALIDA`
explicando, e a batalha continua.

## Respostas especiais

**`MOVER` ou `AGIR` que inicia uma batalha** (`estado: "BATALHA"`) traz também:

```json
{ "inimigo": { "id": "SLIME_01", "nome": "Slime Verde", "nivel": 1 }, "origem": "ALEATORIO", "menu": "PRINCIPAL", "turno": "JOGADOR" }
```

**Comando que termina a batalha** traz `resultadoBatalha` (`VITORIA`, `DERROTA` ou `FUGA`), `fragmentosGanhos` e
`vidaRestante`. O `estado` passa a `EXPLORACAO` (vitória ou fuga) ou `GAME_OVER` (derrota).

**`OPCOES_ATAQUE` e `OPCOES_ITENS`** trazem `opcoes: [{ "id", "nome" }]`.

## Exemplo de uma partida

```
→ {"tipo":"NOVO_JOGO","nome":"Arthur","classe":"Guerreiro","vidaMaxima":100}
← {"ok":true,"estado":"EXPLORACAO","mensagem":"Novo jogo iniciado com Arthur.", ... ,"heroi":{"vida":100,...}}

→ {"tipo":"MOVER","x":16,"y":0,"areaId":"FLORESTA_01"}
← {"ok":true,"estado":"BATALHA","mensagem":"Batalha iniciada contra Slime Verde.","inimigo":{...},"menu":"PRINCIPAL","turno":"JOGADOR", ...}

→ {"tipo":"BATALHA","comando":"ABRIR_ATAQUES"}
→ {"tipo":"BATALHA","comando":"ATACAR","categoria":"FISICO","id":"golpe"}
← {"ok":true,"estado":"BATALHA", ... ,"turno":"INIMIGO"}

→ {"tipo":"BATALHA","comando":"TURNO_INIMIGO"}
← {"ok":true,"estado":"BATALHA", ... ,"turno":"JOGADOR"}

(... até a vitória ...)
← {"ok":true,"estado":"EXPLORACAO","resultadoBatalha":"VITORIA","fragmentosGanhos":2, ...}
```

## Como o front deve se comportar

- **Encontros acontecem no servidor.** O front só manda `MOVER`. O servidor decide, a cada passo completo
  (16 unidades), se há encontro; o front não precisa sortear nada.
- **Enquanto `estado` for `BATALHA`, o front deve parar de enviar `MOVER`** (o servidor recusa) e mostrar a tela de batalha.
- **Erros:** `ok: false` não é falha de conexão. É o servidor dizendo "não pode agora". Mostre `mensagem` e siga.
- **Reconexão:** depois de reconectar, envie `ESTADO` para redesenhar a tela com o que o servidor guardou.

## Exemplo em JavaScript (Phaser)

```js
const socket = new WebSocket("ws://127.0.0.1:8080");
let esperando = [];

socket.onmessage = (ev) => {
  const r = JSON.parse(ev.data);
  const resolver = esperando.shift();       // as respostas chegam na ordem dos pedidos
  if (resolver) resolver(r);
  atualizarTela(r);                         // sua função: usa r.estado, r.heroi, r.inimigo, r.eventos...
};

function pedir(obj) {
  return new Promise((resolver) => {
    esperando.push(resolver);
    socket.send(JSON.stringify(obj));
  });
}

// no update() do Phaser, quando o jogador se mexeu:
//   pedir({ tipo: "MOVER", x: jogador.x, y: jogador.y, areaId: "FLORESTA_01" });
```

## Limites e segurança

- Mensagem de até 65.536 caracteres (o protocolo) e 128 KB (a conexão). Acima disso, erro ou conexão fechada.
- Até 8 conexões simultâneas. Só mensagens de **texto** (binário fecha a conexão).
- O servidor só aceita páginas de `localhost`, `127.0.0.1`, `[::1]` ou abertas como arquivo (`file://`).
  Sites de fora não conseguem controlar o jogo. Para liberar outra origem, use `servidor.permitirOrigem("http://...")`.
- Mensagem inválida (JSON quebrado, campo faltando, tipo desconhecido) nunca derruba o jogo: volta `ok: false`.
