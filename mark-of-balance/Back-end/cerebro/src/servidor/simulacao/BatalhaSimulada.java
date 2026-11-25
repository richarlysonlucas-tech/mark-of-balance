package servidor.simulacao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import estado.ResultadoBatalha;
import portas.BatalhaEmCurso;
import portas.ComandoBatalha;
import portas.EventoDoJogo;
import portas.OpcaoDoJogo;

/**
 * Batalha por turnos SIMULADA, só para testar o servidor e o front-end enquanto o motor real do Vitor
 * não está ligado. Segue a mesma ideia do motor: menus (PRINCIPAL, ATAQUES, MOCHILA), turno do jogador
 * e turno do inimigo separados, e ações fora de hora viram ACAO_INVALIDA em vez de erro.
 */
public class BatalhaSimulada implements BatalhaEmCurso {
    private static final int PERCENTUAL_DE_FUGA = 50;

    private enum Fim { EM_ANDAMENTO, VITORIA, DERROTA, FUGA }

    private final String nomeDoInimigo;
    private final int nivelDoInimigo;
    private final Random sorteador;

    private int vidaDoHeroi;
    private final int vidaMaximaDoHeroi;
    private int vidaDoInimigo;
    private int pocoes = 2;

    private boolean iniciada;
    private Fim fim = Fim.EM_ANDAMENTO;
    private boolean turnoDoInimigo;
    private String menu = "PRINCIPAL";

    public BatalhaSimulada(int vidaDoHeroi, int vidaMaximaDoHeroi, String nomeDoInimigo,
            int nivelDoInimigo, Random sorteador) {
        this.vidaDoHeroi = vidaDoHeroi;
        this.vidaMaximaDoHeroi = vidaMaximaDoHeroi;
        this.nomeDoInimigo = nomeDoInimigo;
        this.nivelDoInimigo = Math.max(1, nivelDoInimigo);
        this.vidaDoInimigo = 20 * this.nivelDoInimigo;
        this.sorteador = sorteador;
    }

    @Override
    public List<EventoDoJogo> iniciar() {
        if (vidaDoHeroi <= 0) {
            return um("ACAO_INVALIDA", "Não é possível iniciar a batalha: o herói está derrotado.");
        }
        iniciada = true;
        return Arrays.asList(
            new EventoDoJogo("BATALHA_INICIADA", "Batalha iniciada contra " + nomeDoInimigo + "."),
            new EventoDoJogo("TURNO_JOGADOR", "É a sua vez."));
    }

    @Override
    public boolean foiIniciada() { return iniciada; }

    @Override
    public boolean terminou() { return fim != Fim.EM_ANDAMENTO; }

    @Override
    public boolean ehTurnoInimigo() { return turnoDoInimigo; }

    @Override
    public String menuAtual() { return menu; }

    @Override
    public List<EventoDoJogo> executar(ComandoBatalha c) {
        if (!iniciada || terminou()) {
            return invalida("A batalha não está em andamento.");
        }
        if (c.getTipo() == ComandoBatalha.Tipo.TURNO_INIMIGO) {
            return turnoDoInimigo();
        }
        if (turnoDoInimigo) {
            return invalida("Aguarde: é o turno do inimigo.");
        }
        switch (c.getTipo()) {
            case ABRIR_ATAQUES:
                return abrirMenu("ATAQUES");
            case ABRIR_MOCHILA:
                return abrirMenu("MOCHILA");
            case VOLTAR:
                if ("PRINCIPAL".equals(menu)) {
                    return invalida("Você já está no menu principal.");
                }
                menu = "PRINCIPAL";
                return um("MENU_ALTERADO", "Menu principal.");
            case ATACAR:
                return atacar(c.getCategoria(), c.getId());
            case USAR_ITEM:
                return usarItem(c.getId());
            case FUGIR:
                return fugir();
            default:
                return invalida("Comando desconhecido.");
        }
    }

    private List<EventoDoJogo> abrirMenu(String destino) {
        if (!"PRINCIPAL".equals(menu)) {
            return invalida("Volte ao menu principal primeiro.");
        }
        menu = destino;
        return um("MENU_ALTERADO", "Menu: " + destino + ".");
    }

    private List<EventoDoJogo> atacar(String categoria, String id) {
        if (!"ATAQUES".equals(menu)) {
            return invalida("Abra o menu de ataques primeiro.");
        }
        int dano;
        String texto;
        if ("FISICO".equals(categoria) && "golpe".equals(id)) {
            dano = 8 + sorteador.nextInt(5);
            texto = "Você acertou um golpe e causou " + dano + " de dano.";
        } else if ("MAGICO".equals(categoria) && "bola_de_fogo".equals(id)) {
            dano = 14;
            texto = "Sua bola de fogo causou " + dano + " de dano.";
        } else if ("BUFF".equals(categoria) && "foco".equals(id)) {
            int antes = vidaDoHeroi;
            vidaDoHeroi = Math.min(vidaMaximaDoHeroi, vidaDoHeroi + 5);
            List<EventoDoJogo> eventos = new ArrayList<EventoDoJogo>();
            eventos.add(new EventoDoJogo("MENSAGEM", "Você se concentrou e recuperou " + (vidaDoHeroi - antes) + " de vida."));
            eventos.addAll(passarParaOInimigo());
            return eventos;
        } else {
            return invalida("Ataque desconhecido.");
        }
        vidaDoInimigo = Math.max(0, vidaDoInimigo - dano);
        List<EventoDoJogo> eventos = new ArrayList<EventoDoJogo>();
        eventos.add(new EventoDoJogo("MENSAGEM", texto));
        if (vidaDoInimigo == 0) {
            fim = Fim.VITORIA;
            eventos.add(new EventoDoJogo("BATALHA_ENCERRADA", nomeDoInimigo + " foi derrotado!"));
        } else {
            eventos.addAll(passarParaOInimigo());
        }
        return eventos;
    }

