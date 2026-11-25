package ousia;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import ousia.entity.Enemy;
import ousia.entity.Player;
import ousia.entity.Stat;

public class GameServer {

    private static final Player heroi = new Player("Heroi de Ousia");

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Rota 1: Retorna status atual do herói em JSON
        server.createContext("/api/player", exchange -> {
            if (handleCors(exchange)) return;
            sendJson(exchange, 200, playerToJson(heroi));
        });

        // Rota 2: Registra derrota de um inimigo, calcula XP e Level Up no Java
        server.createContext("/api/defeat", exchange -> {
            if (handleCors(exchange)) return;

            String query = exchange.getRequestURI().getQuery(); // Ex: ?nome=Slime&nivel=1
            String nome = extrairParam(query, "nome", "Corrompido");
            int nivel = Integer.parseInt(extrairParam(query, "nivel", "1"));

            Enemy inimigo = new Enemy(nome, nivel);
            inimigo.takeDamage(Integer.MAX_VALUE);
            heroi.derrotar(inimigo);

            sendJson(exchange, 200, playerToJson(heroi));
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Servidor de Ousia rodando em http://localhost:8080");
    }

    private static String playerToJson(Player p) {
        return String.format(
            "{\"nome\":\"%s\",\"nivel\":%d,\"xpAtual\":%d,\"xpProximo\":%d," +
            "\"vidaMaxima\":%d,\"energiaMaxima\":%d,\"forca\":%.1f,\"defesa\":%.1f,\"velocidade\":%.1f,\"pontos\":%d}",
            p.getNome(),
            p.getNivel(),
            p.getProgressao().getXpAtual(),
            p.getProgressao().xpParaProximoNivel(),
            p.getVidaMaxima(),
            p.getEnergiaMaxima(),
            p.getStats().get(Stat.FORCA),
            p.getStats().get(Stat.DEFESA),
            p.getStats().get(Stat.VELOCIDADE),
            p.getPontosDeHabilidade()
        );
    }

    // Libera o navegador (Live Server) para acessar o Java sem bloqueio de CORS
    private static boolean handleCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }
        return false;
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String extrairParam(String query, String chave, String padrao) {
        if (query == null) return padrao;
        for (String par : query.split("&")) {
            String[] kv = par.split("=");
            if (kv.length == 2 && kv[0].equals(chave)) return kv[1];
        }
        return padrao;
    }
}