package servidor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import estado.DadosDoJogo;
import jogo.OrquestradorDoJogo;
import jogo.RespostaDoJogo;
import portas.ComandoBatalha;
import portas.OpcaoDoJogo;
import util.JsonSimples;

/**
 * Protocolo de mensagens entre o front-end e o jogo: recebe um JSON (texto) e devolve um JSON (texto).
 * Não conhece sockets, então é testável sozinho. Veja PROTOCOLO.md para a lista completa de mensagens.
 *
 * Toda mensagem tem "tipo" e, opcionalmente, "idPedido" (devolvido na resposta para o front
 * saber a que pedido ela corresponde). Mensagem inválida nunca derruba o jogo: vira resposta com ok=false.
 * As chamadas são serializadas (synchronized), porque o jogo é de um jogador só.
 */
public class ProtocoloDoJogo {
    /** Tamanho máximo de uma mensagem, em caracteres. (O servidor WebSocket limita separadamente os bytes.) */
    public static final int TAMANHO_MAXIMO_MENSAGEM = 64 * 1024;
    private static final int TAMANHO_MAXIMO_TEXTO = 200;

    private final OrquestradorDoJogo jogo;

    public ProtocoloDoJogo(OrquestradorDoJogo jogo) {
        if (jogo == null) {
            throw new IllegalArgumentException("jogo é obrigatório.");
        }
        this.jogo = jogo;
    }

    /** Processa uma mensagem do front-end e devolve a resposta em JSON. Nunca lança exceção. */
    public synchronized String processar(String mensagem) {
        Object idPedido = null;
        String tipo = null;
        RespostaDoJogo resposta;
        try {
            Map<?, ?> campos = lerMensagem(mensagem);
            idPedido = idDoPedido(campos);
            tipo = texto(campos, "tipo");
            resposta = executar(tipo, campos);
        } catch (ErroDeProtocolo e) {
            resposta = RespostaDoJogo.erro(jogo.getEstado(), e.getMessage());
        } catch (RuntimeException e) {
            resposta = RespostaDoJogo.erro(jogo.getEstado(), "Erro interno ao processar a mensagem.");
        }

        if (tipo != null) {
            resposta.extra("tipo", tipo);
        }
        if (idPedido != null) {
            resposta.extra("idPedido", idPedido);
        }
        DadosDoJogo dados = jogo.getDados();
        if (dados != null) {
            resposta.extra("heroi", resumoDoHeroi(dados));
        }
        return resposta.paraJson();
    }

    // ---------------------------------------------------------------- mensagens

    private RespostaDoJogo executar(String tipo, Map<?, ?> m) {
        switch (tipo) {
            case "PING":
                return RespostaDoJogo.ok(jogo.getEstado(), "pong");
            case "ESTADO":
                return RespostaDoJogo.ok(jogo.getEstado(), "Estado atual.");
            case "NOVO_JOGO":
                return jogo.novoJogo(texto(m, "nome"), texto(m, "classe"), inteiro(m, "vidaMaxima"));
            case "MOVER":
                return jogo.mover(numero(m, "x"), numero(m, "y"), texto(m, "areaId"));
            case "AGIR":
                return jogo.agir(texto(m, "acao"));
            case "BATALHA":
                return jogo.comandoDeBatalha(comandoDeBatalha(m));
            case "OPCOES_ATAQUE":
                return opcoes(jogo.opcoesDeAtaque(texto(m, "categoria")));
            case "OPCOES_ITENS":
                return opcoes(jogo.opcoesDeItens());
            case "HUB_ENTRAR":
                return jogo.entrarNoHub();
            case "HUB_SAIR":
                return jogo.sairDoHub();
            case "PURIFICAR":
                return jogo.purificarFragmentos();
            case "SALVAR":
                return jogo.salvar(inteiro(m, "slot"));
            case "CARREGAR":
                return jogo.carregar(inteiro(m, "slot"));
            case "MENU":
                return jogo.voltarAoMenu();
            default:
                throw new ErroDeProtocolo("Tipo de mensagem desconhecido: " + limitar(tipo) + ".");
        }
    }

    private static ComandoBatalha comandoDeBatalha(Map<?, ?> m) {
        String comando = texto(m, "comando");
        switch (comando) {
            case "ABRIR_ATAQUES":
                return ComandoBatalha.abrirAtaques();
            case "ABRIR_MOCHILA":
                return ComandoBatalha.abrirMochila();
            case "VOLTAR":
                return ComandoBatalha.voltar();
            case "FUGIR":
                return ComandoBatalha.fugir();
            case "TURNO_INIMIGO":
                return ComandoBatalha.turnoInimigo();
            case "ATACAR":
                return ComandoBatalha.atacar(texto(m, "categoria"), texto(m, "id"));
            case "USAR_ITEM":
                return ComandoBatalha.usarItem(texto(m, "id"));
            default:
                throw new ErroDeProtocolo("Comando de batalha desconhecido: " + limitar(comando) + ".");
        }
    }

