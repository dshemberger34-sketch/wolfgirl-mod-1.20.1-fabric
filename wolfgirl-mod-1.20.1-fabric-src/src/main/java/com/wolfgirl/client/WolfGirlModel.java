package com.wolfgirl.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wolfgirl.WolfGirlEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders the Blockbench "Wolf girl" model directly from baked geometry
 * (assets/wolfgirl/geo/wolf_girl.json), so per-face UVs and rotated cubes from
 * the free-format model are preserved exactly. Plays the "Walk_WolfAnimaion"
 * keyframe animation synced to walking speed, plus head look, idle tail wag and sitting.
 */
public class WolfGirlModel extends EntityModel<WolfGirlEntity> {
    private static final String GEO_PATH = "/assets/wolfgirl/geo/wolf_girl.json";
    private static final Set<String> LOOK_BONES = Set.of("head", "ears", "hair");

    private static final class Bone {
        String name;
        int parent;
        float px, py, pz;
        float rx, ry, rz;
        float[] quads;
        final List<Bone> children = new ArrayList<>();
    }

    private static final class Channel {
        float[] times = new float[0];
        float[][] values = new float[0][];
    }

    private final List<Bone> bones = new ArrayList<>();
    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Channel[]> walk = new HashMap<>();
    private float walkLength = 1.0f;
    private float lookPivotY = 25.5f;

    private float limbAngle;
    private float limbDistance;
    private float age;
    private float headYaw;
    private float headPitch;
    private boolean sitting;

    public WolfGirlModel() {
        super();
        load();
    }

