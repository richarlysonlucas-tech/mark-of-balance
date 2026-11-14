package interop;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import jogo.OrquestradorDoJogo;
import servidor.ProtocoloDoJogo;
import servidor.ServidorWebSocket;
import servidor.simulacao.JogoDeTeste;
import util.JsonSimples;

/**
 * Teste de interoperabilidade: fala com o servidor usando o cliente WebSocket que vem com o JDK
 * (java.net.http), uma implementação independente da nossa. Exige Java 11 ou superior.
 *
 * Compilar e rodar (a partir da pasta cerebro):
 *   javac -encoding UTF-8 -d out $(find src interop -name '*.java')
 *   java -cp out interop.TesteInteropJdk
 */
public class TesteInteropJdk {
    private static int total = 0;

    public static void main(String[] args) throws Exception {
        OrquestradorDoJogo jogo = JogoDeTeste.criar(Files.createTempDirectory("ousia-interop-"), new Random(7), 100);
        ProtocoloDoJogo protocolo = new ProtocoloDoJogo(jogo);
        ServidorWebSocket servidor = new ServidorWebSocket(InetAddress.getLoopbackAddress(), 0, protocolo::processar);
        int porta = servidor.iniciar();
        try {
            URI uri = URI.create("ws://127.0.0.1:" + porta + "/");
            testarPartida(uri);
            testarMensagemGrandeEPing(uri);
            testarOrigemExterna(porta);
            System.out.println("Todos os " + total + " testes de interoperabilidade com o JDK passaram.");
        } finally {
            servidor.parar();
        }
    }