    private RespostaDoJogo opcoes(List<OpcaoDoJogo> lista) {
        if (!jogo.haBatalhaEmCurso()) {
            return RespostaDoJogo.erro(jogo.getEstado(), "Não há batalha em andamento.");
        }
        List<Object> itens = new ArrayList<Object>();
        for (OpcaoDoJogo o : lista) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", o.getId());
            item.put("nome", o.getNome());
            itens.add(item);
        }
        return RespostaDoJogo.ok(jogo.getEstado(), lista.isEmpty() ? "Nenhuma opção disponível." : "Opções disponíveis.")
            .extra("opcoes", itens);
    }

    private static Map<String, Object> resumoDoHeroi(DadosDoJogo d) {
        Map<String, Object> h = new LinkedHashMap<String, Object>();
        h.put("nome", d.getNomeHeroi());
        h.put("classe", d.getClasseHeroi());
        h.put("vida", Integer.valueOf(d.getVidaAtual()));
        h.put("vidaMaxima", Integer.valueOf(d.getVidaMaxima()));
        h.put("nivel", Integer.valueOf(d.getNivel()));
        h.put("fragmentosColetados", Integer.valueOf(d.getFragmentosColetados()));
        h.put("fragmentosPurificados", Integer.valueOf(d.getFragmentosPurificados()));
        h.put("marcaDoEquilibrio", Integer.valueOf(d.getMarcaDoEquilibrio()));
        h.put("x", Double.valueOf(d.getPosicaoX()));
        h.put("y", Double.valueOf(d.getPosicaoY()));
        h.put("areaId", d.getAreaId());
        h.put("inventario", new LinkedHashMap<String, Integer>(d.getInventario()));
        return h;
    }

    // ---------------------------------------------------------------- leitura dos campos

    private static Map<?, ?> lerMensagem(String mensagem) {
        if (mensagem == null || mensagem.trim().isEmpty()) {
            throw new ErroDeProtocolo("Mensagem vazia.");
        }
        if (mensagem.length() > TAMANHO_MAXIMO_MENSAGEM) {
            throw new ErroDeProtocolo("Mensagem grande demais.");
        }
        Object raiz;
        try {
            raiz = JsonSimples.ler(mensagem);
        } catch (IllegalArgumentException e) {
            throw new ErroDeProtocolo("JSON inválido: " + e.getMessage());
        }
        if (!(raiz instanceof Map)) {
            throw new ErroDeProtocolo("A mensagem deve ser um objeto JSON.");
        }
        return (Map<?, ?>) raiz;
    }

    private static Object idDoPedido(Map<?, ?> m) {
        Object id = m.get("idPedido");
        if (id == null) {
            return null;
        }
        if (id instanceof Long) {
            return id;
        }
        if (id instanceof String && ((String) id).length() <= 100) {
            return id;
        }
        throw new ErroDeProtocolo("idPedido deve ser um inteiro ou um texto de até 100 caracteres.");
    }

    private static String texto(Map<?, ?> m, String campo) {
        Object v = m.get(campo);
        if (!(v instanceof String) || ((String) v).trim().isEmpty()) {
            throw new ErroDeProtocolo("O campo '" + campo + "' é obrigatório e deve ser um texto.");
        }
        if (((String) v).length() > TAMANHO_MAXIMO_TEXTO) {
            throw new ErroDeProtocolo("O campo '" + campo + "' é grande demais.");
        }
        return (String) v;
    }

    private static double numero(Map<?, ?> m, String campo) {
        Object v = m.get(campo);
        if (v instanceof Long || v instanceof Double) {
            double d = ((Number) v).doubleValue();
            if (!Double.isNaN(d) && !Double.isInfinite(d)) {
                return d;
            }
        }
        throw new ErroDeProtocolo("O campo '" + campo + "' é obrigatório e deve ser um número.");
    }

    private static int inteiro(Map<?, ?> m, String campo) {
        Object v = m.get(campo);
        if (v instanceof Long) {
            long n = ((Long) v).longValue();
            if (n >= Integer.MIN_VALUE && n <= Integer.MAX_VALUE) {
                return (int) n;
            }
        } else if (v instanceof Double) {
            double d = ((Double) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 2147483647.0) {
                return (int) d;
            }
        }
        throw new ErroDeProtocolo("O campo '" + campo + "' é obrigatório e deve ser um número inteiro.");
    }

    private static String limitar(String s) {
        return s.length() > 40 ? s.substring(0, 40) + "..." : s;
    }

    /** Erro de uso do protocolo (mensagem mal formada, campo faltando...). */
    private static final class ErroDeProtocolo extends RuntimeException {
        private static final long serialVersionUID = 1L;

        ErroDeProtocolo(String mensagem) {
            super(mensagem);
        }
    }
}
