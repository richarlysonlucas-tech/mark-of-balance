package servidor.simulacao;

import java.nio.file.Path;
import java.util.Random;

import estado.GameStateController;
import jogo.OrquestradorDoJogo;
import save.SaveEmJson;

/**
 * Monta um jogo completo com módulos SIMULADOS de encontro e batalha, para testar o servidor e o
 * front-end usando só o código desta pasta.
 */
public final class JogoDeTeste {
    private JogoDeTeste() {
    }

    public static OrquestradorDoJogo criar(Path pastaDosSaves, Random sorteador, int chanceDeEncontro) {
        OrquestradorDoJogo jogo = new OrquestradorDoJogo(
            new GameStateController(new SaveEmJson(pastaDosSaves)),
            new EncontrosSimulados(sorteador, chanceDeEncontro),
            (heroi, encontro) -> new BatalhaSimulada(heroi.getVidaAtual(), heroi.getVidaMaxima(),
                encontro.getInimigoNome(), encontro.getInimigoNivel(), sorteador),
            "jogador-1");
        jogo.definirOrigemUnica("GATILHO_BAU");
        return jogo;
    }
}
