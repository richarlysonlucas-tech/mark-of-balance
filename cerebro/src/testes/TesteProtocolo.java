package testes;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jogo.OrquestradorDoJogo;
import servidor.ProtocoloDoJogo;
import servidor.simulacao.JogoDeTeste;
import util.JsonSimples;

/**
 * Testes do protocolo de mensagens (texto JSON entra, texto JSON sai), sem rede.
 *
 * Rodar: java -cp out testes.TesteProtocolo
 */
public class TesteProtocolo {
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        testarPingEEcoDoIdPedido();
        testarMensagensInvalidasNuncaQuebram();
        testarPartidaCompletaPorMensagens();
        testarNaoSalvaEmBatalhaMasSalvaDepois();
        testarOpcoesForaDeBatalha();
        testarBauDeUsoUnico();
        testarHubEPurificacao();
        testarCaracteresEspeciais();
        testarMenuEComandosForaDeEstado();
        testarVariasThreadsAoMesmoTempo();
        System.out.println("Todos os " + total + " testes passaram.");
    }

    // ---------------------------------------------------------------- testes

    private static void testarPingEEcoDoIdPedido() throws Exception {
        Sessao s = new Sessao(0);
        Map<?, ?> r = s.enviar("{\"tipo\":\"PING\",\"idPedido\":42}");
        verificar(Boolean.TRUE.equals(r.get("ok")), "PING deveria funcionar.");
        verificar("PING".equals(r.get("tipo")), "Deveria devolver o tipo.");
        verificar(Long.valueOf(42).equals(r.get("idPedido")), "Deveria devolver o idPedido numérico.");
        verificar("MENU".equals(r.get("estado")), "Estado inicial é MENU.");
        Map<?, ?> r2 = s.enviar("{\"tipo\":\"PING\",\"idPedido\":\"abc-1\"}");
        verificar("abc-1".equals(r2.get("idPedido")), "Deveria devolver o idPedido de texto.");
        verificar(!r2.containsKey("heroi"), "Sem jogo, não há herói na resposta.");
        fim("PING responde e devolve o idPedido");
    }

    private static void testarMensagensInvalidasNuncaQuebram() throws Exception {
        Sessao s = new Sessao(0);
        StringBuilder grande = new StringBuilder("{\"tipo\":\"PING\",\"lixo\":\"");
        for (int i = 0; i < 70000; i++) {
            grande.append('x');
        }
        grande.append("\"}");
        String[] ruins = {
            "", "   ", "isto não é json", "[1,2]", "42", "null", "\"texto\"", "{}", "{\"tipo\":\"\"}",
            "{\"tipo\":123}", "{\"tipo\":\"VOAR\"}", "{\"tipo\":\"PING\",\"idPedido\":[1]}",
            "{\"tipo\":\"PING\",\"idPedido\":1.5}", "{\"tipo\":\"NOVO_JOGO\"}",
            "{\"tipo\":\"NOVO_JOGO\",\"nome\":\"A\",\"classe\":\"B\",\"vidaMaxima\":1.5}",
            "{\"tipo\":\"NOVO_JOGO\",\"nome\":\"A\",\"classe\":\"B\",\"vidaMaxima\":\"cem\"}",
            "{\"tipo\":\"NOVO_JOGO\",\"nome\":\"A\",\"classe\":\"B\",\"vidaMaxima\":99999999999}",
            "{\"tipo\":\"NOVO_JOGO\",\"nome\":\"A\",\"classe\":\"B\",\"vidaMaxima\":-5}",
            "{\"tipo\":\"MOVER\",\"x\":\"um\",\"y\":2,\"areaId\":\"A\"}",
            "{\"tipo\":\"MOVER\",\"x\":1,\"y\":2}",
            "{\"tipo\":\"BATALHA\"}", "{\"tipo\":\"BATALHA\",\"comando\":\"DANCAR\"}",
            "{\"tipo\":\"BATALHA\",\"comando\":\"ATACAR\"}",
            "{\"tipo\":\"BATALHA\",\"comando\":\"ATACAR\",\"categoria\":\"FISICO\"}",
            "{\"tipo\":\"SALVAR\"}", "{\"tipo\":\"SALVAR\",\"slot\":99}", "{\"tipo\":\"CARREGAR\",\"slot\":1}",
            "{\"tipo\":\"AGIR\",\"acao\":\"   \"}", "{\"tipo\":\"OPCOES_ATAQUE\"}",
            "{\"tipo\":\"PING\"", "{\"tipo\":\"PING\"}}", "{'tipo':'PING'}",
            grande.toString()
        };
        for (String ruim : ruins) {
            Map<?, ?> r = s.enviar(ruim);
            boolean ehPing = ruim.equals("{\"tipo\":\"PING\"}}");
            if (!ehPing) {
                verificar(Boolean.FALSE.equals(r.get("ok")), "Deveria ser erro: "
                    + (ruim.length() > 80 ? ruim.substring(0, 80) + "..." : ruim));
            }
            verificar(r.get("mensagem") instanceof String, "Toda resposta tem mensagem.");
            verificar(r.get("estado") != null, "Toda resposta tem o estado.");
        }
        verificar(Boolean.TRUE.equals(s.enviar("{\"tipo\":\"PING\"}").get("ok")), "Depois dos erros, o jogo continua respondendo.");
        fim("mensagens inválidas viram erro e nunca quebram o jogo");
    }

    private static void testarPartidaCompletaPorMensagens() throws Exception {
        Sessao s = new Sessao(100);
        Map<?, ?> novo = s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100,\"idPedido\":1}");
        verificar(Boolean.TRUE.equals(novo.get("ok")) && "EXPLORACAO".equals(novo.get("estado")), "Novo jogo: " + novo);
        Map<?, ?> heroi = (Map<?, ?>) novo.get("heroi");
        verificar("Arthur".equals(heroi.get("nome")) && Long.valueOf(100).equals(heroi.get("vida")), "Herói na resposta.");

        Map<?, ?> curto = s.enviar("{\"tipo\":\"MOVER\",\"x\":0,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
        verificar(Boolean.TRUE.equals(curto.get("ok")) && "EXPLORACAO".equals(curto.get("estado")), "Primeiro movimento só marca a posição.");
        Map<?, ?> enc = s.enviar("{\"tipo\":\"MOVER\",\"x\":16,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
        verificar("BATALHA".equals(enc.get("estado")), "Deveria entrar em batalha: " + enc);
        verificar(((Map<?, ?>) enc.get("inimigo")).get("nome") != null, "Resposta traz o inimigo.");
        verificar("PRINCIPAL".equals(enc.get("menu")) && "JOGADOR".equals(enc.get("turno")), "Menu e turno.");
        verificar(!((List<?>) enc.get("eventos")).isEmpty(), "Eventos de início.");

        Map<?, ?> ops = s.enviar("{\"tipo\":\"OPCOES_ATAQUE\",\"categoria\":\"FISICO\"}");
        verificar(Boolean.TRUE.equals(ops.get("ok")) && ((List<?>) ops.get("opcoes")).size() == 1, "Opções de ataque: " + ops);
        Map<?, ?> itens = s.enviar("{\"tipo\":\"OPCOES_ITENS\"}");
        verificar(((List<?>) itens.get("opcoes")).size() == 1, "Opções de itens: " + itens);

        Map<?, ?> fim = jogarAteOFim(s);
        verificar("EXPLORACAO".equals(fim.get("estado")), "Inimigo fraco: deveria vencer. " + fim);
        verificar("VITORIA".equals(fim.get("resultadoBatalha")), "Resultado: vitória.");
        Map<?, ?> h = (Map<?, ?>) fim.get("heroi");
        verificar(((Long) h.get("fragmentosColetados")) >= 2, "Fragmentos de recompensa.");
        fim("partida completa por mensagens: novo jogo, andar, encontro, batalha, vitória");
    }

    private static void testarNaoSalvaEmBatalhaMasSalvaDepois() throws Exception {
        Sessao s = new Sessao(100);
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        s.andarAteEncontro();
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"SALVAR\",\"slot\":1}").get("ok")), "Não salva em batalha.");
        jogarAteOFim(s);
        verificar(Boolean.TRUE.equals(s.enviar("{\"tipo\":\"SALVAR\",\"slot\":1}").get("ok")), "Salva depois da batalha.");
        Map<?, ?> antes = (Map<?, ?>) s.enviar("{\"tipo\":\"ESTADO\"}").get("heroi");

        Sessao outra = new Sessao(0, s.pasta);
        Map<?, ?> carregado = outra.enviar("{\"tipo\":\"CARREGAR\",\"slot\":1}");
        verificar(Boolean.TRUE.equals(carregado.get("ok")) && "EXPLORACAO".equals(carregado.get("estado")), "Carrega: " + carregado);
        Map<?, ?> depois = (Map<?, ?>) carregado.get("heroi");
        verificar(antes.get("vida").equals(depois.get("vida")), "Vida preservada.");
        verificar(antes.get("fragmentosColetados").equals(depois.get("fragmentosColetados")), "Fragmentos preservados.");
        fim("salvar e carregar por mensagens");
    }

    private static void testarOpcoesForaDeBatalha() throws Exception {
        Sessao s = new Sessao(0);
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"OPCOES_ATAQUE\",\"categoria\":\"FISICO\"}").get("ok")), "Sem batalha, sem opções.");
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"OPCOES_ITENS\"}").get("ok")), "Sem batalha, sem itens.");
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"BATALHA\",\"comando\":\"FUGIR\"}").get("ok")), "Sem batalha, sem comando.");
        fim("opções e comandos de batalha fora da batalha são recusados");
    }

    private static void testarBauDeUsoUnico() throws Exception {
        Sessao s = new Sessao(0);
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        Map<?, ?> abrir = s.enviar("{\"tipo\":\"AGIR\",\"acao\":\"ABRIR_BAU\"}");
        verificar("BATALHA".equals(abrir.get("estado")) && "GATILHO_BAU".equals(abrir.get("origem")), "Baú inicia batalha: " + abrir);
        jogarAteOFim(s);
        Map<?, ?> denovo = s.enviar("{\"tipo\":\"AGIR\",\"acao\":\"ABRIR_BAU\"}");
        verificar("EXPLORACAO".equals(denovo.get("estado")), "O baú não dispara de novo.");
        verificar(((String) denovo.get("mensagem")).contains("já resolvido"), "Explica o motivo.");
        fim("baú de uso único não repete");
    }

    private static void testarHubEPurificacao() throws Exception {
        Sessao s = new Sessao(100);
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        s.andarAteEncontro();
        jogarAteOFim(s);
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"PURIFICAR\"}").get("ok")), "Fora do hub não purifica.");
        verificar("HUB".equals(s.enviar("{\"tipo\":\"HUB_ENTRAR\"}").get("estado")), "Entra no hub.");
        Map<?, ?> p = s.enviar("{\"tipo\":\"PURIFICAR\"}");
        verificar(Boolean.TRUE.equals(p.get("ok")), "Purifica no hub: " + p);
        Map<?, ?> h = (Map<?, ?>) p.get("heroi");
        verificar(Long.valueOf(0).equals(h.get("fragmentosColetados")) && ((Long) h.get("fragmentosPurificados")) >= 2, "Fragmentos purificados.");
        verificar("EXPLORACAO".equals(s.enviar("{\"tipo\":\"HUB_SAIR\"}").get("estado")), "Sai do hub.");
        fim("hub e purificação por mensagens");
    }

    private static void testarCaracteresEspeciais() throws Exception {
        Sessao s = new Sessao(0);
        Map<?, ?> r = s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Ação \\\"Herói\\\" \\u00da\\n日本\",\"classe\":\"Mago\",\"vidaMaxima\":40}");
        verificar(Boolean.TRUE.equals(r.get("ok")), "Nome especial deveria ser aceito: " + r);
        verificar("Ação \"Herói\" Ú\n日本".equals(((Map<?, ?>) r.get("heroi")).get("nome")), "Nome preservado.");
        fim("aspas, acentos, escapes e unicode nos campos");
    }

    private static void testarMenuEComandosForaDeEstado() throws Exception {
        Sessao s = new Sessao(0);
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"MOVER\",\"x\":1,\"y\":1,\"areaId\":\"A\"}").get("ok")), "No menu não se anda.");
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"HUB_ENTRAR\"}").get("ok")), "No menu não se entra no hub.");
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        verificar(Boolean.FALSE.equals(s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Outro\",\"classe\":\"Mago\",\"vidaMaxima\":10}").get("ok")), "Novo jogo só no menu.");
        verificar("MENU".equals(s.enviar("{\"tipo\":\"MENU\"}").get("estado")), "Volta ao menu.");
        fim("comandos fora do estado correto são recusados");
    }

    private static void testarVariasThreadsAoMesmoTempo() throws Exception {
        final Sessao s = new Sessao(50);
        s.enviar("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        final AtomicInteger falhas = new AtomicInteger();
        List<Thread> threads = new ArrayList<Thread>();
        for (int t = 0; t < 8; t++) {
            final int numero = t;
            Thread th = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < 60; i++) {
                            String msg = i % 3 == 0 ? "{\"tipo\":\"PING\",\"idPedido\":" + (numero * 1000 + i) + "}"
                                : i % 3 == 1 ? "{\"tipo\":\"ESTADO\"}"
                                : "{\"tipo\":\"MOVER\",\"x\":" + (i * 20) + ",\"y\":0,\"areaId\":\"A\"}";
                            Map<?, ?> r = (Map<?, ?>) JsonSimples.ler(s.protocolo.processar(msg));
                            if (r.get("estado") == null || r.get("mensagem") == null) {
                                falhas.incrementAndGet();
                            }
                        }
                    } catch (RuntimeException e) {
                        falhas.incrementAndGet();
                    }
                }
            });
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) {
            th.join(15000);
        }
        verificar(falhas.get() == 0, "Nenhuma resposta quebrada com threads em paralelo.");
        verificar(Boolean.TRUE.equals(s.enviar("{\"tipo\":\"ESTADO\"}").get("ok")), "O jogo continua íntegro.");
        fim("várias threads ao mesmo tempo não quebram o jogo");
    }

    // ---------------------------------------------------------------- apoio

    /** Joga a batalha (sempre ataque físico) até ela acabar; devolve a última resposta. */
    private static Map<?, ?> jogarAteOFim(Sessao s) {
        Map<?, ?> r = null;
        String menu = "PRINCIPAL";
        String turno = "JOGADOR";
        for (int i = 0; i < 200; i++) {
            String msg;
            if ("INIMIGO".equals(turno)) {
                msg = "{\"tipo\":\"BATALHA\",\"comando\":\"TURNO_INIMIGO\"}";
            } else if ("PRINCIPAL".equals(menu)) {
                msg = "{\"tipo\":\"BATALHA\",\"comando\":\"ABRIR_ATAQUES\"}";
            } else {
                msg = "{\"tipo\":\"BATALHA\",\"comando\":\"ATACAR\",\"categoria\":\"FISICO\",\"id\":\"golpe\"}";
            }
            r = s.enviar(msg);
            if (!"BATALHA".equals(r.get("estado"))) {
                return r;
            }
            menu = (String) r.get("menu");
            turno = (String) r.get("turno");
        }
        throw new AssertionError("A batalha não terminou em 200 comandos.");
    }

    private static class Sessao {
        final ProtocoloDoJogo protocolo;
        final java.nio.file.Path pasta;

        Sessao(int chanceDeEncontro) throws Exception {
            this(chanceDeEncontro, Files.createTempDirectory("ousia-protocolo-"));
        }

        Sessao(int chanceDeEncontro, java.nio.file.Path pasta) {
            this.pasta = pasta;
            OrquestradorDoJogo jogo = JogoDeTeste.criar(pasta, new Random(7), chanceDeEncontro);
            this.protocolo = new ProtocoloDoJogo(jogo);
        }

        Map<?, ?> enviar(String mensagem) {
            String resposta = protocolo.processar(mensagem);
            Object json = JsonSimples.ler(resposta);
            if (!(json instanceof Map)) {
                throw new AssertionError("A resposta não é um objeto JSON: " + resposta);
            }
            return (Map<?, ?>) json;
        }

        void andarAteEncontro() {
            enviar("{\"tipo\":\"MOVER\",\"x\":0,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
            Map<?, ?> r = enviar("{\"tipo\":\"MOVER\",\"x\":16,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
            if (!"BATALHA".equals(r.get("estado"))) {
                throw new AssertionError("Era esperado um encontro: " + r);
            }
        }
    }

    private static void fim(String nome) {
        total++;
        System.out.println("  ok  " + nome);
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }
}
