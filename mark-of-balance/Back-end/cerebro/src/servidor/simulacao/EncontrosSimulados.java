package servidor.simulacao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import portas.FonteDeEncontros;
import util.JsonSimples;

/**
 * Módulo de encontros SIMULADO, só para testar o servidor e o front-end enquanto o módulo real
 * do Richarlyson não está ligado. Devolve o mesmo formato JSON que o ControladorEncontro real.
 */
public class EncontrosSimulados implements FonteDeEncontros {
    private final Random sorteador;
    private final int chancePercentual;

    public EncontrosSimulados(Random sorteador, int chancePercentual) {
        this.sorteador = sorteador;
        this.chancePercentual = Math.max(0, Math.min(100, chancePercentual));
    }

    @Override
    public String verificarPresenca(String jogadorId, double x, double y, String areaId, List<String> acoes) {
        if (jogadorId == null || jogadorId.trim().isEmpty() || areaId == null || areaId.trim().isEmpty()) {
            return resposta("ENTRADA_INVALIDA", jogadorId, areaId, "jogadorId e areaId são obrigatórios.", "NENHUM", null);
        }
        if (acoes != null && acoes.contains("ABRIR_BAU")) {
            return resposta("INICIAR_BATALHA", jogadorId, areaId, "Um guardião protege o baú!",
                "GATILHO_BAU", inimigo("GUARDIAO_BAU", "Guardião do Baú", 3));
        }
        if (sorteador.nextInt(100) >= chancePercentual) {
            return resposta("SEM_ENCONTRO", jogadorId, areaId, "Nenhum inimigo encontrado nesta posição.", "NENHUM", null);
        }
        Map<String, Object> escolhido = sorteador.nextBoolean()
            ? inimigo("SLIME_01", "Slime Verde", 1)
            : inimigo("MORCEGO_01", "Morcego Sombrio", 2);
        return resposta("INICIAR_BATALHA", jogadorId, areaId, "Encontro iniciado.", "ALEATORIO", escolhido);
    }

    private static Map<String, Object> inimigo(String id, String nome, int nivel) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("nivel", Integer.valueOf(nivel));
        return m;
    }

    private static String resposta(String acao, String jogadorId, String areaId, String mensagem,
            String origem, Map<String, Object> inimigo) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("acao", acao);
        m.put("jogadorId", jogadorId);
        m.put("areaId", areaId);
        m.put("mensagem", mensagem);
        m.put("origem", origem);
        if (inimigo != null) {
            m.put("inimigo", inimigo);
        }
        return JsonSimples.escrever(m);
    }
}
