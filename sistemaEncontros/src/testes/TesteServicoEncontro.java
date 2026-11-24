package testes;

import java.util.Arrays;
import java.util.Random;

import controle.IniciadorDeBatalha;
import controle.ControladorEncontro;
import controle.RespostaFrontEnd;
import modelo.AreaEncontro;
import modelo.Encontro;
import modelo.Inimigo;
import modelo.Jogador;
import modelo.GatilhoArea;
import modelo.GatilhoAcao;
import modelo.EntidadeMundo;
import servico.ServicoEncontro;
import servico.DetectorPresenca;

/**
 * Testes simples, sem framework, para executar com Java padrão.
 */
public class TesteServicoEncontro {
    public static void main(String[] args) {
        testarForaDaArea();
        testarDentroDaAreaComEncontro();
        testarGatilhoDeArea();
        testarGatilhoDeAcao();
        testarProximidade();
        testarPrioridadeDosGatilhos();
        testarEntidadeMaisProxima();
        testarFormulaDaDistancia();
        testarEntidadeOutraArea();
        testarGatilhoAreaOutraArea();
        testarGatilhoAcaoGlobal();
        testarGatilhoAcaoEscopoDeArea();
        testarChanceZero();
        testarChanceCem();
        testarAreaSemInimigos();
        testarEmpateDeGatilhos();
        testarEmpateDeEntidades();
        testarEntradasInvalidas();
        testarJsonDeResposta();
        System.out.println("Todos os testes passaram.");
    }

    private static void testarGatilhoDeArea() {
        Inimigo inimigo = new Inimigo("GUARDIAO", "Guardiao", 3);
        GatilhoArea gatilho = new GatilhoArea("PORTAL", "AREA_1", 10, 10, 10, 4, 4, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilho);

        Encontro resultado = servico.verificar(new Jogador("J1", 12, 12, "AREA_1"));
        verificar(resultado.ocorreu(), "O gatilho de area deveria ser ativado.");
        verificar(resultado.getOrigem().contains("PORTAL"), "A origem deveria identificar o gatilho.");
    }

    private static void testarGatilhoDeAcao() {
        Inimigo inimigo = new Inimigo("CHEFE", "Guardiao do Bau", 4);
        GatilhoAcao gatilho = GatilhoAcao.daArea("BAU", "AREA_1", "ABRIR_BAU", 10, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilho);

        Encontro resultado = servico.verificar(
            new Jogador("J1", 0, 0, "AREA_1"),
            Arrays.asList("ABRIR_BAU")
        );
        verificar(resultado.ocorreu(), "O gatilho de acao deveria ser ativado.");
        verificar(resultado.getOrigem().contains("BAU"), "A origem deveria identificar o gatilho.");
    }

    private static void testarProximidade() {
        Inimigo inimigo = new Inimigo("LOBISOMEM", "Lobisomem", 5);
        EntidadeMundo entidade = new EntidadeMundo("MONSTRO_1", "AREA_1", 10, 10, 5, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarEntidade(entidade);

        Encontro resultado = servico.verificar(
            new Jogador("J1", 12, 13, "AREA_1")
        );
        verificar(resultado.ocorreu(), "A entidade deveria estar dentro do raio.");
        verificar(resultado.getOrigem().contains("MONSTRO_1"), "A origem deveria identificar a entidade.");
    }

    private static void testarPrioridadeDosGatilhos() {
        Inimigo inimigoComum = new Inimigo("COMUM", "Inimigo comum", 1);
        Inimigo chefe = new Inimigo("CHEFE", "Chefe", 10);
        GatilhoArea gatilhoComum = new GatilhoArea("COMUM", "AREA_1", 10, 0, 0, 10, 10, inimigoComum);
        GatilhoAcao gatilhoChefe = GatilhoAcao.daArea("CHEFE", "AREA_1", "ABRIR_BAU", 20, chefe);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilhoComum);
        servico.cadastrarGatilho(gatilhoChefe);

        Encontro resultado = servico.verificar(
            new Jogador("J1", 5, 5, "AREA_1"),
            Arrays.asList("ABRIR_BAU")
        );

        verificar(resultado.getInimigo().equals(chefe), "O gatilho de maior prioridade deveria vencer.");
        verificar(contador.quantidade == 1, "O início da batalha deveria ser chamado uma única vez.");
    }

