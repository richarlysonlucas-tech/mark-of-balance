package integracao;

import java.net.BindException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;

import batalha.Batalha;
import batalha.contratos.ResultadoFuga;
import batalha.simulacao.AtaquesSimulados;
import batalha.simulacao.CombatenteSimulado;
import batalha.simulacao.FugaSimulada;
import batalha.simulacao.IASimulada;
import batalha.simulacao.ItensSimulados;
import controle.ControladorEncontro;
import estado.GameStateController;
import jogo.OrquestradorDoJogo;
import modelo.AreaEncontro;
import modelo.GatilhoAcao;
import modelo.Inimigo;
import save.SaveEmJson;
import servidor.ProtocoloDoJogo;
import servidor.ServidorWebSocket;

/**
 * Servidor WebSocket com os módulos REAIS de encontro (Richarlyson) e de batalha (Vitor, usando as
 * simulações de ataques/itens/fuga/IA que ele deixou no código). Só compila junto com o código deles:
 * use integracao/compilar.sh e depois rode:
 *
 *   java -cp <pasta-de-saida> integracao.ServidorIntegrado [porta] [pasta-dos-saves]
 *
 * Quando o Mateus e os demais entregarem as classes reais, é só trocar a fábrica de batalha abaixo
 * e o cadastro de áreas/inimigos: o resto (protocolo, estado, save) não muda.
 */
public class ServidorIntegrado {
    public static void main(String[] args) throws Exception {
        int porta = 8080;
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Porta inválida: " + args[0]);
                return;
            }
        }
        Path saves = Paths.get(args.length > 1 ? args[1] : "saves");

        // Módulo de encontros do Richarlyson, com uma área e um gatilho de exemplo.
        ControladorEncontro encontros = EncontrosDoRicharlyson.criarControlador(new Random());
        encontros.cadastrarArea(new AreaEncontro("FLORESTA_01", -100000, -100000, 200000, 200000, 35, Arrays.asList(
            new Inimigo("SLIME_01", "Slime Verde", 1),
            new Inimigo("MORCEGO_01", "Morcego Sombrio", 2))));
        encontros.cadastrarGatilho(new GatilhoAcao("BAU", "ABRIR_BAU", 100000,
            new Inimigo("GUARDIAO_BAU", "Guardião do Baú", 3)));

        // Motor de batalha do Vitor, com as simulações dele.
        OrquestradorDoJogo jogo = new OrquestradorDoJogo(
            new GameStateController(new SaveEmJson(saves)),
            EncontrosDoRicharlyson.de(encontros),
            (heroi, encontro) -> {
                final CombatenteSimulado h = new CombatenteSimulado(heroi.getNomeHeroi(), heroi.getVidaAtual());
                CombatenteSimulado inimigo = new CombatenteSimulado(encontro.getInimigoNome(),
                    20 * encontro.getInimigoNivel());
                Batalha b = new Batalha(h, inimigo, new AtaquesSimulados(12), new ItensSimulados(),
                    new FugaSimulada(ResultadoFuga.SUCESSO), new IASimulada());
                return new BatalhaViaMotorV2(b, h::getPv, 1 + encontro.getInimigoNivel(), null);
            },
            "jogador-1");
        jogo.definirOrigemUnica("GATILHO_BAU");

        ServidorWebSocket servidor = new ServidorWebSocket(InetAddress.getLoopbackAddress(), porta,
            new ProtocoloDoJogo(jogo)::processar);
        try {
            int usada = servidor.iniciar();
            System.out.println("Servidor integrado no ar: ws://127.0.0.1:" + usada);
            System.out.println("Saves em: " + saves.toAbsolutePath() + ". Ctrl+C para parar.");
        } catch (BindException e) {
            System.out.println("A porta " + porta + " já está em uso. Use outra porta como primeiro argumento.");
            return;
        }
        servidor.aguardar();
    }
}
