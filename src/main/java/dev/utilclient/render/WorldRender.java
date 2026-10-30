/*
 * Rendering pipeline setup, buffer handling and line drawing adapted from
 * Wurst Client (https://github.com/Wurst-Imperium/Wurst7),
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors, GPL-3.0.
 * Modified for AloneByDeath Client, which is also distributed under GPL-3.0.
 */
package dev.utilclient.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.utilclient.module.ModuleManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Real 3D lines and boxes drawn into the world after it renders, without depth testing
 * (so they show through walls). If anything about it fails, {@link #active()} turns false and
 * modules fall back to their 2D screen-space drawing.
 */
public final class WorldRender {
    private static final Logger LOG = Logger.getLogger("alonebydeathclient");
    private static volatile long lastFrame;
    private static volatile boolean broken;

    private WorldRender() {}

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("alonebydeathclient", path); }

    /** Pipelines are created lazily the first time the world renders. */
    private static final class Layers {
        static final RenderPipeline.Snippet FOGLESS_LINES = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withVertexShader(id("core/fogless_lines"))
                .withFragmentShader(id("core/fogless_lines"))
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                .withPrimitiveTopology(PrimitiveTopology.LINES)
                .buildSnippet();

        static final RenderPipeline ESP_LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES)
                .withLocation(id("pipeline/abd_esp_lines"))
                .withDepthStencilState(Optional.empty())
                .build());

        static final RenderPipeline ESP_QUADS_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(id("pipeline/abd_esp_quads"))
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .build());

        static final RenderType LINES = RenderType.create("alonebydeathclient:esp_lines",
                RenderSetup.builder(ESP_LINES_PIPELINE)
                        .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                        .createRenderSetup());

        static final RenderType QUADS = RenderType.create("alonebydeathclient:esp_quads",
                RenderSetup.builder(ESP_QUADS_PIPELINE).sortOnUpload().createRenderSetup());
    }

    /** Collects geometry and draws it in one render pass (Wurst's buffer source). */
    private static final class Batch {
        private final StagedVertexBuffer stagedBuffer = new StagedVertexBuffer(() -> "AloneByDeathBatch", RenderType.BIG_BUFFER_SIZE);
        private final List<StagedVertexBuffer.Draw> draws = new ArrayList<>();
        private final List<RenderType> drawTypes = new ArrayList<>();

        VertexConsumer getBuffer(RenderType renderType) {
            if (!drawTypes.isEmpty() && drawTypes.getLast() == renderType && renderType.canConsolidateConsecutiveGeometry()) {
                return stagedBuffer.getVertexBuilder(draws.getLast());
            }
            StagedVertexBuffer.Draw draw = stagedBuffer.appendDraw(renderType.format(), renderType.primitiveTopology(),
                    renderType.sortOnUpload() ? RenderSystem.getProjectionType().vertexSorting() : null);
            draws.add(draw);
            drawTypes.add(renderType);
            return stagedBuffer.getVertexBuilder(draw);
        }

        void uploadAndDraw() {
            try {
                if (draws.isEmpty()) return;
                stagedBuffer.upload();
                RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                        () -> "AloneByDeathBatch", target.getColorTextureView(), Optional.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
                    RenderSystem.bindDefaultUniforms(pass);
                    for (int i = 0; i < draws.size(); i++) {
                        StagedVertexBuffer.ExecuteInfo info = stagedBuffer.getExecuteInfo(draws.get(i));
                        if (info != null) drawTypes.get(i).prepare().drawFromBuffer(info, pass);
                    }
                }
                stagedBuffer.endDraw();
            } finally {
                draws.clear();
                drawTypes.clear();
                stagedBuffer.close();
            }
        }
    }

    /** What modules draw with during {@code renderWorld}. All positions are world coordinates. */
    public static final class Ctx {
        public final float partialTick;
        public final Vec3 cam;
        private final PoseStack.Pose pose;
        private final Batch batch = new Batch();
        private final Vec3 look;

        Ctx(PoseStack stack, float partialTick) {
            this.partialTick = partialTick;
            Camera c = Minecraft.getInstance().gameRenderer.mainCamera();
            this.cam = c.position();
            this.pose = stack.last();
            double yaw = Math.toRadians(c.yRot()), pitch = Math.toRadians(c.xRot());
            this.look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        }

        public void line(Vec3 a, Vec3 b, int argb) {
            if ((argb >>> 24) == 0) return;
            VertexConsumer buf = batch.getBuffer(Layers.LINES);
            lineRel(buf, (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                    (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z), argb);
        }

        private void lineRel(VertexConsumer buf, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
            Vector3f normal = new Vector3f(x2, y2, z2).sub(x1, y1, z1);
            float length = normal.length();
            if (length < 1e-6f) return;
            normal.normalize();
            buf.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, normal).setLineWidth(2);
            // lines crossing the camera plane get an extra vertex (works around the line shader)
            float t = new Vector3f(x1, y1, z1).negate().dot(normal);
            if (t > 0 && t < length) {
                Vector3f close = new Vector3f(normal).mul(t).add(x1, y1, z1);
                buf.addVertex(pose, close).setColor(color).setNormal(pose, normal).setLineWidth(2);
                buf.addVertex(pose, close).setColor(color).setNormal(pose, normal).setLineWidth(2);
            }
            buf.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, normal).setLineWidth(2);
        }

        /** Line from just in front of the camera to a point. */
        public void tracer(Vec3 end, int argb) {
            line(cam.add(look.scale(2.0)), end, argb);
        }

        public void outline(AABB b, int argb) {
            if ((argb >>> 24) == 0) return;
            double x1 = b.minX, y1 = b.minY, z1 = b.minZ, x2 = b.maxX, y2 = b.maxY, z2 = b.maxZ;
            line(new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), argb);
            line(new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), argb);
            line(new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), argb);
            line(new Vec3(x1, y1, z2), new Vec3(x1, y1, z1), argb);
            line(new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), argb);
            line(new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), argb);
            line(new Vec3(x2, y2, z2), new Vec3(x1, y2, z2), argb);
            line(new Vec3(x1, y2, z2), new Vec3(x1, y2, z1), argb);
            line(new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), argb);
            line(new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), argb);
            line(new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), argb);
            line(new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), argb);
        }

        public void fill(AABB b, int argb) {
            if ((argb >>> 24) == 0) return;
            VertexConsumer buf = batch.getBuffer(Layers.QUADS);
            float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
            float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);
            quad(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, argb);
            quad(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, argb);
            quad(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, argb);
            quad(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, argb);
            quad(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, argb);
            quad(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, argb);
        }

        private void quad(VertexConsumer buf, float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz, int c) {
            buf.addVertex(pose, ax, ay, az).setColor(c);
            buf.addVertex(pose, bx, by, bz).setColor(c);
            buf.addVertex(pose, cx, cy, cz).setColor(c);
            buf.addVertex(pose, dx, dy, dz).setColor(c);
        }

        /** Filled box with an outline: the usual ESP look. */
        public void box(AABB b, int fillArgb, int lineArgb) {
            fill(b, fillArgb);
            outline(b, lineArgb);
        }

        void finish() { batch.uploadAndDraw(); }
    }

    /** Full-block box at a position. */
    public static AABB block(net.minecraft.core.BlockPos p) {
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
    }

    /** Called from LevelRendererMixin after the world has been drawn. */
    public static void onLevelRender(PoseStack stack, float partialTick) {
        if (broken) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        try {
            Ctx ctx = new Ctx(stack, partialTick);
            ModuleManager.renderWorld(ctx);
            ctx.finish();
            lastFrame = System.nanoTime();
        } catch (Throwable t) {
            broken = true;
            LOG.log(Level.WARNING, "3D rendering failed; ESP falls back to 2D overlays", t);
        }
    }

    /** True while 3D drawing works; modules otherwise draw on the HUD. */
    public static boolean active() {
        return !broken && System.nanoTime() - lastFrame < 250_000_000L;
    }
}
