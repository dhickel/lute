package io.mindspice.lute.engine.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.utils.Disposable;
import io.mindspice.lute.engine.assets.AssetService;
import io.mindspice.lute.engine.assets.CharacterAssets;
import io.mindspice.lute.engine.components.*;
import io.mindspice.lute.engine.singletons.GameConsts;

import java.util.*;

/** Isolated premultiplied cells; only completed cells participate in world sorting. */
public final class CharacterCompositor implements Disposable {
    private static final Set<String> ACTIONS = Set.of("idle", "walk", "death", "slash", "jab", "shot");
    // base, feet, body, head, hands, weapon. Up-facing weapons sit behind the body.
    private static final int[] FRONT = {0, 1, 2, 3, 4, 5};
    private static final int[] UP = {5, 0, 1, 2, 3, 4};
    private final AssetService assets;
    private final SpriteBatch batch;
    private final List<Target> targets = new ArrayList<>();
    private final Map<Entity, TextureRegion> completed = new IdentityHashMap<>();
    private final Map<LayerKey, Map<String, TextureRegion[]>> layers = new HashMap<>();
    private final TextureAtlas[] identities = new TextureAtlas[6];
    private final Vector3[] corners = {new Vector3(), new Vector3(), new Vector3(), new Vector3()};
    private final Vector2 rotated = new Vector2();
    private static final float[] CLIP_X = {-1f, 1f, 1f, -1f};
    private static final float[] CLIP_Y = {-1f, -1f, 1f, 1f};
    private boolean disposed;

    public CharacterCompositor(AssetService assets) {
        this.assets = assets;
        var state = new RenderState();
        try { batch = new SpriteBatch(); }
        finally { state.restore(); }
    }

    public TextureRegion region(Entity entity) { return completed.get(entity); }

    public void compose(Iterable<Entity> entities, OrthographicCamera camera) {
        if (disposed) { throw new IllegalStateException("Character compositor is disposed"); }
        completed.clear();
        var visible = new ArrayList<Entity>();
        for (Entity entity : entities) {
            if (CompositeCharacter.MAPPER.has(entity) && visible(entity, camera)) {
                visible.add(entity);
            }
        }
        var state = new RenderState();
        try {
            RenderState.prepare();
            if (!visible.isEmpty()) { refreshIdentities(); }
            int pitchX = 2, pitchY = 2;
            for (var entity : visible) {
                var canvas = CompositeCharacter.MAPPER.get(entity);
                pitchX = Math.max(pitchX, canvas.canvasWidth() + 2);
                pitchY = Math.max(pitchY, canvas.canvasHeight() + 2);
            }
            int maxSide = state.integer(GL20.GL_MAX_TEXTURE_SIZE) / Math.max(pitchX, pitchY);
            if (maxSide == 0 && !visible.isEmpty()) {
                throw new IllegalStateException("GL maximum texture size cannot fit a character cell");
            }
            int offset = 0, targetIndex = 0;
            var commands = new ArrayList<Map<Texture, Map<Target, List<Command>>>>();
            for (int rank = 0; rank < 6; rank++) { commands.add(new LinkedHashMap<>()); }
            while (offset < visible.size()) {
                int count = Math.min(visible.size() - offset, maxSide * maxSide);
                int side = Math.min(maxSide, (int) Math.ceil(Math.sqrt(count)));
                Target target;
                if (targetIndex == targets.size()) {
                    target = new Target(side, pitchX, pitchY);
                    targets.add(target);
                } else {
                    target = targets.get(targetIndex);
                    if (target.side < side || target.pitchX != pitchX || target.pitchY != pitchY) {
                        Target replacement = new Target(side, pitchX, pitchY);
                        state.releasedTarget(target.buffer.getFramebufferHandle(), target.buffer.getColorBufferTexture().getTextureObjectHandle());
                        target.dispose();
                        targets.set(targetIndex, replacement);
                        target = replacement;
                    }
                }
                collect(target, visible.subList(offset, offset + count), commands);
                offset += count;
                targetIndex++;
            }
            for (var target : targets) {
                target.buffer.begin();
                clear();
            }
            batch.setBlendFunctionSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
            // A rank is a dependency barrier. Within it cells are independent, including cells
            // on different targets, so all characters can be grouped by the actual source page.
            for (var rank : commands) {
                for (var page : rank.values()) {
                    for (var entry : page.entrySet()) {
                        var target = entry.getKey();
                        target.buffer.begin();
                        batch.setProjectionMatrix(target.projection);
                        batch.begin();
                        for (var command : entry.getValue()) {
                            batch.draw(command.region, command.x, command.y);
                        }
                        batch.end();
                    }
                }
            }
        } finally {
            if (batch.isDrawing()) { batch.end(); }
            state.restore();
        }
    }

