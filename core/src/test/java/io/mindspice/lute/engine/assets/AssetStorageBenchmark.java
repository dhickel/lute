package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.tiled.ManifestLoader;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Manual microbenchmark, deliberately not part of check. No benchmark dependencies. */
public final class AssetStorageBenchmark {
    private static volatile long sink;
    private static final int QUERY_COUNT = 16_384;
    private static final com.sun.management.ThreadMXBean ALLOCATION = allocationBean();
    private record Query(Class<?> type, String id) { }
    private record Measurement(double nanos, double bytes, long checksum) { }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        if (args.length > 0 && args[0].equals("--fork")) {
            runFork(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                Integer.parseInt(args[4]), Path.of(args[5]));
            return;
        }
        int forks = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int warmups = args.length > 1 ? Integer.parseInt(args[1]) : 4;
        int samples = args.length > 2 ? Integer.parseInt(args[2]) : 7;
        int repetitions = args.length > 3 ? Integer.parseInt(args[3]) : 64;
        if (forks < 2 || warmups < 1 || samples < 3 || repetitions < 1) {
            throw new IllegalArgumentException("Use: [forks>=2] [warmups>=1] [samples>=3] [query repetitions>=1] [output directory]");
        }
        Path output = args.length > 4 ? Path.of(args[4]) : Files.createTempDirectory("lute-asset-benchmark-");
        Files.createDirectories(output);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        System.out.println("Results: " + output.toAbsolutePath());
        for (int fork = 0; fork < forks; fork++) {
            var command = List.of(java, "-Xms512m", "-Xmx512m", "-cp", System.getProperty("java.class.path"),
                AssetStorageBenchmark.class.getName(), "--fork", "" + fork, "" + warmups, "" + samples,
                "" + repetitions, output.toAbsolutePath().toString());
            Files.writeString(output.resolve("fork-" + fork + "-command.txt"), String.join(" ", command) + "\n");
            int exit = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.resolve("fork-" + fork + ".log").toFile()).start().waitFor();
            if (exit != 0) { throw new IllegalStateException("Fork " + fork + " failed; see " + output); }
            System.out.println("Fork " + fork + " passed");
        }
        summarize(output, forks);
    }

    private static void runFork(int fork, int warmups, int samples, int repetitions, Path output) throws Exception {
        System.out.println("java=" + System.getProperty("java.runtime.version") + " vm=" + System.getProperty("java.vm.name"));
        System.out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.arch")
            + " processors=" + Runtime.getRuntime().availableProcessors());
        System.out.println("jvmArgs=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("warmups=" + warmups + " samples=" + samples + " repetitions=" + repetitions
            + " queryCount=" + QUERY_COUNT + " seed=0x51A7 allocation=" + (ALLOCATION != null));
        GdxNativesLoader.load();
        var rows = new ArrayList<String>();
        rows.add("fork,phase,dataset,working,ids,implementation,sample,operations,ns_per_op,bytes_per_op,checksum");
        FileHandle fixture = new FileHandle(Files.createTempDirectory("lute-bench-fixture-").toFile());
        try {
            new FileHandle("raw_assets/maps/test_tiled/objects/player/idle_up_02.png").copyTo(fixture.child("tile.png"));
            fixture.child("tile.atlas").writeString("""
                tile.png
                size: 32, 32
                format: RGBA8888
                filter: Nearest, Nearest
                tile
                  bounds: 0, 0, 32, 32
                """, false);
            fixture.child("tile.tmx").writeString("""
                <?xml version="1.0" encoding="UTF-8"?>
                <map orientation="orthogonal" width="1" height="1" tilewidth="8" tileheight="8">
                  <tileset firstgid="1" name="tile" tilewidth="8" tileheight="8" tilecount="1" columns="0">
                    <tile id="0"><image width="32" height="32" source="tile.png"/></tile>
                  </tileset>
                </map>
                """, false);
            for (int size : new int[]{0, 256, 4096}) {
                var backend = new AssetServiceTest.Backend();
                backend.install();
                List<Asset<?>> assets = size == 0 ? ManifestLoader.parseMapManifest("test_tiled").all() : assets(size, fixture);
                String dataset = size == 0 ? "bundle-" + assets.size() : "balanced-" + size;
                System.out.println(dataset + " types=" + assets.stream().collect(java.util.stream.Collectors.groupingBy(
                    asset -> asset.type().getSimpleName(), java.util.stream.Collectors.counting())));
                var stores = new AssetStorage[]{AssetStorage.create(false, resolver(backend, true)), AssetStorage.create(true, resolver(backend, false))};
                try {
                    for (var store : stores) { assets.forEach(store::load); }
                    // Same mixed query ordering and ID representation for both implementations.
                    for (boolean hot : new boolean[]{true, false}) {
                        for (boolean distinct : new boolean[]{false, true}) {
                            Query[] queries = queries(assets, hot, distinct);
                            long[] expected = new long[2];
                            for (int i = 0; i < stores.length; i++) {
                                for (var query : queries) {
                                    expected[i] += System.identityHashCode(stores[i].get(query.type(), query.id()));
                                }
                                expected[i] *= repetitions;
                            }
                            for (int round = -warmups; round < samples; round++) {
                                int first = (fork + round + warmups) & 1;
                                for (int order = 0; order < 2; order++) {
                                    int i = first ^ order;
                                    Measurement result = lookup(stores[i], queries, repetitions);
                                    if (result.checksum() != expected[i]) { throw new AssertionError("Lookup checksum mismatch"); }
                                    row(rows, fork, round < 0 ? "warmup-get" : "get", dataset, hot ? "hot" : "wide",
                                        distinct ? "equal-distinct" : "reference", i, round, QUERY_COUNT * repetitions, result);
                                }
                            }
                        }
                    }
                } finally {
                    for (var store : stores) { store.dispose(); }
                    backend.checkAllDisposed();
                }
            }
            // Queue timing includes direct audio dispatch into the same simulated backend as lifecycle tests.
            // Creation, prefix loading, queueing, validation, and disposal are outside the timed interval.
            int queueWarmups = Math.max(96, warmups);
            System.out.println("queueWarmups=" + queueWarmups + " batches of 128 updates per prefix/implementation");
            for (int prefix : new int[]{0, 256, 4096}) {
                for (int round = -queueWarmups; round < samples; round++) {
                    int first = (fork + round + queueWarmups) & 1;
                    for (int order = 0; order < 2; order++) {
                        int i = first ^ order;
                        Measurement result = queue(i == 1, prefix);
                        row(rows, fork, round < 0 ? "warmup-queue" : "queue", "prefix-" + prefix,
                            "pending-128", "reference", i, round, 128, result);
                    }
                }
            }
        } finally {
            fixture.deleteDirectory();
        }
        Files.write(output.resolve("fork-" + fork + ".csv"), rows);
        System.out.println("checksums passed; sink=" + sink);
    }

    private static com.badlogic.gdx.assets.loaders.FileHandleResolver resolver(AssetServiceTest.Backend backend, boolean sourceMaps) {
        // The original composite benchmark retains direct source-texture map loading.
        return path -> sourceMaps && path.startsWith("maps/test_tiled/")
            ? new FileHandle("raw_assets/" + path.substring(5))
            : new java.io.File(path).isAbsolute() ? new FileHandle(path) : backend.resolver().resolve(path);
    }

    private static List<Asset<?>> assets(int size, FileHandle fixture) {
        var assets = new ArrayList<Asset<?>>();
        for (int i = 0; i < size; i++) {
            // Reuse logical IDs across all four types, just as the contract allows.
            String id = "asset-" + i / 4;
            assets.add(switch (i % 4) {
                case 0 -> new Asset.Sound(id, "audio/bench.wav");
                case 1 -> new Asset.Music(id, "audio/bench.ogg");
                case 2 -> new Asset.Atlas(id, fixture.child("tile.atlas").path());
                default -> new Asset.Map(id, fixture.child("tile.tmx").path());
            });
        }
        return assets;
    }

    private static Query[] queries(List<Asset<?>> assets, boolean hot, boolean distinct) {
        int working = hot ? Math.min(16, assets.size()) : assets.size();
        var queries = new ArrayList<Query>(QUERY_COUNT);
        for (int i = 0; i < QUERY_COUNT; i++) {
            Asset<?> asset = assets.get(i % working);
            String id = distinct ? new String(asset.id()) : asset.id();
            id.hashCode(); // Equal prehashed IDs, not string first-hash work in one implementation only.
            queries.add(new Query(asset.type(), id));
        }
        Collections.shuffle(queries, new Random(0x51A7));
        return queries.toArray(Query[]::new);
    }

    private static Measurement lookup(AssetStorage store, Query[] queries, int repetitions) {
        long bytes = allocated();
        long start = System.nanoTime();
        long checksum = 0;
        for (int repeat = 0; repeat < repetitions; repeat++) {
            for (Query query : queries) {
                checksum += System.identityHashCode(store.get(query.type(), query.id()));
            }
        }
        long elapsed = System.nanoTime() - start;
        long allocation = delta(bytes);
        sink = checksum;
        int operations = queries.length * repetitions;
        return new Measurement((double) elapsed / operations, allocation < 0 ? -1 : (double) allocation / operations, checksum);
    }

    private static Measurement queue(boolean perType, int prefix) {
        var backend = new AssetServiceTest.Backend();
        backend.install();
        var store = AssetStorage.create(perType, backend.resolver());
        try {
            for (int i = 0; i < prefix; i++) { store.load(Asset.Sound.of("prefix-" + i, "prefix.wav")); }
            var pending = new ArrayList<Asset<?>>();
            for (int i = 0; i < 128; i++) {
                Asset<?> asset = (i & 1) == 0 ? Asset.Sound.of("pending-" + i, "queue.wav")
                    : Asset.Music.of("pending-" + i, "queue.ogg");
                pending.add(asset);
                store.queue(asset);
            }
            long bytes = allocated();
            long start = System.nanoTime();
            long completions = 0;
            int completionIndex = -1;
            for (int i = 0; i < pending.size(); i++) {
                if (store.update()) { completions++; completionIndex = i; }
            }
            long elapsed = System.nanoTime() - start;
            long allocation = delta(bytes);
            if (completions != 1 || completionIndex != 127) { throw new AssertionError("Queue completion mismatch"); }
            for (var asset : pending) {
                if (!store.isLoaded(asset.type(), asset.id())) { throw new AssertionError("Queue item not loaded"); }
            }
            sink = completions;
            return new Measurement((double) elapsed / pending.size(), allocation < 0 ? -1 : (double) allocation / pending.size(), completions);
        } finally {
            store.dispose();
            backend.checkAllDisposed();
        }
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean supported && supported.isThreadAllocatedMemorySupported()) {
            if (!supported.isThreadAllocatedMemoryEnabled()) { supported.setThreadAllocatedMemoryEnabled(true); }
            return supported;
        }
        return null;
    }

    private static long allocated() { return ALLOCATION == null ? -1 : ALLOCATION.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    private static long delta(long before) { return before < 0 ? -1 : allocated() - before; }

    private static void row(List<String> rows, int fork, String phase, String dataset, String working, String ids,
                            int implementation, int sample, int operations, Measurement result) {
        rows.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%s,%d,%d,%.3f,%.3f,%d", fork, phase, dataset,
            working, ids, implementation == 0 ? "composite" : "per-type", sample, operations,
            result.nanos(), result.bytes(), result.checksum()));
    }

    private static void summarize(Path output, int forks) throws Exception {
        var grouped = new java.util.TreeMap<String, List<double[]>>();
        for (int fork = 0; fork < forks; fork++) {
            var rows = Files.readAllLines(output.resolve("fork-" + fork + ".csv"));
            for (String row : rows.subList(1, rows.size())) {
                String[] columns = row.split(",");
                if (columns[1].startsWith("warmup")) { continue; }
                String key = String.join(",", Arrays.copyOfRange(columns, 0, 6));
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new double[]{
                    Double.parseDouble(columns[8]), Double.parseDouble(columns[9])});
            }
        }
        var summary = new ArrayList<String>();
        summary.add("fork,phase,dataset,working,ids,implementation,median_ns,min_ns,max_ns,median_bytes");
        grouped.forEach((key, values) -> {
            double[] times = values.stream().mapToDouble(value -> value[0]).sorted().toArray();
            double[] bytes = values.stream().mapToDouble(value -> value[1]).sorted().toArray();
            summary.add(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.3f", key, median(times), times[0],
                times[times.length - 1], median(bytes)));
        });
        Files.write(output.resolve("summary.csv"), summary);
        System.out.println("Per-fork medians and sample ranges: " + output.resolve("summary.csv"));
    }

    private static double median(double[] sorted) {
        int middle = sorted.length / 2;
        return (sorted.length & 1) == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }
}