    private static final class Ouvinte implements WebSocket.Listener {
        final LinkedBlockingQueue<String> textos = new LinkedBlockingQueue<String>();
        final LinkedBlockingQueue<Integer> fechamentos = new LinkedBlockingQueue<Integer>();
        final LinkedBlockingQueue<String> pongs = new LinkedBlockingQueue<String>();
        private final StringBuilder parcial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence dados, boolean ultimo) {
            parcial.append(dados);
            if (ultimo) {
                textos.add(parcial.toString());
                parcial.setLength(0);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onPong(WebSocket ws, java.nio.ByteBuffer msg) {
            pongs.add("pong");
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int codigo, String motivo) {
            fechamentos.add(codigo);
            return null;
        }

        @Override
        public void onOpen(WebSocket ws) {
            ws.request(1);
        }
    }

    private static Map<?, ?> pedir(WebSocket ws, Ouvinte o, String json) throws Exception {
        ws.sendText(json, true).get(5, TimeUnit.SECONDS);
        String r = o.textos.poll(5, TimeUnit.SECONDS);
        if (r == null) {
            throw new AssertionError("Sem resposta para: " + (json.length() > 80 ? json.substring(0, 80) + "..." : json));
        }
        return (Map<?, ?>) JsonSimples.ler(r);
    }

    private static void testarPartida(URI uri) throws Exception {
        Ouvinte o = new Ouvinte();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, o).get(5, TimeUnit.SECONDS);
        Map<?, ?> r = pedir(ws, o, "{\"tipo\":\"NOVO_JOGO\",\"nome\":\"Arthur\",\"classe\":\"Guerreiro\",\"vidaMaxima\":100}");
        verificar(Boolean.TRUE.equals(r.get("ok")), "Novo jogo: " + r);
        pedir(ws, o, "{\"tipo\":\"MOVER\",\"x\":0,\"y\":0,\"areaId\":\"F\"}");
        r = pedir(ws, o, "{\"tipo\":\"MOVER\",\"x\":16,\"y\":0,\"areaId\":\"F\"}");
        verificar("BATALHA".equals(r.get("estado")), "Encontro: " + r);
        String menu = "PRINCIPAL";
        String turno = "JOGADOR";
        for (int i = 0; i < 200 && "BATALHA".equals(r.get("estado")); i++) {
            String msg = "INIMIGO".equals(turno) ? "{\"tipo\":\"BATALHA\",\"comando\":\"TURNO_INIMIGO\"}"
                : "PRINCIPAL".equals(menu) ? "{\"tipo\":\"BATALHA\",\"comando\":\"ABRIR_ATAQUES\"}"
                : "{\"tipo\":\"BATALHA\",\"comando\":\"ATACAR\",\"categoria\":\"FISICO\",\"id\":\"golpe\"}";
            r = pedir(ws, o, msg);
            menu = (String) r.get("menu");
            turno = (String) r.get("turno");
        }
        verificar("VITORIA".equals(r.get("resultadoBatalha")), "Vitória: " + r);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "tchau").get(5, TimeUnit.SECONDS);
        Integer codigo = o.fechamentos.poll(5, TimeUnit.SECONDS);
        verificar(codigo != null && codigo == 1000, "Fechamento educado deveria terminar com 1000, veio: " + codigo);
        fim("cliente do JDK joga uma partida inteira e fecha com 1000");
    }

    private static void testarMensagemGrandeEPing(URI uri) throws Exception {
        Ouvinte o = new Ouvinte();
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, o).get(5, TimeUnit.SECONDS);
        ws.sendPing(java.nio.ByteBuffer.wrap(new byte[] {1, 2})).get(5, TimeUnit.SECONDS);
        verificar(o.pongs.poll(5, TimeUnit.SECONDS) != null, "O JDK deveria receber o pong.");

        // 70.000 caracteres: passa do limite do protocolo (65.536), mas não do limite do transporte (128 KB)
        StringBuilder grande = new StringBuilder("{\"tipo\":\"PING\",\"lixo\":\"");
        for (int i = 0; i < 70000; i++) {
            grande.append('a');
        }
        grande.append("\"}");
        Map<?, ?> r = pedir(ws, o, grande.toString());
        verificar(Boolean.FALSE.equals(r.get("ok")), "Mensagem acima do limite do protocolo deveria virar erro.");
        r = pedir(ws, o, "{\"tipo\":\"PING\"}");
        verificar(Boolean.TRUE.equals(r.get("ok")), "A conexão continua depois do erro.");

        // várias mensagens em sequência sem esperar as respostas (o JDK envia em fila)
        List<CompletableFuture<WebSocket>> envios = new ArrayList<CompletableFuture<WebSocket>>();
        for (int i = 0; i < 50; i++) {
            envios.add(ws.sendText("{\"tipo\":\"PING\",\"idPedido\":" + i + "}", true));
            // o JDK só permite um envio por vez: espera cada um terminar
            envios.get(i).get(5, TimeUnit.SECONDS);
        }
        for (int i = 0; i < 50; i++) {
            Map<?, ?> m = (Map<?, ?>) JsonSimples.ler(o.textos.poll(5, TimeUnit.SECONDS));
            verificar(Long.valueOf(i).equals(m.get("idPedido")), "As respostas devem chegar na ordem dos pedidos.");
        }
        ws.abort();

        // 300 KB: passa do limite do transporte, então o servidor fecha a conexão com 1009
        Ouvinte o2 = new Ouvinte();
        WebSocket ws2 = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(uri, o2).get(5, TimeUnit.SECONDS);
        StringBuilder enorme = new StringBuilder("{\"tipo\":\"PING\",\"lixo\":\"");
        for (int i = 0; i < 300000; i++) {
            enorme.append('a');
        }
        enorme.append("\"}");
        ws2.sendText(enorme.toString(), true);
        Integer codigo = o2.fechamentos.poll(5, TimeUnit.SECONDS);
        verificar(codigo != null && codigo == 1009, "Mensagem acima do limite do transporte deveria fechar com 1009, veio: " + codigo);
        fim("ping do JDK, 70 mil caracteres viram erro de protocolo, 50 pedidos em fila na ordem e 300 KB fecha com 1009");
    }

    private static void testarOrigemExterna(int porta) throws Exception {
        Ouvinte o = new Ouvinte();
        boolean recusou = false;
        try {
            HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "http://evil.com")
                .buildAsync(URI.create("ws://127.0.0.1:" + porta + "/"), o).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            recusou = true;
        }
        verificar(recusou, "O JDK deveria ver a conexão de origem externa recusada.");
        fim("origem externa é recusada também para o cliente do JDK");
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
