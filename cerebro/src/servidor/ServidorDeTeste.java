package servidor;

import java.net.BindException;
import java.net.InetAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import jogo.OrquestradorDoJogo;
import servidor.simulacao.JogoDeTeste;

/**
 * Servidor do jogo com módulos SIMULADOS de encontro e batalha. Serve para testar o front-end e a
 * página cliente-teste/index.html usando só o código desta pasta.
 *
 * Rodar: java -cp out servidor.ServidorDeTeste [porta] [pasta-dos-saves]
 * Padrões: porta 8080, pasta "saves". Escuta só em 127.0.0.1 (este computador).
 */
public class ServidorDeTeste {
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

        OrquestradorDoJogo jogo = JogoDeTeste.criar(saves, new Random(), 35);
        ProtocoloDoJogo protocolo = new ProtocoloDoJogo(jogo);
        ServidorWebSocket servidor = new ServidorWebSocket(InetAddress.getLoopbackAddress(), porta, protocolo::processar);
        try {
            int usada = servidor.iniciar();
            System.out.println("Servidor de teste no ar: ws://127.0.0.1:" + usada);
            System.out.println("Saves em: " + saves.toAbsolutePath());
            System.out.println("Abra cliente-teste/index.html no navegador. Ctrl+C para parar.");
        } catch (BindException e) {
            System.out.println("A porta " + porta + " já está em uso. Feche o outro programa ou use outra porta:");
            System.out.println("  java -cp out servidor.ServidorDeTeste 8081");
            return;
        }
        servidor.aguardar();
    }
}
