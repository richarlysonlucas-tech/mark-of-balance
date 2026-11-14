package servidor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Servidor WebSocket (RFC 6455) em Java puro, sem bibliotecas. Atende mensagens de texto:
 * cada mensagem recebida vira uma chamada ao {@link Tratador} e o texto devolvido é enviado de volta.
 *
 * Segurança e robustez:
 * - escuta só no endereço informado (use o loopback: 127.0.0.1);
 * - confere o cabeçalho Origin, para que uma página qualquer da internet não controle o jogo local;
 * - limita conexões simultâneas, tamanho de mensagem e tempo do handshake;
 * - recusa quadros mal formados (sem máscara, bits reservados, UTF-8 inválido, tamanho absurdo)
 *   fechando a conexão com o código de erro correto do protocolo.
 */
public class ServidorWebSocket {
    /** Recebe o texto de uma mensagem e devolve o texto da resposta (ou null para não responder). */
    public interface Tratador {
        String tratar(String mensagem);
    }

    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int TAMANHO_MAXIMO_CABECALHO = 8192;
    private static final String RESPOSTA_ERRO_INTERNO = "{\"ok\":false,\"mensagem\":\"Erro interno do servidor.\"}";

    private final InetAddress endereco;
    private final int portaSolicitada;
    private final Tratador tratador;
    private final Set<String> origensExtras = new CopyOnWriteArraySet<String>();
    private final Set<Conexao> conexoes = ConcurrentHashMap.newKeySet();

    private volatile int maximoDeConexoes = 8;
    private volatile int tempoLimiteDoHandshakeMs = 5000;
    private volatile int tamanhoMaximoDaMensagem = 128 * 1024;
    private volatile boolean parado;

    private ServerSocket servidor;
    private Thread aceitador;
    private ExecutorService pool;

    public ServidorWebSocket(InetAddress endereco, int porta, Tratador tratador) {
        if (endereco == null || tratador == null || porta < 0 || porta > 65535) {
            throw new IllegalArgumentException("endereço, porta (0 a 65535) e tratador são obrigatórios.");
        }
        this.endereco = endereco;
        this.portaSolicitada = porta;
        this.tratador = tratador;
    }

    // ------------------------------------------------------------ configuração

    /** Permite uma origem além de localhost/127.0.0.1/file (ex.: "http://meujogo.local:3000"). */
    public void permitirOrigem(String origem) {
        if (origem != null && !origem.trim().isEmpty()) {
            origensExtras.add(origem.trim());
        }
    }

    public void setMaximoDeConexoes(int maximo) {
        this.maximoDeConexoes = Math.max(1, maximo);
    }

    public void setTempoLimiteDoHandshakeMs(int milissegundos) {
        this.tempoLimiteDoHandshakeMs = Math.max(50, milissegundos);
    }

    public void setTamanhoMaximoDaMensagem(int bytes) {
        this.tamanhoMaximoDaMensagem = Math.max(125, bytes);
    }

    // ------------------------------------------------------------ ciclo de vida