    private static void testarEntidadeMaisProxima() {
        Inimigo inimigoLonge = new Inimigo("LONGE", "Inimigo longe", 1);
        Inimigo inimigoPerto = new Inimigo("PERTO", "Inimigo perto", 2);
        EntidadeMundo entidadeLonge = new EntidadeMundo("E1", "AREA_1", 8, 8, 10, inimigoLonge);
        EntidadeMundo entidadePerto = new EntidadeMundo("E2", "AREA_1", 1, 1, 10, inimigoPerto);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarEntidade(entidadeLonge);
        servico.cadastrarEntidade(entidadePerto);

        Encontro resultado = servico.verificar(new Jogador("J1", 0, 0, "AREA_1"));

        verificar(resultado.getInimigo().equals(inimigoPerto),
            "A entidade mais próxima deveria vencer.");
        verificar(resultado.getOrigem().contains("E2"),
            "A origem deveria identificar a entidade mais próxima.");
    }

    private static void testarFormulaDaDistancia() {
        DetectorPresenca detector = new DetectorPresenca();
        Jogador jogador = new Jogador("J1", 0, 0, "AREA_1");
        EntidadeMundo entidade = new EntidadeMundo("E1", "AREA_1", 3, 4, 5, new Inimigo("E", "Entidade", 1));
        double distancia = detector.calcularDistancia(jogador, entidade);
        verificar(Math.abs(distancia - 5.0) < 0.0001,
            "A distância deveria ser calculada pela raiz de dx² + dy².");
    }

