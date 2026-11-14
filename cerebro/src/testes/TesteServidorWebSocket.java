package testes;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import jogo.OrquestradorDoJogo;
import servidor.ProtocoloDoJogo;
import servidor.ServidorWebSocket;
import servidor.simulacao.JogoDeTeste;
import util.JsonSimples;

/**
 * Testes do servidor WebSocket com um cliente escrito à mão (sockets puros), cobrindo o protocolo
 * em detalhe: handshake, quadros, fragmentação, ping/pong, fechamento, origem, limites e abusos.
 *
 * Rodar: java -cp out testes.TesteServidorWebSocket
 */
public class TesteServidorWebSocket {
    private static int total = 0;
    private static final String CHAVE = "dGhlIHNhbXBsZSBub25jZQ==";

    public static void main(String[] args) throws Exception {
        testarCalculoDoAcceptDaRfc();
        testarOrigens();
        testarHandshakeEEco();
        testarPingPong();
        testarMensagemFragmentada();
        testarTamanhosDeQuadro();
        testarFechamentoNormal();
        testarQuadroSemMascara();
        testarBitsReservadosEOpcodeInvalido();
        testarQuadroGrandeDemais();
        testarUtf8Invalido();
        testarMensagemBinaria();
        testarTratadorQueFalhaNaoDerrubaAConexao();
        testarHandshakesInvalidos();
        testarOrigemBloqueada();
        testarHandshakeLento();
        testarLimiteDeConexoes();
        testarVariosClientesEmParalelo();
        testarPararLiberaAPorta();
        testarPartidaCompletaPelaRede();
        System.out.println("Todos os " + total + " testes passaram.");
    }

    // ---------------------------------------------------------------- testes

    private static void testarCalculoDoAcceptDaRfc() {
        verificar("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=".equals(ServidorWebSocket.calcularAccept(CHAVE)),
            "Exemplo da RFC 6455 deveria bater.");
        fim("Sec-WebSocket-Accept confere com o exemplo da RFC 6455");
    }

    private static void testarOrigens() {
        ServidorWebSocket s = new ServidorWebSocket(InetAddress.getLoopbackAddress(), 0, m -> m);
        String[] boas = {null, "", "null", "http://localhost", "http://localhost:5500", "https://localhost:8443",
            "http://127.0.0.1:3000", "http://[::1]:8000", "HTTP://LOCALHOST:80"};
        for (String o : boas) {
            verificar(s.origemPermitida(o), "Deveria permitir a origem: " + o);
        }
        String[] ruins = {"http://evil.com", "https://localhost.evil.com", "http://127.0.0.1.evil.com",
            "http://evil.com:8080", "ftp://localhost", "file://", "javascript:alert(1)", "http://", "lixo", "http://local host"};
        for (String o : ruins) {
            verificar(!s.origemPermitida(o), "Deveria bloquear a origem: " + o);
        }
        s.permitirOrigem("http://meujogo.local:3000");
        verificar(s.origemPermitida("http://meujogo.local:3000"), "Origem extra deveria passar.");
        verificar(!s.origemPermitida("http://meujogo.local:3001"), "Origem extra é exata.");
        fim("lista de origens permitidas (localhost sim, sites externos não)");
    }

    private static void testarHandshakeEEco() throws Exception {
        try (Fixture f = new Fixture(m -> "eco:" + m)) {
            Cliente c = f.conectar(null);
            c.enviarTexto("olá, mundo");
            verificar("eco:olá, mundo".equals(c.lerTexto()), "Deveria devolver o eco com acentos.");
            c.enviarTexto("");
            verificar("eco:".equals(c.lerTexto()), "Mensagem vazia também funciona.");
            c.fechar();
        }
        fim("handshake e troca de mensagens de texto");
    }

