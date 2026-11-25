package modelo;

import java.util.List;
import java.util.Objects;

/**
 * Gatilho ativado quando o front-end informa que uma ação foi realizada.
 */
public class GatilhoAcao implements Gatilho {
    private final String id;
    private final String acaoNecessaria;
    private final int prioridade;
    private final Inimigo inimigo;
    private final String areaId;
    private final boolean global;

    private GatilhoAcao(String id, String acaoNecessaria, int prioridade,
            Inimigo inimigo, String areaId, boolean global) {
        this.id = validarTexto(id, "id");
        this.acaoNecessaria = validarTexto(acaoNecessaria, "ação");
        if (prioridade < 0) {
            throw new IllegalArgumentException("A prioridade do gatilho não pode ser negativa.");
        }
        this.prioridade = prioridade;
        this.inimigo = Objects.requireNonNull(inimigo, "O gatilho deve possuir um inimigo.");
        this.areaId = global ? null : validarTexto(areaId, "areaId");
        this.global = global;
    }

    public static GatilhoAcao global(String id, String acaoNecessaria,
            int prioridade, Inimigo inimigo) {
        return new GatilhoAcao(id, acaoNecessaria, prioridade, inimigo, null, true);
    }

    public static GatilhoAcao daArea(String id, String areaId, String acaoNecessaria,
            int prioridade, Inimigo inimigo) {
        return new GatilhoAcao(id, acaoNecessaria, prioridade, inimigo, areaId, false);
    }

    private static String validarTexto(String texto, String campo) {
        Objects.requireNonNull(texto, "O " + campo + " do gatilho é obrigatório.");
        if (texto.trim().isEmpty()) {
            throw new IllegalArgumentException("O " + campo + " do gatilho não pode estar vazio.");
        }
        return texto.trim();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPrioridade() {
        return prioridade;
    }

    @Override
    public boolean foiAtivado(Jogador jogador, List<String> acoesRealizadas) {
        Objects.requireNonNull(acoesRealizadas, "A lista de ações realizadas não pode ser nula.");
        return acoesRealizadas.contains(acaoNecessaria);
    }

    @Override
    public boolean pertenceA(String outraAreaId) {
        return global || areaId.equals(outraAreaId);
    }

    @Override
    public Inimigo getInimigo() {
        return inimigo;
    }
}