    private void refreshIdentities() {
        var definitions = List.of(CharacterAssets.CHARACTER, CharacterAssets.FEET, CharacterAssets.BODY,
            CharacterAssets.HEAD, CharacterAssets.HANDS, CharacterAssets.WEAPONS);
        boolean changed = false;
        for (int i = 0; i < identities.length; i++) {
            var atlas = assets.get(TextureAtlas.class, definitions.get(i).id());
            changed |= identities[i] != atlas;
            identities[i] = atlas;
        }
        if (changed) { layers.clear(); }
    }

    private TextureRegion[] frames(Entity entity) {
        var animation = Animation2D.MAPPER.get(entity);
        var equipment = Loadout.MAPPER.get(entity).getEquipment();
        var weapon = Combat.MAPPER.get(entity).getCurrentWeapon();
        String[] prefixes = {"", equipment.feet(), equipment.body(), equipment.head(), equipment.hands(), weapon.atlasKey()};
        String suffix = animation.getAction() + "_" + animation.getFdirection().atlasKey;
        int frame = animation.getFrameIndex();
        var result = new TextureRegion[6];
        for (int layer = 0; layer < 6; layer++) {
            if (layer == 5 && Loadout.MAPPER.get(entity).getWeaponId() == null) { continue; }
            Set<String> actions = layer == 5
                ? new HashSet<>(List.of("idle", "walk", "death", weapon.primary().animation().action(), weapon.secondary().animation().action()))
                : ACTIONS;
            var key = new LayerKey(layer, prefixes[layer], actions);
            var cache = layers.computeIfAbsent(key, this::loadLayer);
            var sequence = cache.get(suffix);
            if (sequence == null) { throw new IllegalArgumentException("Unsupported character action: " + suffix); }
            result[layer] = sequence[frame];
        }
        return result;
    }

    private Map<String, TextureRegion[]> loadLayer(LayerKey key) {
        var result = new HashMap<String, TextureRegion[]>();
        for (String action : key.actions) {
            for (var direction : Facing.Direction.values()) {
                String suffix = action + "_" + direction.atlasKey;
                String name = key.prefix.isEmpty() ? suffix : key.prefix + "/" + suffix;
                var frames = identities[key.layer].findRegions(name);
                var reference = identities[0].findRegions(suffix);
                if (reference.size != 8 || frames.size != reference.size) {
                    throw new IllegalArgumentException("Composite layer frame count mismatch: " + name);
                }
                var sequence = new TextureRegion[frames.size];
                for (int i = 0; i < frames.size; i++) {
                    var frame = frames.get(i);
                    if (frame.index != i) { throw new IllegalArgumentException("Composite frame index mismatch: " + name); }
                    AnimationSystem.validateCanvas(frame, reference.get(i).getRegionWidth(), reference.get(i).getRegionHeight(), name);
                    sequence[i] = frame;
                }
                result.put(suffix, sequence);
            }
        }
        return result;
    }

