import heroscript.server.RunnerServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Sobe o RunnerServer numa porta livre e confere execução, limites e respostas de erro. */
public class RunnerServerTests {
    static int checks;
    static final HttpClient client = HttpClient.newHttpClient();
    static String base;

    static void check(boolean condition, String what) {
        if (System.getenv("RUNNER_TEST_TRACE") != null) System.err.println("check: " + what.lines().findFirst().orElse(""));
        if (!condition) throw new AssertionError(what);
        checks++;
    }

    static HttpResponse<String> post(String kind, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + kind))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    static String expected(String name) throws Exception {
        return Files.readString(Path.of("tests/expected/" + name + ".out")).stripTrailing()
                .replace(name + ".hero", "playground.hero");
    }

    public static void main(String[] args) throws Exception {
        // 1 vaga, 3 s de tempo, 4 KB de entrada, 2 KB de saída, sem fila
        RunnerServer server = new RunnerServer("127.0.0.1", 0, 1, 3, 4096, 2048, 0, "128m");
        server.start();
        base = "http://127.0.0.1:" + server.port() + "/api/";
        try {
            var health = client.send(HttpRequest.newBuilder(URI.create(base + "health")).build(),
                    HttpResponse.BodyHandlers.ofString());
            check(health.statusCode() == 200 && health.body().equals("ok"), "health");

            // mesma saída da linha de comando, inclusive erros e nomes de arquivo
            for (String name : new String[] {"hello", "batcave", "secret", "flashtravel"}) {
                var response = post("run", Files.readString(Path.of("examples/" + name + ".hero")));
                check(response.statusCode() == 200, name + " status");
                check(response.body().equals(expected(name)), name + " output:\n" + response.body());
            }
            var syntax = post("run", "battle { announce 1 }");
            check(syntax.body().startsWith("HeroSyntaxError em playground.hero:1:") && syntax.body().endsWith("[exit 1]"),
                    "syntax error: " + syntax.body());
            var javaError = post("run", "battle { int x = \"a\"; }");
            check(javaError.body().startsWith("HeroCompileError em playground.hero:1") && javaError.body().endsWith("[exit 1]"),
                    "java error: " + javaError.body());

            // tempo: o que foi impresso antes do corte é preservado e o processo é morto
            long start = System.nanoTime();
            var loop = post("run", "battle { announce \"antes\"; while (true) { } }");
            long seconds = (System.nanoTime() - start) / 1_000_000_000L;
            check(loop.body().startsWith(">> antes\n"), "output before timeout kept: " + loop.body());
            check(loop.body().contains("limite de 3 s") && loop.body().endsWith("[exit 124]"), "timeout: " + loop.body());
            check(seconds >= 3 && seconds < 8, "timeout took " + seconds + " s");

            // saída: cortada no limite, sem travar o processo no pipe
            var flood = post("run", "battle { for (int i = 0; i < 100000; i++) { announce \"linha \" + i; } }");
            check(flood.body().contains("saída cortada em 2 KB") && flood.body().endsWith("[exit 0]"), "output cap");
            check(flood.body().length() < 2400, "capped size " + flood.body().length());

            // System.exit do Java embutido encerra só a JVM filha
            var exit = post("run", "battle { announce \"a\"; System.exit(7); }");
            check(exit.body().equals(">> a\n[exit 7]"), "System.exit: " + exit.body());
            check(post("run", "battle { announce \"vivo\"; }").body().equals(">> vivo\n[exit 0]"), "server survives");

            // memória: estouro derruba só a JVM filha
            var memory = post("run", "battle { java.util.List<long[]> l = new java.util.ArrayList<>(); while (true) { l.add(new long[1 << 20]); } }");
            check(memory.statusCode() == 200 && memory.body().contains("OutOfMemoryError"), "memory: " + memory.body());

            check(post("run", "x".repeat(5000)).statusCode() == 413, "oversized source");
            check(post("nada", "battle{}").statusCode() == 404, "unknown operation");
            var get = client.send(HttpRequest.newBuilder(URI.create(base + "run")).build(), HttpResponse.BodyHandlers.ofString());
            check(get.statusCode() == 405, "GET rejected");

            // ocupado: com a única vaga em uso, a segunda requisição recebe 503 na hora
            CompletableFuture<HttpResponse<String>> slow = CompletableFuture.supplyAsync(() -> {
                try { return post("run", "battle { try { Thread.sleep(1500); } catch (InterruptedException e) { } announce \"fim\"; }"); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            Thread.sleep(500);
            check(post("run", "battle{}").statusCode() == 503, "busy");
            check(slow.get().body().equals(">> fim\n[exit 0]"), "slow request unaffected: " + slow.get().body());

            // demais operações do playground
            check(post("tokens", "battle { }").body().contains("battle"), "tokens");
            check(post("java", "battle { announce 1; }").body().contains("Arena.announce"), "java");
            check(post("diagnostics", "battle { announce 1; }").body().equals("[]"), "diagnostics clean");
            var issue = post("diagnostics", "battle { announce 1 }").body();
            check(issue.startsWith("[{\"code\":\"HERO_ERROR\"") && issue.endsWith("]"), "diagnostics error: " + issue);
            var warning = post("diagnostics", "battle { deadpoolAction(chance = 50) { announce 1; } }").body();
            check(warning.contains("DEADPOOL_MAY_SKIP"), "diagnostics warning");
        } catch (Throwable failure) {
            failure.printStackTrace();
            server.stop();
            System.exit(1);
        }
        server.stop();
        System.out.println(checks + " runner server checks passed");
        System.exit(0);
    }
}