    private static void testarPingPong() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x9, new byte[] {1, 2, 3}, true);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0xA && Arrays.equals(q.carga, new byte[] {1, 2, 3}), "Pong deveria repetir a carga do ping.");
            c.enviarQuadro(true, 0xA, new byte[0], true); // pong espontâneo é ignorado
            c.enviarTexto("ainda vivo");
            verificar("ainda vivo".equals(c.lerTexto()), "Depois de ping/pong a conexão segue normal.");
            c.fechar();
        }
        fim("ping vira pong e pong espontâneo é ignorado");
    }

    private static void testarMensagemFragmentada() throws Exception {
        try (Fixture f = new Fixture(m -> "[" + m + "]")) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(false, 0x1, "parte1-".getBytes(StandardCharsets.UTF_8), true);
            c.enviarQuadro(true, 0x9, new byte[] {9}, true); // ping no meio é permitido
            c.enviarQuadro(false, 0x0, "parte2-".getBytes(StandardCharsets.UTF_8), true);
            c.enviarQuadro(true, 0x0, "fim".getBytes(StandardCharsets.UTF_8), true);
            Quadro pong = c.lerQuadro();
            verificar(pong.opcode == 0xA, "O ping no meio da mensagem é respondido na hora.");
            verificar("[parte1-parte2-fim]".equals(c.lerTexto()), "As partes deveriam ser juntadas.");

            // um caractere multibyte cortado ao meio entre dois fragmentos continua válido
            byte[] eAgudo = "é".getBytes(StandardCharsets.UTF_8);
            c.enviarQuadro(false, 0x1, new byte[] {eAgudo[0]}, true);
            c.enviarQuadro(true, 0x0, new byte[] {eAgudo[1]}, true);
            verificar("[é]".equals(c.lerTexto()), "UTF-8 dividido entre fragmentos deveria funcionar.");
            c.fechar();
        }
        fim("mensagem fragmentada, com ping no meio e UTF-8 dividido");
    }

    private static void testarTamanhosDeQuadro() throws Exception {
        try (Fixture f = new Fixture(m -> String.valueOf(m.length()))) {
            Cliente c = f.conectar(null);
            int[] tamanhos = {0, 1, 125, 126, 127, 65535, 65536, 100000};
            for (int n : tamanhos) {
                c.enviarTexto(repetir('a', n));
                verificar(String.valueOf(n).equals(c.lerTexto()), "Tamanho " + n + " deveria funcionar.");
            }
            c.fechar();
        }
        try (Fixture f = new Fixture(m -> repetir('b', Integer.parseInt(m)))) {
            Cliente c = f.conectar(null);
            for (int n : new int[] {125, 126, 65535, 65536, 100000}) {
                c.enviarTexto(String.valueOf(n));
                verificar(repetir('b', n).equals(c.lerTexto()), "Resposta de " + n + " bytes deveria chegar inteira.");
            }
            c.fechar();
        }
        fim("quadros de 0 a 100000 bytes nos dois sentidos (7, 16 e 64 bits de tamanho)");
    }

    private static void testarFechamentoNormal() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x8, new byte[] {0x03, (byte) 0xE8}, true); // 1000
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1000, "Servidor deveria responder com fechamento 1000.");
            verificar(c.chegouAoFim(), "Depois do fechamento, o servidor fecha a conexão.");
        }
        fim("fechamento educado (1000) e conexão encerrada");
    }

    private static void testarQuadroSemMascara() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x1, "oi".getBytes(StandardCharsets.UTF_8), false);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Quadro sem máscara: fechar com 1002.");
        }
        fim("quadro sem máscara é recusado (1002)");
    }

    private static void testarBitsReservadosEOpcodeInvalido() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarBruto(new byte[] {(byte) 0xC1, (byte) 0x80, 0, 0, 0, 0}); // RSV1 ligado, sem carga
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Bits reservados: 1002.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x3, new byte[0], true);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Opcode inválido: 1002.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(false, 0x9, new byte[0], true); // controle fragmentado
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Controle fragmentado: 1002.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x0, "x".getBytes(StandardCharsets.UTF_8), true); // continuação sem início
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Continuação sem início: 1002.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(false, 0x1, "a".getBytes(StandardCharsets.UTF_8), true);
            c.enviarQuadro(true, 0x1, "b".getBytes(StandardCharsets.UTF_8), true); // texto novo no meio de outro
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Texto novo no meio de outro: 1002.");
        }
        fim("quadros fora da especificação são recusados (1002)");
    }

    private static void testarQuadroGrandeDemais() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            f.servidor.setTamanhoMaximoDaMensagem(1000);
            Cliente c = f.conectar(null);
            // declara 2 GB mas não envia a carga: o servidor precisa recusar sem tentar alocar nem ler
            c.enviarBruto(new byte[] {(byte) 0x81, (byte) 0xFF, 0, 0, 0, 0, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0});
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1009, "Tamanho absurdo: 1009.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            f.servidor.setTamanhoMaximoDaMensagem(1000);
            Cliente c = f.conectar(null);
            c.enviarQuadro(false, 0x1, repetir('a', 600).getBytes(StandardCharsets.UTF_8), true);
            c.enviarQuadro(true, 0x0, repetir('a', 600).getBytes(StandardCharsets.UTF_8), true);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1009, "Soma dos fragmentos passou do limite: 1009.");
        }
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarBruto(new byte[] {(byte) 0x81, (byte) 0xFF, (byte) 0x80, 0, 0, 0, 0, 0, 0, 0}); // bit alto do tamanho ligado
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1002, "Tamanho negativo: 1002.");
        }
        fim("quadros grandes demais ou com tamanho inválido são recusados sem ler a carga");
    }

    private static void testarUtf8Invalido() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x1, new byte[] {(byte) 0xC3, (byte) 0x28}, true);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1007, "UTF-8 inválido: 1007.");
        }
        fim("texto com UTF-8 inválido é recusado (1007)");
    }

    private static void testarMensagemBinaria() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            Cliente c = f.conectar(null);
            c.enviarQuadro(true, 0x2, new byte[] {1, 2, 3}, true);
            Quadro q = c.lerQuadro();
            verificar(q.opcode == 0x8 && codigo(q) == 1003, "Mensagem binária: 1003.");
        }
        fim("mensagem binária é recusada (1003)");
    }

    private static void testarTratadorQueFalhaNaoDerrubaAConexao() throws Exception {
        try (Fixture f = new Fixture(m -> {
            if (m.equals("quebra")) {
                throw new IllegalStateException("boom");
            }
            return "ok:" + m;
        })) {
            Cliente c = f.conectar(null);
            c.enviarTexto("quebra");
            Map<?, ?> erro = (Map<?, ?>) JsonSimples.ler(c.lerTexto());
            verificar(Boolean.FALSE.equals(erro.get("ok")), "Falha do tratador vira resposta de erro.");
            c.enviarTexto("depois");
            verificar("ok:depois".equals(c.lerTexto()), "A conexão continua útil depois da falha.");
            c.fechar();
        }
        fim("exceção no tratador vira erro e a conexão continua");
    }

    private static void testarHandshakesInvalidos() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            verificar(f.statusDeRequisicao("GET / HTTP/1.1\r\nHost: x\r\n\r\n").startsWith("HTTP/1.1 426"),
                "HTTP comum deveria receber 426.");
            verificar(f.statusDeRequisicao(requisicao(CHAVE, null, "Upgrade: websocket\r\nConnection: keep-alive\r\n", "13")).startsWith("HTTP/1.1 400"),
                "Sem Connection: Upgrade deveria receber 400.");
            verificar(f.statusDeRequisicao(requisicao(CHAVE, null, "Upgrade: websocket\r\nConnection: Upgrade\r\n", "8")).startsWith("HTTP/1.1 426"),
                "Versão antiga deveria receber 426.");
            verificar(f.statusDeRequisicao(requisicao("curta", null, "Upgrade: websocket\r\nConnection: Upgrade\r\n", "13")).startsWith("HTTP/1.1 400"),
                "Chave inválida deveria receber 400.");
            verificar(f.statusDeRequisicao(requisicao("!!!não-base64!!!", null, "Upgrade: websocket\r\nConnection: Upgrade\r\n", "13")).startsWith("HTTP/1.1 400"),
                "Chave que não é base64 deveria receber 400.");
            verificar(f.statusDeRequisicao("POST / HTTP/1.1\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n").startsWith("HTTP/1.1 400"),
                "POST deveria receber 400.");
            verificar(f.statusDeRequisicao("lixo sem sentido\r\n\r\n").startsWith("HTTP/1.1 400"),
                "Lixo deveria receber 400.");
            String enorme = "GET / HTTP/1.1\r\nX: " + repetir('a', 20000) + "\r\n\r\n";
            verificar(f.statusDeRequisicao(enorme).startsWith("HTTP/1.1 400"), "Cabeçalho gigante deveria receber 400.");

            // maiúsculas e minúsculas nos cabeçalhos, e Connection com vários valores, são aceitos
            String variada = "GET /jogo HTTP/1.1\r\nhost: x\r\nUPGRADE: WebSocket\r\nconnection: keep-alive, Upgrade\r\n"
                + "sec-websocket-key: " + CHAVE + "\r\nSEC-WEBSOCKET-VERSION: 13\r\n\r\n";
            verificar(f.statusDeRequisicao(variada).startsWith("HTTP/1.1 101"), "Cabeçalhos em qualquer caixa deveriam passar.");
        }
        fim("handshakes inválidos recebem o código HTTP certo e válidos variados passam");
    }

    private static void testarOrigemBloqueada() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            String ruim = requisicao(CHAVE, "http://evil.com", "Upgrade: websocket\r\nConnection: Upgrade\r\n", "13");
            verificar(f.statusDeRequisicao(ruim).startsWith("HTTP/1.1 403"), "Origem externa deveria receber 403.");
            for (String o : new String[] {"http://localhost:5500", "null", "http://127.0.0.1:3000"}) {
                Cliente c = f.conectar(o);
                c.enviarTexto("x");
                verificar("x".equals(c.lerTexto()), "Origem permitida deveria funcionar: " + o);
                c.fechar();
            }
        }
        fim("origem externa recebe 403; localhost e arquivo local passam");
    }

    private static void testarHandshakeLento() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            f.servidor.setTempoLimiteDoHandshakeMs(300);
            Socket s = new Socket(InetAddress.getLoopbackAddress(), f.porta);
            s.setSoTimeout(3000);
            s.getOutputStream().write("GET / HTTP/1.1\r\nHo".getBytes(StandardCharsets.ISO_8859_1)); // nunca termina
            long inicio = System.currentTimeMillis();
            int lido = s.getInputStream().read();
            long demora = System.currentTimeMillis() - inicio;
            verificar(lido == -1 || lido == 'H', "O servidor deveria encerrar (ou responder 400) a conexão lenta.");
            verificar(demora < 2500, "O servidor não pode esperar para sempre por um handshake incompleto.");
            s.close();
            esperar(() -> f.servidor.conexoesAtivas() == 0, 2000);
            verificar(f.servidor.conexoesAtivas() == 0, "A conexão lenta deveria ter sido liberada.");
        }
        fim("handshake que nunca termina é cortado por tempo");
    }

    private static void testarLimiteDeConexoes() throws Exception {
        try (Fixture f = new Fixture(m -> m)) {
            f.servidor.setMaximoDeConexoes(2);
            Cliente a = f.conectar(null);
            Cliente b = f.conectar(null);
            esperar(() -> f.servidor.conexoesAtivas() == 2, 2000);
            verificar(f.statusDeRequisicao(requisicao(CHAVE, null, "Upgrade: websocket\r\nConnection: Upgrade\r\n", "13")).startsWith("HTTP/1.1 503"),
                "A terceira conexão deveria receber 503.");
            a.fechar();
            esperar(() -> f.servidor.conexoesAtivas() == 1, 2000);
            Cliente c = f.conectar(null);
            c.enviarTexto("voltou");
            verificar("voltou".equals(c.lerTexto()), "Liberada uma vaga, novas conexões voltam a funcionar.");
            b.fechar();
            c.fechar();
        }
        fim("limite de conexões simultâneas (503) e liberação de vagas");
    }

    private static void testarVariosClientesEmParalelo() throws Exception {
        try (Fixture f = new Fixture(m -> "r:" + m)) {
            final AtomicInteger falhas = new AtomicInteger();
            List<Thread> threads = new ArrayList<Thread>();
            for (int t = 0; t < 6; t++) {
                final int numero = t;
                Thread th = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Cliente c = f.conectar(null);
                            for (int i = 0; i < 100; i++) {
                                String msg = "c" + numero + "-" + i;
                                c.enviarTexto(msg);
                                if (!("r:" + msg).equals(c.lerTexto())) {
                                    falhas.incrementAndGet();
                                }
                            }
                            c.fechar();
                        } catch (Exception e) {
                            falhas.incrementAndGet();
                        }
                    }
                });
                threads.add(th);
                th.start();
            }
            for (Thread th : threads) {
                th.join(20000);
            }
            verificar(falhas.get() == 0, "Nenhuma resposta trocada ou perdida entre os clientes.");
        }
        fim("6 clientes em paralelo, 100 mensagens cada, sem trocar respostas");
    }

    private static void testarPararLiberaAPorta() throws Exception {
        ServidorWebSocket s = new ServidorWebSocket(InetAddress.getLoopbackAddress(), 0, m -> m);
        int porta = s.iniciar();
        Socket aberto = new Socket(InetAddress.getLoopbackAddress(), porta);
        s.parar();
        esperar(() -> s.conexoesAtivas() == 0, 2000);
        boolean recusou = false;
        try {
            new Socket(InetAddress.getLoopbackAddress(), porta).close();
        } catch (IOException e) {
            recusou = true;
        }
        verificar(recusou, "Depois de parar, a porta não deveria aceitar conexões.");
        aberto.close();
        ServidorWebSocket novo = new ServidorWebSocket(InetAddress.getLoopbackAddress(), porta, m -> m);
        verificar(novo.iniciar() == porta, "A mesma porta deveria poder ser usada de novo.");
        novo.parar();
        fim("parar() fecha as conexões e libera a porta");
    }

    private static void testarPartidaCompletaPelaRede() throws Exception {
        OrquestradorDoJogo jogo = JogoDeTeste.criar(Files.createTempDirectory("ousia-rede-"), new Random(7), 100);
        ProtocoloDoJogo protocolo = new ProtocoloDoJogo(jogo);
        try (Fixture f = new Fixture(protocolo::processar)) {
            Cliente c = f.conectar("http://localhost:5500");
            Map<?, ?> r = c.pedir("{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100,\"idPedido\":\"n1\"}");
            verificar(Boolean.TRUE.equals(r.get("ok")) && "n1".equals(r.get("idPedido")), "Novo jogo pela rede: " + r);
            c.pedir("{\"tipo\":\"MOVER\",\"x\":0,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
            r = c.pedir("{\"tipo\":\"MOVER\",\"x\":16,\"y\":0,\"areaId\":\"FLORESTA_01\"}");
            verificar("BATALHA".equals(r.get("estado")), "Encontro pela rede: " + r);
            String menu = "PRINCIPAL";
            String turno = "JOGADOR";
            for (int i = 0; i < 200 && "BATALHA".equals(r.get("estado")); i++) {
                String msg = "INIMIGO".equals(turno) ? "{\"tipo\":\"BATALHA\",\"comando\":\"TURNO_INIMIGO\"}"
                    : "PRINCIPAL".equals(menu) ? "{\"tipo\":\"BATALHA\",\"comando\":\"ABRIR_ATAQUES\"}"
                    : "{\"tipo\":\"BATALHA\",\"comando\":\"ATACAR\",\"categoria\":\"FISICO\",\"id\":\"golpe\"}";
                r = c.pedir(msg);
                menu = (String) r.get("menu");
                turno = (String) r.get("turno");
            }
            verificar("EXPLORACAO".equals(r.get("estado")) && "VITORIA".equals(r.get("resultadoBatalha")), "Vitória pela rede: " + r);
            verificar(Boolean.TRUE.equals(c.pedir("{\"tipo\":\"SALVAR\",\"slot\":1}").get("ok")), "Salvar pela rede.");

            // reconecta (como se o jogador recarregasse a página) e continua de onde parou
            c.fechar();
            Cliente c2 = f.conectar("http://localhost:5500");
            Map<?, ?> estado = c2.pedir("{\"tipo\":\"ESTADO\"}");
            verificar("EXPLORACAO".equals(estado.get("estado")), "O jogo continua entre conexões.");
            verificar(((Long) ((Map<?, ?>) estado.get("heroi")).get("fragmentosColetados")) >= 2, "O progresso fica no servidor.");
            c2.fechar();
        }
        fim("partida completa pela rede, com reconexão no meio");
    }

    // ---------------------------------------------------------------- apoio

    private static String requisicao(String chave, String origem, String cabecalhos, String versao) {
        return "GET /jogo HTTP/1.1\r\nHost: 127.0.0.1\r\n" + cabecalhos
            + "Sec-WebSocket-Key: " + chave + "\r\nSec-WebSocket-Version: " + versao + "\r\n"
            + (origem == null ? "" : "Origin: " + origem + "\r\n") + "\r\n";
    }

    private static String repetir(char c, int n) {
        char[] cs = new char[n];
        Arrays.fill(cs, c);
        return new String(cs);
    }

    private static int codigo(Quadro q) {
        return q.carga.length < 2 ? -1 : ((q.carga[0] & 0xFF) << 8) | (q.carga[1] & 0xFF);
    }

    private interface Condicao {
        boolean ok();
    }

    private static void esperar(Condicao condicao, long ms) throws InterruptedException {
        long limite = System.currentTimeMillis() + ms;
        while (!condicao.ok() && System.currentTimeMillis() < limite) {
            Thread.sleep(20);
        }
    }

    private static final class Quadro {
        final int opcode;
        final boolean fim;
        final byte[] carga;

        Quadro(int opcode, boolean fim, byte[] carga) {
            this.opcode = opcode;
            this.fim = fim;
            this.carga = carga;
        }
    }

    /** Servidor de teste na porta 0 (qualquer porta livre) com um tratador à escolha. */
    private static final class Fixture implements AutoCloseable {
        final ServidorWebSocket servidor;
        final int porta;

        Fixture(ServidorWebSocket.Tratador tratador) throws IOException {
            servidor = new ServidorWebSocket(InetAddress.getLoopbackAddress(), 0, tratador);
            porta = servidor.iniciar();
        }

        Cliente conectar(String origem) throws IOException {
            Cliente c = new Cliente(porta);
            c.handshake(origem);
            return c;
        }

        /** Envia o texto cru e devolve a primeira linha da resposta HTTP. */
        String statusDeRequisicao(String requisicao) throws IOException {
            Socket s = new Socket(InetAddress.getLoopbackAddress(), porta);
            try {
                s.setSoTimeout(3000);
                s.getOutputStream().write(requisicao.getBytes(StandardCharsets.ISO_8859_1));
                s.getOutputStream().flush();
                StringBuilder linha = new StringBuilder();
                int b;
                while ((b = s.getInputStream().read()) >= 0 && b != '\n') {
                    linha.append((char) b);
                }
                return linha.toString().trim();
            } finally {
                s.close();
            }
        }

        @Override
        public void close() {
            servidor.parar();
        }
    }

    /** Cliente WebSocket mínimo, escrito conforme a RFC 6455, para falar com o servidor. */
    private static final class Cliente {
        private final Socket socket;
        private final DataInputStream entrada;
        private final OutputStream saida;
        private final Random sorteador = new Random(5);

        Cliente(int porta) throws IOException {
            socket = new Socket(InetAddress.getLoopbackAddress(), porta);
            socket.setSoTimeout(5000);
            entrada = new DataInputStream(socket.getInputStream());
            saida = socket.getOutputStream();
        }

        void handshake(String origem) throws IOException {
            byte[] bytes = new byte[16];
            sorteador.nextBytes(bytes);
            String chave = Base64.getEncoder().encodeToString(bytes);
            saida.write(requisicao(chave, origem, "Upgrade: websocket\r\nConnection: Upgrade\r\n", "13")
                .getBytes(StandardCharsets.ISO_8859_1));
            saida.flush();
            String cab = lerCabecalho();
            if (!cab.startsWith("HTTP/1.1 101")) {
                throw new AssertionError("Handshake recusado: " + cab);
            }
            if (!cab.contains("Sec-WebSocket-Accept: " + ServidorWebSocket.calcularAccept(chave))) {
                throw new AssertionError("Accept incorreto: " + cab);
            }
        }

        private String lerCabecalho() throws IOException {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            while (true) {
                int x = entrada.read();
                if (x < 0) {
                    throw new IOException("conexão fechada no handshake");
                }
                b.write(x);
                byte[] a = b.toByteArray();
                int n = a.length;
                if (n >= 4 && a[n - 4] == '\r' && a[n - 3] == '\n' && a[n - 2] == '\r' && a[n - 1] == '\n') {
                    return new String(a, StandardCharsets.ISO_8859_1);
                }
            }
        }

        void enviarTexto(String texto) throws IOException {
            enviarQuadro(true, 0x1, texto.getBytes(StandardCharsets.UTF_8), true);
        }

        void enviarBruto(byte[] bytes) throws IOException {
            saida.write(bytes);
            saida.flush();
        }

        void enviarQuadro(boolean fim, int opcode, byte[] carga, boolean mascarar) throws IOException {
            ByteArrayOutputStream q = new ByteArrayOutputStream();
            q.write((fim ? 0x80 : 0) | opcode);
            int mascaraBit = mascarar ? 0x80 : 0;
            int n = carga.length;
            if (n < 126) {
                q.write(mascaraBit | n);
            } else if (n <= 0xFFFF) {
                q.write(mascaraBit | 126);
                q.write(n >> 8);
                q.write(n);
            } else {
                q.write(mascaraBit | 127);
                for (int i = 7; i >= 0; i--) {
                    q.write((int) (((long) n) >> (8 * i)));
                }
            }
            if (mascarar) {
                byte[] mascara = new byte[4];
                sorteador.nextBytes(mascara);
                q.write(mascara);
                for (int i = 0; i < n; i++) {
                    q.write(carga[i] ^ mascara[i & 3]);
                }
            } else {
                q.write(carga);
            }
            saida.write(q.toByteArray());
            saida.flush();
        }

        Quadro lerQuadro() throws IOException {
            int b0 = entrada.readUnsignedByte();
            int b1 = entrada.readUnsignedByte();
            long n = b1 & 0x7F;
            if (n == 126) {
                n = entrada.readUnsignedShort();
            } else if (n == 127) {
                n = entrada.readLong();
            }
            if ((b1 & 0x80) != 0) {
                throw new AssertionError("O servidor nunca deve mascarar quadros.");
            }
            byte[] carga = new byte[(int) n];
            entrada.readFully(carga);
            return new Quadro(b0 & 0x0F, (b0 & 0x80) != 0, carga);
        }

        String lerTexto() throws IOException {
            Quadro q = lerQuadro();
            if (q.opcode != 0x1 || !q.fim) {
                throw new AssertionError("Era esperado um texto, veio opcode " + q.opcode);
            }
            return new String(q.carga, StandardCharsets.UTF_8);
        }

        Map<?, ?> pedir(String json) throws IOException {
            enviarTexto(json);
            return (Map<?, ?>) JsonSimples.ler(lerTexto());
        }

        /** true se o servidor encerrou a conexão (fim de arquivo ou reset). */
        boolean chegouAoFim() {
            try {
                return entrada.read() < 0;
            } catch (SocketTimeoutException e) {
                return false;
            } catch (IOException e) {
                return true;
            }
        }

        void fechar() {
            try {
                enviarQuadro(true, 0x8, new byte[] {0x03, (byte) 0xE8}, true);
            } catch (IOException e) {
                // já fechado
            }
            try {
                socket.close();
            } catch (IOException e) {
                // já fechado
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