    private void collect(Target target, List<Entity> entities, List<Map<Texture, Map<Target, List<Command>>>> commands) {
        for (int cell = 0; cell < entities.size(); cell++) {
            var entity = entities.get(cell);
            var frames = frames(entity);
            var order = Animation2D.MAPPER.get(entity).getFdirection() == Facing.Direction.UP ? UP : FRONT;
            var canvas = CompositeCharacter.MAPPER.get(entity);
            int x = cell % target.side * target.pitchX + 1, y = cell / target.side * target.pitchY + 1;
            for (var frame : frames) {
                if (frame != null && (frame.getRegionWidth() != canvas.canvasWidth()
                    || frame.getRegionHeight() != canvas.canvasHeight())) {
                    throw new IllegalArgumentException("Loaded canvas dimensions changed for character");
                }
            }
            for (int rank = 0; rank < order.length; rank++) {
                var frame = frames[order[rank]];
                if (frame != null) {
                    commands.get(rank).computeIfAbsent(frame.getTexture(), ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(target, ignored -> new ArrayList<>()).add(new Command(frame, x, y));
                }
            }
            var region = new TextureRegion(target.buffer.getColorBufferTexture(), x, y, canvas.canvasWidth(), canvas.canvasHeight());
            region.flip(false, true);
            completed.put(entity, region);
        }
    }

    private static void clear() {
        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    private boolean visible(Entity entity, OrthographicCamera camera) {
        var transform = Transform.MAPPER.get(entity);
        var canvas = CompositeCharacter.MAPPER.get(entity);
        var position = transform.getPosition();
        var size = transform.getSize();
        var scale = transform.getScaling();
        float cx = position.x + size.x * scale.x * 0.5f;
        float cy = position.y + size.y * scale.y * 0.5f;
        for (int index = 0; index < corners.length; index++) {
            float signX = index == 0 || index == 3 ? -1f : 1f;
            float signY = index < 2 ? -1f : 1f;
            rotated.set(signX * canvas.canvasWidth() * GameConsts.UNIT_SCALE * scale.x * 0.5f,
                signY * canvas.canvasHeight() * GameConsts.UNIT_SCALE * scale.y * 0.5f)
                .rotateDeg(transform.getRotationDeg());
            corners[index].set(cx + rotated.x, cy + rotated.y, 0).mul(camera.combined);
        }
        return intersectsClip();
    }

    private boolean intersectsClip() {
        for (var point : corners) {
            if (insideClip(point.x, point.y)) { return true; }
        }
        for (int index = 0; index < CLIP_X.length; index++) {
            if (insideQuad(CLIP_X[index], CLIP_Y[index])) { return true; }
        }
        for (int index = 0; index < corners.length; index++) {
            var start = corners[index];
            var end = corners[(index + 1) % corners.length];
            for (int clip = 0; clip < CLIP_X.length; clip++) {
                int next = (clip + 1) % CLIP_X.length;
                if (segmentsIntersect(start.x, start.y, end.x, end.y,
                    CLIP_X[clip], CLIP_Y[clip], CLIP_X[next], CLIP_Y[next])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean insideClip(float x, float y) {
        return x >= -1f && x <= 1f && y >= -1f && y <= 1f;
    }

    private boolean insideQuad(float x, float y) {
        boolean positive = false;
        boolean negative = false;
        for (int index = 0; index < corners.length; index++) {
            var start = corners[index];
            var end = corners[(index + 1) % corners.length];
            float cross = (end.x - start.x) * (y - start.y) - (end.y - start.y) * (x - start.x);
            positive |= cross > 0f;
            negative |= cross < 0f;
            if (positive && negative) { return false; }
        }
        return true;
    }

    private static boolean segmentsIntersect(float ax, float ay, float bx, float by,
                                             float cx, float cy, float dx, float dy) {
        float abC = cross(ax, ay, bx, by, cx, cy);
        float abD = cross(ax, ay, bx, by, dx, dy);
        float cdA = cross(cx, cy, dx, dy, ax, ay);
        float cdB = cross(cx, cy, dx, dy, bx, by);
        if (abC == 0f && onSegment(ax, ay, bx, by, cx, cy)) { return true; }
        if (abD == 0f && onSegment(ax, ay, bx, by, dx, dy)) { return true; }
        if (cdA == 0f && onSegment(cx, cy, dx, dy, ax, ay)) { return true; }
        if (cdB == 0f && onSegment(cx, cy, dx, dy, bx, by)) { return true; }
        return (abC > 0f) != (abD > 0f) && (cdA > 0f) != (cdB > 0f);
    }

    private static float cross(float ax, float ay, float bx, float by, float px, float py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean onSegment(float ax, float ay, float bx, float by, float px, float py) {
        return px >= Math.min(ax, bx) && px <= Math.max(ax, bx)
            && py >= Math.min(ay, by) && py <= Math.max(ay, by);
    }

    @Override public void dispose() {
        if (disposed) { return; }
        disposed = true;
        var state = new RenderState();
        try {
            for (var target : targets) {
                state.releasedTarget(target.buffer.getFramebufferHandle(), target.buffer.getColorBufferTexture().getTextureObjectHandle());
                target.dispose();
            }
            targets.clear();
            completed.clear();
            layers.clear();
            Arrays.fill(identities, null);
            state.releasedShader(batch.getShader().getHandle());
            batch.dispose();
        } finally { state.restore(); }
    }

    private record LayerKey(int layer, String prefix, Set<String> actions) { }
    private record Command(TextureRegion region, int x, int y) { }
    private static final class Target implements Disposable {
        final int side;
        final FrameBuffer buffer;
        final Matrix4 projection;
        final int pitchX, pitchY;
        Target(int side, int pitchX, int pitchY) {
            this.side = side;
            this.pitchX = pitchX;
            this.pitchY = pitchY;
            projection = new Matrix4().setToOrtho2D(0, 0, side * pitchX, side * pitchY);
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, side * pitchX, side * pitchY, false);
            buffer.getColorBufferTexture().setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        @Override public void dispose() { buffer.dispose(); }
    }
}