    private void load() {
        try (InputStream in = WolfGirlModel.class.getResourceAsStream(GEO_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Missing model file " + GEO_PATH);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();

            for (JsonElement el : root.getAsJsonArray("bones")) {
                JsonObject o = el.getAsJsonObject();
                Bone b = new Bone();
                b.name = o.get("name").getAsString();
                b.parent = o.get("parent").getAsInt();
                JsonArray p = o.getAsJsonArray("pivot");
                b.px = p.get(0).getAsFloat();
                b.py = p.get(1).getAsFloat();
                b.pz = p.get(2).getAsFloat();
                JsonArray r = o.getAsJsonArray("rotation");
                b.rx = r.get(0).getAsFloat();
                b.ry = r.get(1).getAsFloat();
                b.rz = r.get(2).getAsFloat();
                JsonArray q = o.getAsJsonArray("quads");
                b.quads = new float[q.size()];
                for (int i = 0; i < b.quads.length; i++) {
                    b.quads[i] = q.get(i).getAsFloat();
                }
                bones.add(b);
            }
            for (Bone b : bones) {
                if (b.parent >= 0) {
                    bones.get(b.parent).children.add(b);
                } else {
                    roots.add(b);
                }
                if (b.name.equals("head")) {
                    lookPivotY = b.py;
                }
            }

            JsonObject anims = root.getAsJsonObject("animations");
            if (anims != null && !anims.entrySet().isEmpty()) {
                JsonObject anim = anims.entrySet().iterator().next().getValue().getAsJsonObject();
                walkLength = Math.max(0.05f, anim.get("length").getAsFloat());
                for (Map.Entry<String, JsonElement> e : anim.getAsJsonObject("bones").entrySet()) {
                    JsonObject c = e.getValue().getAsJsonObject();
                    walk.put(e.getKey(), new Channel[]{readChannel(c.getAsJsonArray("rotation")), readChannel(c.getAsJsonArray("position"))});
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load Wolf Girl model", ex);
        }
    }

    private static Channel readChannel(JsonArray arr) {
        Channel ch = new Channel();
        if (arr == null) {
            return ch;
        }
        ch.times = new float[arr.size()];
        ch.values = new float[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonArray k = arr.get(i).getAsJsonArray();
            ch.times[i] = k.get(0).getAsFloat();
            ch.values[i] = new float[]{k.get(1).getAsFloat(), k.get(2).getAsFloat(), k.get(3).getAsFloat()};
        }
        return ch;
    }

    private static float[] sample(Channel ch, float t) {
        int n = ch.times.length;
        if (n == 0) {
            return new float[]{0f, 0f, 0f};
        }
        if (t <= ch.times[0]) {
            return ch.values[0];
        }
        for (int i = 0; i < n - 1; i++) {
            float a = ch.times[i];
            float b = ch.times[i + 1];
            if (t >= a && t <= b) {
                float k = b > a ? (t - a) / (b - a) : 0f;
                float[] va = ch.values[i];
                float[] vb = ch.values[i + 1];
                return new float[]{
                        va[0] + (vb[0] - va[0]) * k,
                        va[1] + (vb[1] - va[1]) * k,
                        va[2] + (vb[2] - va[2]) * k};
            }
        }
        return ch.values[n - 1];
    }

    @Override
    public void setAngles(WolfGirlEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        this.limbAngle = limbAngle;
        this.limbDistance = limbDistance;
        this.age = animationProgress;
        this.headYaw = headYaw;
        this.headPitch = headPitch;
        this.sitting = entity.isInSittingPose();
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        // One vanilla walk cycle (2*PI / 0.6662 limb units) == one loop of the Blockbench animation.
        float cycle = (limbAngle * 0.6662f) / (float) (Math.PI * 2.0);
        float t = (cycle - (float) Math.floor(cycle)) * walkLength;
        float weight = sitting ? 0f : MathHelper.clamp(limbDistance * 1.6f, 0f, 1f);

        matrices.push();
        // Vanilla entity model space (y down, origin 24px above feet) -> Blockbench space (y up, pixels).
        matrices.translate(0.0f, 1.5f, 0.0f);
        matrices.scale(-1.0f, -1.0f, 1.0f);
        matrices.scale(1.0f / 16.0f, 1.0f / 16.0f, 1.0f / 16.0f);
        if (sitting) {
            matrices.translate(0.0f, -11.0f, 0.0f);
        }
        for (Bone root : roots) {
            renderBone(root, matrices, vertices, light, overlay, red, green, blue, alpha, t, weight);
        }
        matrices.pop();
    }

    private void renderBone(Bone b, MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
                            float red, float green, float blue, float alpha, float t, float weight) {
        matrices.push();

        if (LOOK_BONES.contains(b.name) && b.parent >= 0 && bones.get(b.parent).parent < 0) {
            matrices.translate(0.0f, lookPivotY, 0.0f);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-headYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-headPitch));
            matrices.translate(0.0f, -lookPivotY, 0.0f);
        }

        float rx = b.rx, ry = b.ry, rz = b.rz;
        float ox = 0f, oy = 0f, oz = 0f;

        Channel[] ch = walk.get(b.name);
        if (ch != null && weight > 0.001f) {
            float[] r = sample(ch[0], t);
            rx -= r[0] * weight;
            ry -= r[1] * weight;
            rz += r[2] * weight;
            float[] p = sample(ch[1], t);
            ox = -p[0] * weight;
            oy = p[1] * weight;
            oz = p[2] * weight;
        }

        float idle = 1.0f - weight;
        switch (b.name) {
            case "tail" -> {
                ry += MathHelper.sin(age * 0.12f) * 14.0f * idle;
                if (sitting) {
                    rx -= 35.0f;
                }
            }
            case "ear_r_inner", "ear_l_inner" -> {
                float twitch = MathHelper.sin(age * 0.05f + (b.name.startsWith("ear_r") ? 0f : 1.7f));
                rz += (twitch > 0.95f ? (twitch - 0.95f) * 160.0f : 0f) * (b.name.startsWith("ear_r") ? 1f : -1f);
            }
            case "body" -> rx += MathHelper.sin(age * 0.08f) * 0.8f * idle;
            case "legs" -> {
                if (sitting) {
                    rx += 90.0f;
                }
            }
            case "hand_r", "hand_l" -> {
                if (sitting) {
                    rx += 25.0f;
                } else {
                    rz += MathHelper.sin(age * 0.067f) * 2.5f * idle * (b.name.equals("hand_r") ? -1f : 1f);
                }
            }
            default -> {
            }
        }

        matrices.translate(ox, oy, oz);
        matrices.translate(b.px, b.py, b.pz);
        if (rz != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rz));
        }
        if (ry != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
        }
        if (rx != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
        }
        matrices.translate(-b.px, -b.py, -b.pz);

        emitQuads(b.quads, matrices.peek(), vertices, light, overlay, red, green, blue, alpha);

        for (Bone child : b.children) {
            renderBone(child, matrices, vertices, light, overlay, red, green, blue, alpha, t, weight);
        }
        matrices.pop();
    }

    private static void emitQuads(float[] q, MatrixStack.Entry entry, VertexConsumer vertices, int light, int overlay,
                                  float red, float green, float blue, float alpha) {
        Matrix4f pose = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        // Layout per vertex: x, y, z, u, v, nx, ny, nz ; 4 vertices per quad.
        for (int i = 0; i + 31 < q.length; i += 32) {
            Vector3f n = normalMatrix.transform(new Vector3f(q[i + 5], q[i + 6], q[i + 7]));
            for (int v = 0; v < 4; v++) {
                int o = i + v * 8;
                Vector4f pos = pose.transform(new Vector4f(q[o], q[o + 1], q[o + 2], 1.0f));
                vertices.vertex(pos.x(), pos.y(), pos.z(), red, green, blue, alpha,
                        q[o + 3], q[o + 4], overlay, light, n.x(), n.y(), n.z());
            }
        }
    }
}
