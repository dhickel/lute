package io.mindspice.lute.engine.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.utils.GdxNativesLoader;
import io.mindspice.lute.engine.tiled.ManifestLoader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Manual loaded-lookup benchmark, not a loading benchmark or part of check. */
public final class AssetManagerLookupBenchmark {
    private static final int QUERIES = 16_384;
    private static final com.sun.management.ThreadMXBean ALLOC = allocationBean();
    private static volatile long sink;
    private record Query(Class<?> type, String id, String path, Object expected) { }
    private record Measurement(double nanos, double bytes, long checksum) { }
    private interface Lookup { Object get(Query q); }

    // Test-only injection bypasses loaders, dependency tracking and I/O. The service alone owns disposal.
    private static final class LoadedManager extends AssetManager {
        LoadedManager() { super(path -> new FileHandle(path), false); }
        <T> void inject(Asset<T> asset, T value) { addAsset(asset.path(), asset.type(), value); }
        // AssetManager.dispose() calls clear() before shutting down its executor. These
        // borrowed assets must not be unloaded; the manager and its references are discarded.
        @Override public void clear() { }
    }
    private static final class Registry implements Lookup {
        private final Map<String, String> sounds = new HashMap<>(), music = new HashMap<>(),
            atlases = new HashMap<>(), maps = new HashMap<>();
        private final LoadedManager manager;
        Registry(LoadedManager manager) { this.manager = manager; }
        private Map<String, String> table(Class<?> type) {
            if (type == Sound.class) return sounds;
            if (type == Music.class) return music;
            if (type == TextureAtlas.class) return atlases;
            if (type == TiledMap.class) return maps;
            throw new IllegalArgumentException("Unsupported type: " + type);
        }
        void add(Asset<?> asset) { table(asset.type()).put(asset.id(), asset.path()); }
        public Object get(Query q) {
            String path = table(q.type()).get(q.id());
            if (path == null) throw new IllegalStateException("Missing logical ID: " + q.id());
            return manager.get(path, q.type());
        }
    }
    private record ServiceLookup(AssetService service) implements Lookup {
        public Object get(Query q) { return service.get(q.type(), q.id()); }
    }
    private record DirectLookup(LoadedManager manager) implements Lookup {
        public Object get(Query q) { return manager.get(q.path(), q.type()); }
    }
    private static final String[] NAMES = {"service", "registry-manager", "manager-direct"};

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
        if (forks < 2 || warmups < 1 || samples < 3 || repetitions < 1)
            throw new IllegalArgumentException("Use [forks>=2] [warmups>=1] [samples>=3] [repetitions>=1] [output dir]");
        Path out = args.length > 4 ? Path.of(args[4]).toAbsolutePath() : Files.createTempDirectory("lute-manager-lookups-");
        Files.createDirectories(out);
        System.out.println("Results: " + out);
        for (int fork = 0; fork < forks; fork++) {
            var command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms512m", "-Xmx512m", "-cp", System.getProperty("java.class.path"),
                AssetManagerLookupBenchmark.class.getName(), "--fork", "" + fork, "" + warmups,
                "" + samples, "" + repetitions, out.toString());
            Files.writeString(out.resolve("fork-" + fork + "-command.txt"), String.join(" ", command) + "\n");
            int code = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(out.resolve("fork-" + fork + ".log").toFile()).start().waitFor();
            if (code != 0) throw new IllegalStateException("Fork " + fork + " failed: " + out);
            System.out.println("Fork " + fork + " passed");
        }
        summarize(out, forks);
    }

    private static void runFork(int fork, int warmups, int samples, int repetitions, Path out) throws Exception {
        System.out.println("java=" + System.getProperty("java.runtime.version") + " vm=" + System.getProperty("java.vm.name")
            + " os=" + System.getProperty("os.name") + "/" + System.getProperty("os.arch")
            + " jvmArgs=" + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println("queries=" + QUERIES + " repetitions=" + repetitions + " warmups=" + warmups
            + " samples=" + samples + " seed=0x51A7 allocation=" + (ALLOC != null));
        System.out.println("Registry: four typed HashMaps ID->path, direct class selection; validates supported type and missing ID, not blank ID or disposed state. Manager direct knows path, no registry; manager validates loaded path/type. Service validates open/type/ID/loaded.");
        GdxNativesLoader.load();
        FileHandle fixture = new FileHandle(Files.createTempDirectory("lute-manager-fixture-").toFile());
        var rows = new ArrayList<String>();
        rows.add("fork,phase,dataset,working,strings,implementation,sample,operations,ns_per_op,bytes_per_op,checksum");
        try {
            new FileHandle("assets/objects/player/idle_up_02.png").copyTo(fixture.child("tile.png"));
            String atlas = "tile.png\nsize: 32, 32\nformat: RGBA8888\nfilter: Nearest, Nearest\ntile\n  bounds: 0, 0, 8, 8\n";
            String map = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><map orientation=\"orthogonal\" width=\"1\" height=\"1\" tilewidth=\"8\" tileheight=\"8\"><tileset firstgid=\"1\" name=\"tile\" tilewidth=\"8\" tileheight=\"8\" tilecount=\"1\" columns=\"0\"><tile id=\"0\"><image width=\"32\" height=\"32\" source=\"tile.png\"/></tile></tileset></map>";
            for (int size : new int[]{4, 256, 4096}) {
                var backend = new AssetServiceTest.Backend();
                backend.install();
                var service = new AssetService(path -> new java.io.File(path).isAbsolute() ? new FileHandle(path) : backend.resolver().resolve(path));
                var manager = new LoadedManager();
                var registry = new Registry(manager);
                var base = new ArrayList<Query>();
                try {
                    List<Asset<?>> bundle = size == 4 ? ManifestLoader.parseMapManifest("test_tiled").all() : List.of();
                    if (size == 4 && bundle.size() != 4) throw new AssertionError("Expected four-entry test_tiled bundle");
                    for (int i = 0; i < size; i++) {
                        if (size == 4) {
                            load(service, manager, registry, bundle.get(i), base);
                            continue;
                        }
                        String id = "asset-" + i / 4;
                        String path = fixture.child("asset-" + i + (i % 4 == 2 ? ".atlas" : i % 4 == 3 ? ".tmx" : ".audio")).path();
                        if (i % 4 == 2) new FileHandle(path).writeString(atlas, false);
                        if (i % 4 == 3) new FileHandle(path).writeString(map, false);
                        Asset<?> asset = switch (i % 4) {
                            case 0 -> new Asset.Sound(id, path);
                            case 1 -> new Asset.Music(id, path);
                            case 2 -> new Asset.Atlas(id, path);
                            default -> new Asset.Map(id, path);
                        };
                        load(service, manager, registry, asset, base);
                    }
                    Lookup[] lookups = {new ServiceLookup(service), registry, new DirectLookup(manager)};
                    correctness(lookups, base);
                    String dataset = size == 4 ? "bundle-4" : "balanced-" + size;
                    for (boolean hot : new boolean[]{true, false}) for (boolean distinct : new boolean[]{false, true}) {
                        Query[] queries = queries(base, hot, distinct);
                        long expected = 0;
                        for (Query q : queries) expected += System.identityHashCode(q.expected());
                        expected *= repetitions;
                        for (int round = -warmups; round < samples; round++) {
                            int first = Math.floorMod(fork + round + warmups, 3);
                            for (int order = 0; order < 3; order++) {
                                int index = (first + order) % 3;
                                Measurement m = measure(lookups[index], queries, repetitions);
                                if (m.checksum() != expected) throw new AssertionError("Checksum mismatch " + NAMES[index]);
                                rows.add(String.format(Locale.ROOT, "%d,%s,%s,%s,%s,%s,%d,%d,%.3f,%.3f,%d",
                                    fork, round < 0 ? "warmup" : "get", dataset, hot ? "hot" : "wide",
                                    distinct ? "equal-distinct" : "reference", NAMES[index], round,
                                    QUERIES * repetitions, m.nanos(), m.bytes(), m.checksum()));
                            }
                        }
                    }
                } finally {
                    // Shut down the manager's executor without disposing borrowed assets.
                    try { manager.dispose(); } finally { service.dispose(); }
                    backend.checkAllDisposed();
                }
            }
        } finally { fixture.deleteDirectory(); }
        Files.write(out.resolve("fork-" + fork + ".csv"), rows);
        System.out.println("correctness and checksums passed; sink=" + sink);
    }
    private static <T> void load(AssetService service, LoadedManager manager, Registry registry, Asset<T> asset, List<Query> base) {
        T value = service.load(asset);
        manager.inject(asset, value);
        registry.add(asset);
        base.add(new Query(asset.type(), asset.id(), asset.path(), value));
    }
    private static void correctness(Lookup[] lookups, List<Query> base) {
        for (Query q : base) for (Lookup lookup : lookups)
            if (lookup.get(q) != q.expected()) throw new AssertionError("Identity mismatch: " + q);
        Query q = base.getFirst();
        expectFailure(() -> lookups[0].get(new Query(q.type(), "missing", q.path(), null)));
        expectFailure(() -> lookups[1].get(new Query(q.type(), "missing", q.path(), null)));
        expectFailure(() -> lookups[2].get(new Query(q.type(), q.id(), "missing", null)));
        Query other = base.get(1);
        if (base.size() > 4 && (!q.id().equals(other.id()) || q.expected() == other.expected()))
            throw new AssertionError("Type collision fixture");
        expectFailure(() -> lookups[2].get(new Query(other.type(), other.id(), q.path(), null)));
        expectFailure(() -> lookups[0].get(new Query(String.class, q.id(), q.path(), null)));
        expectFailure(() -> lookups[1].get(new Query(String.class, q.id(), q.path(), null)));
    }
    private static void expectFailure(Runnable call) {
        try { call.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected lookup failure");
    }
    private static Query[] queries(List<Query> base, boolean hot, boolean distinct) {
        var list = new ArrayList<Query>();
        int working = hot ? Math.min(16, base.size()) : base.size();
        for (int i = 0; i < QUERIES; i++) {
            Query q = base.get(i % working);
            String id = distinct ? new String(q.id()) : q.id();
            String path = distinct ? new String(q.path()) : q.path();
            id.hashCode(); path.hashCode();
            list.add(new Query(q.type(), id, path, q.expected()));
        }
        Collections.shuffle(list, new Random(0x51A7));
        return list.toArray(Query[]::new);
    }
    private static Measurement measure(Lookup lookup, Query[] queries, int repetitions) {
        long before = allocated(), start = System.nanoTime(), checksum = 0;
        for (int i = 0; i < repetitions; i++) for (Query q : queries)
            checksum += System.identityHashCode(lookup.get(q));
        long elapsed = System.nanoTime() - start;
        long bytes = before < 0 ? -1 : allocated() - before;
        sink = checksum;
        int count = queries.length * repetitions;
        return new Measurement((double) elapsed / count, bytes < 0 ? -1 : (double) bytes / count, checksum);
    }
    private static com.sun.management.ThreadMXBean allocationBean() {
        var bean = ManagementFactory.getThreadMXBean();
        if (bean instanceof com.sun.management.ThreadMXBean b && b.isThreadAllocatedMemorySupported()) {
            if (!b.isThreadAllocatedMemoryEnabled()) b.setThreadAllocatedMemoryEnabled(true);
            return b;
        }
        return null;
    }
    private static long allocated() { return ALLOC == null ? -1 : ALLOC.getThreadAllocatedBytes(Thread.currentThread().threadId()); }
    private static double median(double[] values) {
        Arrays.sort(values);
        return values.length % 2 == 1 ? values[values.length / 2] : (values[values.length / 2 - 1] + values[values.length / 2]) / 2;
    }
    private static void summarize(Path out, int forks) throws Exception {
        var groups = new TreeMap<String, List<double[]>>();
        for (int fork = 0; fork < forks; fork++) {
            List<String> rows = Files.readAllLines(out.resolve("fork-" + fork + ".csv"));
            for (String row : rows.subList(1, rows.size())) {
                String[] c = row.split(",");
                if (!c[1].equals("get")) continue;
                String key = String.join(",", Arrays.copyOfRange(c, 0, 6));
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new double[]{Double.parseDouble(c[8]), Double.parseDouble(c[9])});
            }
        }
        var summary = new ArrayList<String>();
        summary.add("fork,phase,dataset,working,strings,implementation,median_ns,min_ns,max_ns,median_bytes");
        groups.forEach((key, values) -> {
            double[] times = values.stream().mapToDouble(v -> v[0]).toArray();
            double[] bytes = values.stream().mapToDouble(v -> v[1]).toArray();
            double med = median(times), alloc = median(bytes);
            summary.add(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.3f,%.3f", key, med, times[0], times[times.length - 1], alloc));
        });
        Files.write(out.resolve("summary.csv"), summary);
        System.out.println("Per-fork medians and sample ranges: " + out.resolve("summary.csv"));
    }
}