    /** Começa a escutar. @return a porta realmente usada (útil quando se pede a porta 0). */
    public synchronized int iniciar() throws IOException {
        if (servidor != null) {
            throw new IllegalStateException("O servidor já foi iniciado.");
        }
        ServerSocket novo = new ServerSocket();
        try {
            novo.setReuseAddress(true);
            novo.bind(new InetSocketAddress(endereco, portaSolicitada));
        } catch (IOException e) {
            novo.close();
            throw e;
        }
        servidor = novo;
        pool = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ws-conexao");
                t.setDaemon(true);
                return t;
            }
        });
        aceitador = new Thread(new Runnable() {
            @Override
            public void run() {
                aceitar();
            }
        }, "ws-aceitador");
        aceitador.setDaemon(true);
        aceitador.start();
        return servidor.getLocalPort();
    }

    public int getPorta() {
        return servidor == null ? -1 : servidor.getLocalPort();
    }

    public int conexoesAtivas() {
        return conexoes.size();
    }

    /** Bloqueia até o servidor ser parado (usado pelo main para manter o programa vivo). */
    public void aguardar() throws InterruptedException {
        Thread t = aceitador;
        if (t != null) {
            t.join();
        }
    }

    public synchronized void parar() {
        parado = true;
        if (servidor != null) {
            try {
                servidor.close();
            } catch (IOException e) {
                // já está fechando
            }
        }
        for (Conexao c : conexoes) {
            c.fecharAgora();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private void aceitar() {
        while (!parado) {
            Socket socket;
            try {
                socket = servidor.accept();
            } catch (IOException e) {
                return; // servidor fechado
            }
            if (conexoes.size() >= maximoDeConexoes) {
                recusar(socket);
                continue;
            }
            Conexao conexao = new Conexao(socket);
            conexoes.add(conexao);
            try {
                pool.execute(conexao);
            } catch (RuntimeException e) {
                conexoes.remove(conexao);
                conexao.fecharAgora();
            }
        }
    }

    private void recusar(Socket socket) {
        try {
            socket.setSoTimeout(500);
            responderHttp(socket.getOutputStream(), "503 Service Unavailable", "Servidor cheio.", null);
        } catch (IOException e) {
            // quem tentou conectar já foi embora
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // nada a fazer
            }
        }
    }

    // ------------------------------------------------------------ utilitários públicos (testáveis)

    /** Calcula o Sec-WebSocket-Accept a partir da chave enviada pelo cliente (RFC 6455, seção 4.2.2). */
    public static String calcularAccept(String chave) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((chave + GUID).getBytes(StandardCharsets.ISO_8859_1));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Decide se a origem (cabeçalho Origin) pode conectar. Sem cabeçalho (programas que não são
     * navegadores), "null" (página aberta como arquivo) e localhost/127.0.0.1/[::1] são aceitos.
     */
    public boolean origemPermitida(String origem) {
        if (origem == null || origem.trim().isEmpty() || "null".equals(origem.trim())) {
            return true;
        }
        String o = origem.trim();
        if (origensExtras.contains(o)) {
            return true;
        }
        try {
            URI uri = new URI(o);
            String esquema = uri.getScheme();
            String host = uri.getHost();
            if (host == null || esquema == null
                    || !("http".equalsIgnoreCase(esquema) || "https".equalsIgnoreCase(esquema))) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
        } catch (java.net.URISyntaxException e) {
            return false;
        }
    }

    private static void responderHttp(OutputStream out, String status, String corpo, String cabecalhoExtra)
            throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(status).append("\r\n");
        sb.append("Content-Type: text/plain; charset=utf-8\r\n");
        sb.append("Content-Length: ").append(bytes.length).append("\r\n");
        sb.append("Connection: close\r\n");
        if (cabecalhoExtra != null) {
            sb.append(cabecalhoExtra).append("\r\n");
        }
        sb.append("\r\n");
        out.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    // ------------------------------------------------------------ uma conexão

    private final class Conexao implements Runnable {
        private final Socket socket;
        private final Object escrita = new Object();
        private InputStream entradaBruta;
        private DataInputStream entrada;
        private OutputStream saida;
        private volatile boolean fechada;

        Conexao(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(tempoLimiteDoHandshakeMs);
                entradaBruta = new BufferedInputStream(socket.getInputStream());
                entrada = new DataInputStream(entradaBruta);
                saida = socket.getOutputStream();
                if (!handshake()) {
                    return;
                }
                socket.setSoTimeout(0);
                lacoDeQuadros();
            } catch (IOException e) {
                // conexão caiu ou foi encerrada
            } catch (RuntimeException e) {
                // defesa final: uma conexão com problema nunca pode derrubar o servidor
            } finally {
                fecharAgora();
                conexoes.remove(this);
            }
        }

        void fecharAgora() {
            fechada = true;
            try {
                socket.close();
            } catch (IOException e) {
                // já fechado
            }
        }

        // -------------------------------------------------------- handshake HTTP

        private boolean handshake() throws IOException {
            String requisicao = lerCabecalho();
            if (requisicao == null) {
                responderHttp(saida, "400 Bad Request", "Requisição inválida ou grande demais.", null);
                return false;
            }
            String[] linhas = requisicao.split("\r\n");
            String[] primeira = linhas[0].split(" ");
            if (primeira.length != 3 || !"GET".equals(primeira[0]) || !primeira[2].startsWith("HTTP/1.")) {
                responderHttp(saida, "400 Bad Request", "Use GET com HTTP/1.1.", null);
                return false;
            }

            Map<String, String> cab = new HashMap<String, String>();
            for (int i = 1; i < linhas.length; i++) {
                int dois = linhas[i].indexOf(':');
                if (dois <= 0) {
                    continue;
                }
                String nome = linhas[i].substring(0, dois).trim().toLowerCase(Locale.ROOT);
                String valor = linhas[i].substring(dois + 1).trim();
                cab.put(nome, cab.containsKey(nome) ? cab.get(nome) + "," + valor : valor);
            }

            if (!"websocket".equalsIgnoreCase(cab.get("upgrade"))) {
                responderHttp(saida, "426 Upgrade Required", "Este servidor só aceita WebSocket.",
                    "Upgrade: websocket");
                return false;
            }
            if (!contemToken(cab.get("connection"), "upgrade")) {
                responderHttp(saida, "400 Bad Request", "Cabeçalho Connection deve conter Upgrade.", null);
                return false;
            }
            if (!"13".equals(cab.get("sec-websocket-version"))) {
                responderHttp(saida, "426 Upgrade Required", "Versão de WebSocket não suportada.",
                    "Sec-WebSocket-Version: 13");
                return false;
            }
            String chave = cab.get("sec-websocket-key");
            if (!chaveValida(chave)) {
                responderHttp(saida, "400 Bad Request", "Sec-WebSocket-Key inválida.", null);
                return false;
            }
            if (!origemPermitida(cab.get("origin"))) {
                responderHttp(saida, "403 Forbidden", "Origem não permitida.", null);
                return false;
            }

            String resposta = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + calcularAccept(chave) + "\r\n\r\n";
            saida.write(resposta.getBytes(StandardCharsets.ISO_8859_1));
            saida.flush();
            return true;
        }

        private String lerCabecalho() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int estado = 0; // quantos bytes de "\r\n\r\n" já vimos em sequência
            while (bytes.size() < TAMANHO_MAXIMO_CABECALHO) {
                int b = entradaBruta.read();
                if (b < 0) {
                    return null;
                }
                bytes.write(b);
                if ((estado == 0 || estado == 2) && b == '\r') {
                    estado++;
                } else if ((estado == 1 || estado == 3) && b == '\n') {
                    estado++;
                } else {
                    estado = b == '\r' ? 1 : 0;
                }
                if (estado == 4) {
                    return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
                }
            }
            return null;
        }

        private boolean contemToken(String valor, String token) {
            if (valor == null) {
                return false;
            }
            for (String parte : valor.split(",")) {
                if (parte.trim().equalsIgnoreCase(token)) {
                    return true;
                }
            }
            return false;
        }

        private boolean chaveValida(String chave) {
            if (chave == null) {
                return false;
            }
            try {
                return Base64.getDecoder().decode(chave).length == 16;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        // -------------------------------------------------------- quadros

        private void lacoDeQuadros() throws IOException {
            ByteArrayOutputStream fragmentos = null;
            while (!fechada) {
                int b0 = entradaBruta.read();
                if (b0 < 0) {
                    return;
                }
                int b1 = entradaBruta.read();
                if (b1 < 0) {
                    return;
                }
                boolean fim = (b0 & 0x80) != 0;
                int reservados = b0 & 0x70;
                int opcode = b0 & 0x0F;
                boolean mascarado = (b1 & 0x80) != 0;
                long tamanho = b1 & 0x7F;

                if (reservados != 0) {
                    encerrar(1002, "bits reservados");
                    return;
                }
                if (!mascarado) {
                    encerrar(1002, "o cliente deve mascarar os quadros");
                    return;
                }
                if (tamanho == 126) {
                    tamanho = entrada.readUnsignedShort();
                } else if (tamanho == 127) {
                    tamanho = entrada.readLong();
                    if (tamanho < 0) {
                        encerrar(1002, "tamanho inválido");
                        return;
                    }
                }
                boolean controle = (opcode & 0x08) != 0;
                if (controle && (!fim || tamanho > 125)) {
                    encerrar(1002, "quadro de controle inválido");
                    return;
                }
                if (tamanho > tamanhoMaximoDaMensagem) {
                    encerrar(1009, "mensagem grande demais");
                    return;
                }

                byte[] mascara = new byte[4];
                entrada.readFully(mascara);
                byte[] carga = new byte[(int) tamanho];
                entrada.readFully(carga);
                for (int i = 0; i < carga.length; i++) {
                    carga[i] = (byte) (carga[i] ^ mascara[i & 3]);
                }

                switch (opcode) {
                    case 0x0: // continuação
                        if (fragmentos == null) {
                            encerrar(1002, "continuação sem início");
                            return;
                        }
                        if (fragmentos.size() + carga.length > tamanhoMaximoDaMensagem) {
                            encerrar(1009, "mensagem grande demais");
                            return;
                        }
                        fragmentos.write(carga);
                        if (fim) {
                            byte[] completa = fragmentos.toByteArray();
                            fragmentos = null;
                            if (!entregar(completa)) {
                                return;
                            }
                        }
                        break;
                    case 0x1: // texto
                        if (fragmentos != null) {
                            encerrar(1002, "nova mensagem antes de terminar a anterior");
                            return;
                        }
                        if (fim) {
                            if (!entregar(carga)) {
                                return;
                            }
                        } else {
                            fragmentos = new ByteArrayOutputStream();
                            fragmentos.write(carga);
                        }
                        break;
                    case 0x2: // binário
                        encerrar(1003, "apenas mensagens de texto");
                        return;
                    case 0x8: // fechar
                        responderFechamento(carga);
                        return;
                    case 0x9: // ping
                        enviar(0xA, carga);
                        break;
                    case 0xA: // pong
                        break;
                    default:
                        encerrar(1002, "opcode desconhecido");
                        return;
                }
            }
        }

        /** @return false se a conexão foi encerrada por erro na mensagem */
        private boolean entregar(byte[] bytes) throws IOException {
            String texto;
            try {
                texto = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                encerrar(1007, "texto não é UTF-8 válido");
                return false;
            }
            String resposta;
            try {
                resposta = tratador.tratar(texto);
            } catch (RuntimeException e) {
                resposta = RESPOSTA_ERRO_INTERNO;
            }
            if (resposta != null) {
                enviar(0x1, resposta.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        }

        private void responderFechamento(byte[] carga) throws IOException {
            int codigo = 1000;
            if (carga.length == 1) {
                codigo = 1002;
            } else if (carga.length >= 2) {
                int recebido = ((carga[0] & 0xFF) << 8) | (carga[1] & 0xFF);
                codigo = codigoDeFechamentoValido(recebido) ? recebido : 1002;
            }
            try {
                enviar(0x8, new byte[] {(byte) (codigo >> 8), (byte) codigo});
            } finally {
                fecharAgora();
            }
        }

        private boolean codigoDeFechamentoValido(int c) {
            return (c >= 1000 && c <= 1003) || (c >= 1007 && c <= 1011) || (c >= 3000 && c <= 4999);
        }

        // -------------------------------------------------------- envio

        void enviar(int opcode, byte[] carga) throws IOException {
            synchronized (escrita) {
                if (fechada) {
                    throw new SocketException("conexão fechada");
                }
                saida.write(0x80 | opcode);
                int n = carga.length;
                if (n < 126) {
                    saida.write(n);
                } else if (n <= 0xFFFF) {
                    saida.write(126);
                    saida.write(n >> 8);
                    saida.write(n);
                } else {
                    saida.write(127);
                    saida.write(0);
                    saida.write(0);
                    saida.write(0);
                    saida.write(0);
                    saida.write(n >> 24);
                    saida.write(n >> 16);
                    saida.write(n >> 8);
                    saida.write(n);
                }
                saida.write(carga);
                saida.flush();
            }
        }

        /** Fecha a conexão por iniciativa do servidor, avisando o motivo com o código do protocolo. */
        private void encerrar(int codigo, String motivo) {
            try {
                byte[] texto = motivo.getBytes(StandardCharsets.UTF_8);
                int tamanhoTexto = Math.min(texto.length, 120);
                byte[] carga = new byte[2 + tamanhoTexto];
                carga[0] = (byte) (codigo >> 8);
                carga[1] = (byte) codigo;
                System.arraycopy(texto, 0, carga, 2, tamanhoTexto);
                enviar(0x8, carga);
                socket.shutdownOutput();
                // Lê e descarta o que o cliente ainda enviar, para o aviso de fechamento chegar inteiro.
                socket.setSoTimeout(1000);
                byte[] lixo = new byte[1024];
                long total = 0;
                while (total < 65536) {
                    int lidos = entradaBruta.read(lixo);
                    if (lidos < 0) {
                        break;
                    }
                    total += lidos;
                }
            } catch (IOException e) {
                // o cliente já foi embora
            } finally {
                fecharAgora();
            }
        }
    }
}
