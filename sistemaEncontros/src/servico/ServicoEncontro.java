package servico;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.Objects;

import controle.IniciadorDeBatalha;
import modelo.AreaEncontro;
import modelo.Encontro;
import modelo.Inimigo;
import modelo.Jogador;
import modelo.Gatilho;
import modelo.EntidadeMundo;

/**
 * Contém a regra de encontros e não conhece detalhes de tela ou JSON.
 */
public class ServicoEncontro {
    private final Map<String, AreaEncontro> areas;
    private final DetectorPresenca detectorPresenca;
    private final Random sorteador;
    private final IniciadorDeBatalha iniciadorDeBatalha;
    private final List<Gatilho> gatilhos;
    private final List<EntidadeMundo> entidades;

    public ServicoEncontro(IniciadorDeBatalha iniciadorDeBatalha) {
        this(new Random(), iniciadorDeBatalha);
    }

    public ServicoEncontro(Random sorteador, IniciadorDeBatalha iniciadorDeBatalha) {
        this.sorteador = Objects.requireNonNull(sorteador, "O sorteador é obrigatório.");
        this.iniciadorDeBatalha = Objects.requireNonNull(iniciadorDeBatalha,
            "O iniciador de batalha é obrigatório.");
        this.detectorPresenca = new DetectorPresenca();
        this.areas = new HashMap<String, AreaEncontro>();
        this.gatilhos = new ArrayList<Gatilho>();
        this.entidades = new ArrayList<EntidadeMundo>();
    }

    public void cadastrarArea(AreaEncontro area) {
        Objects.requireNonNull(area, "A área é obrigatória.");
        areas.put(area.getId(), area);
    }

    public void cadastrarGatilho(Gatilho gatilho) {
        Objects.requireNonNull(gatilho, "O gatilho é obrigatório.");
        gatilhos.add(gatilho);
    }

    public void cadastrarEntidade(EntidadeMundo entidade) {
        Objects.requireNonNull(entidade, "A entidade é obrigatória.");
        entidades.add(entidade);
    }

    /**
     * Recebe a posição informada pelo front-end e decide se ocorre um encontro.
     * A batalha só é acionada quando o encontro de fato é criado.
     */
    public Encontro verificar(Jogador jogador) {
        return verificar(jogador, new ArrayList<String>());
    }

    public Encontro verificar(Jogador jogador, List<String> acoesRealizadas) {
        Objects.requireNonNull(jogador, "O jogador é obrigatório.");
        List<String> acoes = acoesRealizadas == null
            ? new ArrayList<String>()
            : acoesRealizadas;
        validarAcoes(acoes);
        String areaAtualId = jogador.getAreaAtualId();
        Gatilho gatilho = encontrarGatilho(jogador, acoes, areaAtualId);
        if (gatilho != null) {
            return iniciar(Encontro.comEncontro(jogador, gatilho.getInimigo(), "GATILHO_" + gatilho.getId()));
        }

        EntidadeMundo entidade = encontrarEntidadeMaisProxima(jogador, areaAtualId);
        if (entidade != null) {
            return iniciar(Encontro.comEncontro(jogador, entidade.getInimigo(), "PROXIMIDADE_" + entidade.getId()));
        }

        AreaEncontro area = areas.get(areaAtualId);
        if (area == null || !detectorPresenca.estaNaArea(
                jogador.getPosicaoX(), jogador.getPosicaoY(), area)) {
            return Encontro.semEncontro(jogador);
        }

        int valorSorteado = sorteador.nextInt(100);
        if (valorSorteado >= area.getChanceEncontroPercentual()) {
            return Encontro.semEncontro(jogador);
        }

        if (area.getInimigosPossiveis().isEmpty()) {
            return Encontro.semEncontro(jogador);
        }

        int indice = sorteador.nextInt(area.getInimigosPossiveis().size());
        Inimigo inimigo = area.getInimigosPossiveis().get(indice);
        Encontro encontro = Encontro.comEncontro(jogador, inimigo);
        return iniciar(encontro);
    }

    private void validarAcoes(List<String> acoesRealizadas) {
        for (String acao : acoesRealizadas) {
            if (acao == null || acao.trim().isEmpty()) {
                throw new IllegalArgumentException("A lista de ações não pode conter valores nulos ou vazios.");
            }
        }
    }

    private Gatilho encontrarGatilho(Jogador jogador, List<String> acoesRealizadas, String areaAtualId) {
        Gatilho escolhido = null;
        for (Gatilho gatilho : gatilhos) {
            if (gatilho.pertenceA(areaAtualId)
                    && gatilho.foiAtivado(jogador, acoesRealizadas)
                    && (escolhido == null || gatilho.getPrioridade() > escolhido.getPrioridade())) {
                escolhido = gatilho;
            }
        }
        return escolhido;
    }

    private EntidadeMundo encontrarEntidadeMaisProxima(Jogador jogador, String areaAtualId) {
        EntidadeMundo maisProxima = null;
        double menorDistancia = Double.MAX_VALUE;
        for (EntidadeMundo entidade : entidades) {
            if (!entidade.pertenceA(areaAtualId)) {
                continue;
            }
            double distancia = detectorPresenca.calcularDistancia(jogador, entidade);
            if (distancia <= entidade.getRaioDeteccao() && distancia < menorDistancia) {
                maisProxima = entidade;
                menorDistancia = distancia;
            }
        }
        return maisProxima;
    }

    private Encontro iniciar(Encontro encontro) {
        iniciadorDeBatalha.iniciar(encontro);
        return encontro;
    }
}
