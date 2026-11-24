import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Main {

    private static final int LIMITE_RODADAS = 20;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        Random sorteio = new Random();
        CalculadoraDeBatalha calculadora = new CalculadoraDeBatalha(sorteio);

        System.out.println("=========================================");
        System.out.println("             MARK OF BALANCE             ");
        System.out.println("=========================================");

        // 1. Criação do Herói
        System.out.print("Digite o nome do seu herói: ");
        String nomeHeroi = scanner.nextLine();

        System.out.println("\nEscolha sua classe:");
        System.out.println("1 - Guerreiro");
        System.out.println("2 - Mago");
        System.out.println("3 - Arqueiro");
        System.out.print("Opção: ");
        int opcaoClasse = scanner.nextInt();

        Heroi heroi;
        if (opcaoClasse == 2) {
            heroi = new Mago(nomeHeroi);
        } else if (opcaoClasse == 3) {
            heroi = new Arqueiro(nomeHeroi);
        } else {
            heroi = new Guerreiro(nomeHeroi);
        }

        // 2. Menu Interativo de Melhoria de Atributos (5 pontos iniciais para testar)
        heroi.ganharPontos(5);

        while (heroi.getPontosDisponiveis() > 0) {
            System.out.println("\n-----------------------------------------");
            System.out.println(heroi.getPainelAtributos());
            System.out.println(heroi.getResumoStatus());
            System.out.println("Pontos disponíveis: " + heroi.getPontosDisponiveis());
            System.out.println("Escolha um atributo para melhorar (1.5x):");
            System.out.println("1 - Força");
            System.out.println("2 - Defesa");
            System.out.println("3 - Velocidade");
            System.out.println("4 - Precisão");
            System.out.println("5 - Atributo de Classe");
            System.out.println("0 - Seguir para a batalha sem gastar o restante");
            System.out.print("Opção: ");
            int opcaoAtributo = scanner.nextInt();

            if (opcaoAtributo == 0) break;

            Heroi.Atributo escolhido = switch (opcaoAtributo) {
                case 1 -> Heroi.Atributo.FORCA;
                case 2 -> Heroi.Atributo.DEFESA;
                case 3 -> Heroi.Atributo.VELOCIDADE;
                case 4 -> Heroi.Atributo.PRECISAO;
                case 5 -> Heroi.Atributo.CLASSE;
                default -> null;
            };

            if (escolhido == null) {
                System.out.println("Opção inválida!");
            } else if (heroi.melhorarAtributo(escolhido)) {
                System.out.println(">> " + escolhido.getRotulo() + " melhorado com sucesso!");
            } else {
                System.out.println(">> Não foi possível melhorar (nível máximo 5/5 atingido).");
            }
        }

        // 3. Escolha do Oponente e Escalonamento
        int totalMelhorias = heroi.getTotalMelhorias();
        System.out.println("\nEscolha seu oponente (será escalonado x" + totalMelhorias + " melhorias):");
        System.out.println("1 - Vilão Genérico");
        System.out.println("2 - Vilão Específico");
        System.out.println("3 - Boss");
        System.out.print("Opção: ");
        int opcaoInimigo = scanner.nextInt();

        Inimigo inimigo = switch (opcaoInimigo) {
            case 2 -> new VilaoEspecifico("Vilão Específico");
            case 3 -> new Boss("Rei das Sombras");
            default -> new VilaoGenerico("Vilão Genérico");
        };

        inimigo.escalonar(totalMelhorias);
        heroi.restaurar();

        System.out.println("\n=========================================");
        System.out.println("BATALHA: " + heroi.getNome() + " vs " + inimigo.getNome());
        System.out.println("Status do Inimigo: " + inimigo.getResumoStatus());
        System.out.println("=========================================");

        // 4. Loop de Batalha por Turnos
        int rodada = 1;
        while (heroi.estaVivo() && inimigo.estaVivo() && rodada <= LIMITE_RODADAS) {
            System.out.println("\n--- Rodada " + rodada + " ---");
            System.out.printf("%s: %d/%d HP | %s: %d/%d HP%n",
                    heroi.getNome(), heroi.getVidaAtual(), heroi.getVidaMaxima(),
                    inimigo.getNome(), inimigo.getVidaAtual(), inimigo.getVidaMaxima());

            boolean heroiMaisRapido = heroi.getVelocidade() >= inimigo.getVelocidade();

            if (heroiMaisRapido) {
                executarTurnoJogador(scanner, calculadora, heroi, inimigo);
                if (inimigo.estaVivo()) {
                    calculadora.executarAtaque(inimigo, heroi, inimigo.escolherAtaque(sorteio));
                }
            } else {
                System.out.println(inimigo.getNome() + " é mais rápido e ataca primeiro!");
                calculadora.executarAtaque(inimigo, heroi, inimigo.escolherAtaque(sorteio));
                if (heroi.estaVivo()) {
                    executarTurnoJogador(scanner, calculadora, heroi, inimigo);
                }
            }

            rodada++;
        }

        // 5. Resultado Final
        System.out.println("\n=== FIM DA BATALHA ===");
        System.out.printf("%s: %d/%d | %s: %d/%d%n",
                heroi.getNome(), heroi.getVidaAtual(), heroi.getVidaMaxima(),
                inimigo.getNome(), inimigo.getVidaAtual(), inimigo.getVidaMaxima());

        if (heroi.estaVivo() && !inimigo.estaVivo()) {
            System.out.println("Vitória de " + heroi.getNome() + "!");
        } else if (inimigo.estaVivo() && !heroi.estaVivo()) {
            System.out.println(heroi.getNome() + " foi derrotado...");
        } else {
            System.out.println("Empate por limite de rodadas!");
        }

        scanner.close();
    }

    private static void executarTurnoJogador(Scanner scanner, CalculadoraDeBatalha calculadora, Heroi heroi, Inimigo inimigo) {
        List<Ataque> ataques = heroi.getAtaques();
        System.out.println("\nEscolha seu golpe:");
        for (int i = 0; i < ataques.size(); i++) {
            Ataque a = ataques.get(i);
            System.out.printf("%d - %s (Dano: %d | Precisão: %d%%)%n",
                    (i + 1), a.getNome(), a.getDano(), a.getPrecisao());
        }

        System.out.print("Golpe: ");
        int escolha = scanner.nextInt() - 1;
        if (escolha < 0 || escolha >= ataques.size()) {
            System.out.println("Opção inválida! Usando o primeiro golpe.");
            escolha = 0;
        }

        calculadora.executarAtaque(heroi, inimigo, ataques.get(escolha));
    }
}