    private List<EventoDoJogo> usarItem(String id) {
        if (!"MOCHILA".equals(menu)) {
            return invalida("Abra a mochila primeiro.");
        }
        if (!"pocao_vida".equals(id) || pocoes <= 0) {
            return invalida("Item indisponível.");
        }
        pocoes--;
        int antes = vidaDoHeroi;
        vidaDoHeroi = Math.min(vidaMaximaDoHeroi, vidaDoHeroi + 20);
        List<EventoDoJogo> eventos = new ArrayList<EventoDoJogo>();
        eventos.add(new EventoDoJogo("MENSAGEM", "Você usou uma Poção de Vida e recuperou " + (vidaDoHeroi - antes) + " de vida."));
        eventos.addAll(passarParaOInimigo());
        return eventos;
    }

    private List<EventoDoJogo> fugir() {
        if (!"PRINCIPAL".equals(menu)) {
            return invalida("Volte ao menu principal para fugir.");
        }
        if (sorteador.nextInt(100) < PERCENTUAL_DE_FUGA) {
            fim = Fim.FUGA;
            return um("BATALHA_ENCERRADA", "Você conseguiu fugir.");
        }
        List<EventoDoJogo> eventos = new ArrayList<EventoDoJogo>();
        eventos.add(new EventoDoJogo("MENSAGEM", "A fuga falhou!"));
        eventos.addAll(passarParaOInimigo());
        return eventos;
    }

    private List<EventoDoJogo> passarParaOInimigo() {
        turnoDoInimigo = true;
        menu = "PRINCIPAL";
        return um("TURNO_INIMIGO", "Turno do inimigo.");
    }

    private List<EventoDoJogo> turnoDoInimigo() {
        if (!turnoDoInimigo) {
            return invalida("Ainda não é o turno do inimigo.");
        }
        int dano = 4 + sorteador.nextInt(3 + nivelDoInimigo);
        vidaDoHeroi = Math.max(0, vidaDoHeroi - dano);
        turnoDoInimigo = false;
        List<EventoDoJogo> eventos = new ArrayList<EventoDoJogo>();
        eventos.add(new EventoDoJogo("MENSAGEM", nomeDoInimigo + " atacou e causou " + dano + " de dano."));
        if (vidaDoHeroi == 0) {
            fim = Fim.DERROTA;
            eventos.add(new EventoDoJogo("BATALHA_ENCERRADA", "Você foi derrotado."));
        } else {
            eventos.add(new EventoDoJogo("TURNO_JOGADOR", "É a sua vez."));
        }
        return eventos;
    }

    @Override
    public List<OpcaoDoJogo> listarAtaques(String categoria) {
        if ("FISICO".equals(categoria)) {
            return Arrays.asList(new OpcaoDoJogo("golpe", "Golpe"));
        }
        if ("MAGICO".equals(categoria)) {
            return Arrays.asList(new OpcaoDoJogo("bola_de_fogo", "Bola de Fogo"));
        }
        if ("BUFF".equals(categoria)) {
            return Arrays.asList(new OpcaoDoJogo("foco", "Foco"));
        }
        return Collections.<OpcaoDoJogo>emptyList();
    }

    @Override
    public List<OpcaoDoJogo> listarItens() {
        if (pocoes <= 0) {
            return Collections.<OpcaoDoJogo>emptyList();
        }
        return Arrays.asList(new OpcaoDoJogo("pocao_vida", "Poção de Vida (" + pocoes + ")"));
    }

    @Override
    public ResultadoBatalha resultado() {
        switch (fim) {
            case VITORIA: return ResultadoBatalha.VITORIA;
            case DERROTA: return ResultadoBatalha.DERROTA;
            case FUGA: return ResultadoBatalha.FUGA;
            default: return null;
        }
    }

    @Override
    public int vidaRestanteDoHeroi() { return vidaDoHeroi; }

    @Override
    public int fragmentosGanhos() { return 1 + nivelDoInimigo; }

    @Override
    public String flagDerrotado() { return null; }

    private static List<EventoDoJogo> um(String tipo, String texto) {
        return Collections.singletonList(new EventoDoJogo(tipo, texto));
    }

    private static List<EventoDoJogo> invalida(String texto) {
        return um("ACAO_INVALIDA", texto);
    }
}
