package heroscript.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Executa programas do playground no servidor: {@code POST /api/run|java|tokens|diagnostics} com o código no corpo.
 *
 * <p>Cada requisição roda numa JVM filha ({@link Worker}) usada uma única vez, nunca neste processo: o programa recebido contém Java
 * arbitrário, e só um processo separado pode ser morto ao estourar o tempo. Este servidor impõe tempo, tamanho da
 * entrada, tamanho da saída, memória da JVM filha e número de execuções simultâneas. Ele <b>não</b> isola o
 * programa do sistema: isso é papel do container (sem rede, sem root, disco somente leitura — ver deploy/).
 */
public final class RunnerServer {

    private static final Set<String> KINDS = Set.of("run", "java", "tokens", "diagnostics");

    private final HttpServer http;
    private final Semaphore slots;
    private final int timeoutSeconds;
    private final int maxSourceBytes;
    private final int maxOutputBytes;
    private final int queueSeconds;
    private final String childMemory;
    private final ExecutorService executor;
    private final ExecutorService background;
    /** JVMs filhas já aquecidas; no máximo uma por vaga de execução. */
    private final BlockingQueue<Prepared> pool;
    private volatile boolean stopped;
    /** Classpath deste processo em caminhos absolutos: a JVM filha roda em outra pasta. */
    private final String classPath = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(entry -> !entry.isEmpty()).map(entry -> Path.of(entry).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));

    /** Limites lidos do ambiente; os padrões servem para o container descrito em deploy/docker-compose.yml. */
    public RunnerServer(int port) throws IOException {
        this(System.getenv().getOrDefault("RUNNER_BIND", "127.0.0.1"), port, env("RUNNER_CONCURRENCY", 2), env("RUNNER_TIMEOUT_SECONDS", 10), env("RUNNER_MAX_SOURCE_BYTES", 64 * 1024),
                env("RUNNER_MAX_OUTPUT_BYTES", 256 * 1024), env("RUNNER_QUEUE_SECONDS", 3),
                System.getenv().getOrDefault("RUNNER_CHILD_MEMORY", "128m"));
    }

    /**
     * @param bind endereço de escuta. O padrão é só a própria máquina: este serviço executa código arbitrário e
     *             nunca deve ficar visível na rede. No container (rede interna, sem portas publicadas) usa-se 0.0.0.0.
     */
    public RunnerServer(String bind, int port, int concurrency, int timeoutSeconds, int maxSourceBytes, int maxOutputBytes,
            int queueSeconds, String childMemory) throws IOException {
        this.slots = new Semaphore(concurrency);
        this.pool = new ArrayBlockingQueue<>(concurrency);
        this.background = Executors.newFixedThreadPool(concurrency, task -> {
            Thread thread = new Thread(task, "runner-prepare");
            thread.setDaemon(true);
            return thread;
        });
        this.timeoutSeconds = timeoutSeconds;
        this.maxSourceBytes = maxSourceBytes;
        this.maxOutputBytes = maxOutputBytes;
        this.queueSeconds = queueSeconds;
        this.childMemory = childMemory;
        this.http = HttpServer.create(new InetSocketAddress(bind, port), 64);
        http.createContext("/api/", this::handle);
        // Mais threads que vagas: as excedentes esperam a vaga por pouco tempo e respondem 503.
        this.executor = Executors.newFixedThreadPool(concurrency + 6);
        http.setExecutor(executor);
    }

    public static void main(String[] args) throws IOException {
        RunnerServer server = new RunnerServer(env("RUNNER_PORT", 8080));
        server.start();
        System.out.println("HeroScript runner ouvindo em " + server.http.getAddress());
    }

    public void start() {
        for (int i = pool.remainingCapacity(); i > 0; i--) replenish();
        http.start();
    }
    public void stop() {
        stopped = true;
        http.stop(0);
        executor.shutdownNow();
        background.shutdownNow();
        for (Prepared worker; (worker = pool.poll()) != null; ) discard(worker);
    }
    public int port() { return http.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String kind = exchange.getRequestURI().getPath().substring("/api/".length());
            if (kind.equals("health")) {
                reply(exchange, 200, "ok");
                return;
            }
            if (!KINDS.contains(kind)) {
                reply(exchange, 404, "operação desconhecida");
                return;
            }
            if (!exchange.getRequestMethod().equals("POST")) {
                reply(exchange, 405, "use POST");
                return;
            }
            byte[] source = exchange.getRequestBody().readNBytes(maxSourceBytes + 1);
            if (source.length > maxSourceBytes) {
                reply(exchange, 413, "programa maior que " + maxSourceBytes / 1024 + " KB");
                return;
            }
            if (!slots.tryAcquire(queueSeconds, TimeUnit.SECONDS)) {
                reply(exchange, 503, "servidor ocupado");
                return;
            }
            String result;
            try {
                result = execute(kind, source);
            } finally {
                slots.release();
            }
            reply(exchange, 200, result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reply(exchange, 503, "servidor encerrando");
        } catch (IOException | RuntimeException e) {
            System.err.println("[runner] " + e);
            reply(exchange, 500, "erro interno do servidor");
        } finally {
            exchange.close();
        }
    }

    /** Uma JVM filha já iniciada, à espera do programa na entrada padrão, com sua pasta temporária. */
    private record Prepared(Process process, Path dir) {}

    /**
     * Inicia um {@link Worker}. Com {@code warm}, ele compila um programa mínimo antes de ler a entrada: quando a
     * requisição chega, a JVM e o javac já estão carregados e a resposta sai em uma fração do tempo.
     */
    private Prepared spawn(boolean warm) throws IOException {
        Path dir = Files.createTempDirectory("run");
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx" + childMemory, "-Xss512k", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-Xshare:auto",
                "-XX:MaxMetaspaceSize=96m", "-XX:-UsePerfData",
                "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                "-Djava.io.tmpdir=" + dir,
                "-cp", classPath,
                Worker.class.getName(), warm ? "warm" : "cold"));
        try {
            return new Prepared(new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start(), dir);
        } catch (IOException e) {
            deleteQuietly(dir);
            throw e;
        }
    }

    /** Repõe, em segundo plano, a JVM aquecida que acabou de ser usada. Cada JVM atende um único programa. */
    private void replenish() {
        if (stopped) return;
        background.execute(() -> {
            try {
                Prepared worker = spawn(true);
                if (stopped || !pool.offer(worker)) discard(worker);
            } catch (IOException | RuntimeException e) {
                System.err.println("[runner] não foi possível preparar uma JVM: " + e);
            }
        });
    }

    private static void discard(Prepared worker) {
        try {
            kill(worker.process());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        deleteQuietly(worker.dir());
    }

    /** Entrega o programa a uma JVM filha (aquecida, se houver) e devolve o que ela imprimiu. */
    private String execute(String kind, byte[] source) throws IOException, InterruptedException {
        Prepared worker = pool.poll();
        if (worker != null && !worker.process().isAlive()) {
            discard(worker);
            worker = null;
        }
        if (worker == null) worker = spawn(false);
        replenish();
        Process process = worker.process();
        try {
            Capture capture = new Capture(process.getInputStream(), maxOutputBytes);
            capture.start();
            // Em outra thread: se a JVM filha ainda não estiver lendo, a escrita não pode segurar o limite de tempo.
            Thread writer = new Thread(() -> {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write((kind + "\n").getBytes(StandardCharsets.UTF_8));
                    stdin.write(source);
                } catch (IOException ignored) {
                    // processo morto antes de ler: o código de saída conta o resto
                }
            });
            writer.setDaemon(true);
            writer.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) kill(process);
            capture.join(2000);
            String text = capture.text();
            if (capture.truncated()) text += "\n… saída cortada em " + maxOutputBytes / 1024 + " KB\n";
            if (!finished) {
                if (!text.isEmpty() && !text.endsWith("\n")) text += "\n";
                text += "Erro: o programa passou do limite de " + timeoutSeconds + " s e foi interrompido.\n";
            }
            if (!kind.equals("run")) return text.stripTrailing();
            if (!text.isEmpty() && !text.endsWith("\n")) text += "\n";
            return text + "[exit " + (finished ? process.exitValue() : 124) + "]";
        } finally {
            discard(worker);
        }
    }

    /** Mata a JVM filha e tudo o que ela tiver iniciado. */
    private static void kill(Process process) throws InterruptedException {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.walk(dir)) {
            for (Path f : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
        } catch (IOException ignored) {
            // pasta temporária: o tmpfs do container é descartado de qualquer forma
        }
    }

    private static int env(String name, int fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
    }

    /** Lê a saída da JVM filha até o limite; o que passar é descartado, para o processo nunca travar no pipe. */
    private static final class Capture extends Thread {
        private final InputStream in;
        private final int limit;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private boolean truncated;

        Capture(InputStream in, int limit) {
            this.in = in;
            this.limit = limit;
            setDaemon(true);
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                for (int n; (n = in.read(chunk)) > 0; ) {
                    synchronized (this) {
                        int room = limit - buffer.size();
                        if (n > room) truncated = true;
                        if (room > 0) buffer.write(chunk, 0, Math.min(n, room));
                    }
                }
            } catch (IOException ignored) {
                // processo morto: fica o que já foi lido
            }
        }

        synchronized String text() { return buffer.toString(StandardCharsets.UTF_8); }
        synchronized boolean truncated() { return truncated; }
    }
}