    private static void testarForaDaArea() {
        Inimigo inimigo = new Inimigo("SLIME", "Slime", 1);
        AreaEncontro area = new AreaEncontro(
            "AREA_1", 0, 0, 10, 10, 100, Arrays.asList(inimigo)
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarArea(area);

        Encontro resultado = servico.verificar(new Jogador("J1", 20, 20, "AREA_1"));
        verificar(!resultado.ocorreu(), "Não deveria haver encontro fora da área.");
        verificar(contador.quantidade == 0, "A batalha não deveria iniciar fora da área.");
    }

    private static void testarDentroDaAreaComEncontro() {
        Inimigo inimigo = new Inimigo("MORCEGO", "Morcego", 2);
        AreaEncontro area = new AreaEncontro(
            "AREA_1", 0, 0, 10, 10, 100, Arrays.asList(inimigo)
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarArea(area);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(resultado.ocorreu(), "Deveria haver encontro dentro da área.");
        verificar(contador.quantidade == 1, "A batalha deveria ser iniciada uma vez.");
        verificar(resultado.getInimigo().equals(inimigo), "O inimigo sorteado está incorreto.");
    }

    private static void testarEntidadeOutraArea() {
        Inimigo inimigo = new Inimigo("FORA", "Entidade fora", 1);
        EntidadeMundo entidade = new EntidadeMundo("E2", "AREA_2", 5, 5, 10, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarEntidade(entidade);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(!resultado.ocorreu(),
            "Uma entidade de outra área não deveria iniciar encontro.");
        verificar(contador.quantidade == 0,
            "Uma entidade de outra área não deveria iniciar batalha.");
    }

    private static void testarGatilhoAreaOutraArea() {
        Inimigo inimigo = new Inimigo("FORA", "Gatilho fora", 1);
        GatilhoArea gatilho = new GatilhoArea("PORTAL_2", "AREA_2", 10, 0, 0, 10, 10, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilho);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(!resultado.ocorreu(),
            "Um gatilho de área pertencente a outra área não deveria ativar.");
    }

    private static void testarGatilhoAcaoGlobal() {
        Inimigo inimigo = new Inimigo("GLOBAL", "Gatilho global", 5);
        GatilhoAcao gatilho = GatilhoAcao.global("EVENTO", "SALVAR", 10, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilho);

        Encontro resultado = servico.verificar(
            new Jogador("J1", 0, 0, "AREA_QUALQUER"),
            Arrays.asList("SALVAR")
        );
        verificar(resultado.ocorreu(),
            "Um gatilho de ação global deve ativar em qualquer área.");
    }

    private static void testarGatilhoAcaoEscopoDeArea() {
        Inimigo inimigo = new Inimigo("LOCAL", "Gatilho local", 5);
        GatilhoAcao gatilho = GatilhoAcao.daArea("BAU", "AREA_1", "ABRIR_BAU", 10, inimigo);
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilho);

        Encontro fora = servico.verificar(
            new Jogador("J1", 0, 0, "AREA_2"),
            Arrays.asList("ABRIR_BAU")
        );
        verificar(!fora.ocorreu(),
            "Um gatilho de ação local não deve ativar em outra área.");

        Encontro dentro = servico.verificar(
            new Jogador("J1", 0, 0, "AREA_1"),
            Arrays.asList("ABRIR_BAU")
        );
        verificar(dentro.ocorreu(),
            "Um gatilho de ação local deve ativar na área correspondente.");
    }

    private static void testarChanceZero() {
        Inimigo inimigo = new Inimigo("ZERO", "Nunca encontrado", 1);
        AreaEncontro area = new AreaEncontro(
            "AREA_1", 0, 0, 10, 10, 0, Arrays.asList(inimigo)
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarArea(area);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(!resultado.ocorreu(), "Chance zero não deve gerar encontro.");
        verificar(contador.quantidade == 0, "Chance zero não deve iniciar batalha.");
    }

    private static void testarChanceCem() {
        Inimigo inimigo = new Inimigo("CERTO", "Sempre encontrado", 1);
        AreaEncontro area = new AreaEncontro(
            "AREA_1", 0, 0, 10, 10, 100, Arrays.asList(inimigo)
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarArea(area);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(resultado.ocorreu(), "Chance cem deve gerar encontro.");
        verificar(contador.quantidade == 1, "Chance cem deve iniciar uma batalha.");
    }

    private static void testarAreaSemInimigos() {
        AreaEncontro area = new AreaEncontro(
            "AREA_1", 0, 0, 10, 10, 100, Arrays.asList()
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarArea(area);

        Encontro resultado = servico.verificar(new Jogador("J1", 5, 5, "AREA_1"));
        verificar(!resultado.ocorreu(), "Área sem inimigos não deve gerar encontro.");
        verificar(contador.quantidade == 0,
            "Área sem inimigos não deve iniciar batalha.");
    }

    private static void testarEmpateDeGatilhos() {
        Inimigo primeiro = new Inimigo("P1", "Primeiro", 1);
        Inimigo segundo = new Inimigo("P2", "Segundo", 1);
        GatilhoAcao gatilhoPrimeiro = GatilhoAcao.daArea(
            "UM", "AREA_1", "ACAO", 10, primeiro
        );
        GatilhoAcao gatilhoSegundo = GatilhoAcao.daArea(
            "DOIS", "AREA_1", "ACAO", 10, segundo
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarGatilho(gatilhoPrimeiro);
        servico.cadastrarGatilho(gatilhoSegundo);

        Encontro resultado = servico.verificar(
            new Jogador("J1", 0, 0, "AREA_1"),
            Arrays.asList("ACAO")
        );
        verificar(resultado.getInimigo().equals(primeiro),
            "No empate de prioridade deve prevalecer o primeiro gatilho cadastrado.");
    }

    private static void testarEmpateDeEntidades() {
        Inimigo primeiro = new Inimigo("E1", "Primeira", 1);
        Inimigo segundo = new Inimigo("E2", "Segunda", 1);
        EntidadeMundo entidadePrimeira = new EntidadeMundo(
            "UM", "AREA_1", 3, 4, 10, primeiro
        );
        EntidadeMundo entidadeSegunda = new EntidadeMundo(
            "DOIS", "AREA_1", 4, 3, 10, segundo
        );
        ContadorDeBatalha contador = new ContadorDeBatalha();
        ServicoEncontro servico = new ServicoEncontro(new RandomZero(), contador);
        servico.cadastrarEntidade(entidadePrimeira);
        servico.cadastrarEntidade(entidadeSegunda);

        Encontro resultado = servico.verificar(new Jogador("J1", 0, 0, "AREA_1"));
        verificar(resultado.getInimigo().equals(primeiro),
            "No empate de distância deve prevalecer a primeira entidade cadastrada.");
    }

    private static void testarEntradasInvalidas() {
        verificarExcecao(IllegalArgumentException.class, new Operacao() {
            @Override
            public void executar() {
                new Jogador("J1", Double.NaN, 0, "AREA_1");
            }
        });
        verificarExcecao(IllegalArgumentException.class, new Operacao() {
            @Override
            public void executar() {
                new AreaEncontro("A", 0, 0, 10, 10, 101, Arrays.asList());
            }
        });
        verificarExcecao(NullPointerException.class, new Operacao() {
            @Override
            public void executar() {
                new EntidadeMundo("E", null, 0, 0, 5, new Inimigo("I", "I", 1));
            }
        });
        verificarExcecao(IllegalArgumentException.class, new Operacao() {
            @Override
            public void executar() {
                GatilhoAcao.daArea("G", "", "ACAO", 10, new Inimigo("I", "I", 1));
            }
        });
    }

    private static void testarJsonDeResposta() {
        RespostaFrontEnd resposta = new RespostaFrontEnd(
            "INICIAR_BATALHA", "j\\\"1", "AREA\n1", "mensagem\u0001"
        );
        resposta.definirInimigo("I\"1", "Nome\\Teste", 3);
        resposta.definirOrigem("GATILHO\n1");
        String json = resposta.paraJson();
        verificar(json.contains("\\\\"),
            "A resposta JSON deve escapar barras invertidas.");
        verificar(json.contains("\\\""),
            "A resposta JSON deve escapar aspas.");
        verificar(json.contains("\\n"),
            "A resposta JSON deve escapar quebras de linha.");
        verificar(json.contains("\\u0001"),
            "A resposta JSON deve escapar caracteres de controle.");

        ControladorEncontro controlador = new ControladorEncontro(
            new ServicoEncontro(new RandomZero(), new ContadorDeBatalha())
        );
        String invalido = controlador.verificarPresenca(
            "", Double.NaN, 0, "AREA_1"
        );
        verificar(invalido.contains("ENTRADA_INVALIDA"),
            "O controlador deve sinalizar entradas inválidas.");
    }

    private interface Operacao {
        void executar();
    }

    private static void verificarExcecao(Class<? extends Throwable> tipo, Operacao operacao) {
        try {
            operacao.executar();
        } catch (Throwable erro) {
            verificar(tipo.isInstance(erro),
                "Era esperada exceção " + tipo.getSimpleName()
                    + ", mas ocorreu " + erro.getClass().getSimpleName() + ".");
            return;
        }
        throw new AssertionError("Era esperada a exceção " + tipo.getSimpleName() + ".");
    }

    private static void verificar(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new AssertionError(mensagem);
        }
    }

    private static class RandomZero extends Random {
        private static final long serialVersionUID = 1L;

        @Override
        public int nextInt(int limite) {
            return 0;
        }
    }

    private static class ContadorDeBatalha implements IniciadorDeBatalha {
        private int quantidade;

        @Override
        public void iniciar(Encontro encontro) {
            quantidade++;
        }
    }
}
