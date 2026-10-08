package com.abyssia.client.light;

import com.abyssia.Abyssia;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copies of the solid and cutout blocks of level sections, for spotlights that shine on them again with their own
 * shader ({@link SpotlightProjector}). Shape, texture and tint only: no vanilla shading or ambient occlusion, the light
 * brings its own. Translucent blocks (water) are left out, so the light shines through them.
 * <p>
 * Each is made on the render thread when a light first needs it, within {@link #BUDGET_NANOS} per frame, closest to a
 * light first. There is no client block-change event without a mixin, so a copy in use is remade every
 * {@link #REFRESH_MS} (still drawn meanwhile), and when its or a neighbouring chunk loads. Copies no light needed for
 * {@link #KEEP_UNUSED} ms are thrown away. Ported from AshWarfare's BlockMeshes.
 */
@EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
public final class BlockMeshes
{
    private static final long BUDGET_NANOS = 2_000_000L;
    private static final long KEEP_UNUSED = 20_000L;
    private static final long REFRESH_MS = 1_500L;
    private static final int MAX_MESHES = 512;

    /** The copy of one section. */
    static final class Mesh
    {
        final SectionPos pos;
        // null while not made yet or when there is nothing to draw
        @Nullable VertexBuffer buffer;
        boolean made;
        boolean outdated;
        long madeAt;
        long lastUsed;

        Mesh(SectionPos pos)
        {
            this.pos = pos;
        }

        BlockPos origin()
        {
            return pos.origin();
        }
    }

    private static final Map<Long, Mesh> meshes = new HashMap<>();
    // asked for this frame and not made yet or outdated, with the distance to the light that asked
    private static final Map<Mesh, Double> wanted = new HashMap<>();
    @Nullable private static ClientLevel meshLevel;
    @Nullable private static ByteBufferBuilder bytes;

    private BlockMeshes() {}

    /** The copy of a section, drawn once {@link Mesh#made}; made or remade this frame or a later one if needed. */
    static Mesh get(ClientLevel level, SectionPos pos, double lightDistance)
    {
        if (level != meshLevel)
        {
            clear();
            meshLevel = level;
        }
        Mesh mesh = meshes.computeIfAbsent(pos.asLong(), key -> new Mesh(pos));
        long now = Util.getMillis();
        mesh.lastUsed = now;
        if (mesh.made && now - mesh.madeAt > REFRESH_MS) mesh.outdated = true;
        if (!mesh.made || mesh.outdated) wanted.merge(mesh, lightDistance, Math::min);
        return mesh;
    }

    /** Makes the copies asked for this frame within the budget (at least one), drops long-unused ones. */
    static void make(ClientLevel level)
    {
        if (level != meshLevel)
        {
            wanted.clear();
            return;
        }
        List<Map.Entry<Mesh, Double>> queue = new ArrayList<>(wanted.entrySet());
        // never-made copies first, then the closest
        queue.sort(Comparator.<Map.Entry<Mesh, Double>, Boolean>comparing(e -> e.getKey().made).thenComparing(Map.Entry.comparingByValue()));
        wanted.clear();
        long start = System.nanoTime();
        for (Map.Entry<Mesh, Double> entry : queue)
        {
            make(level, entry.getKey());
            if (System.nanoTime() - start > BUDGET_NANOS) break;
        }
        long now = Util.getMillis();
        meshes.values().removeIf(mesh -> {
            boolean unused = now - mesh.lastUsed > KEEP_UNUSED;
            if (unused) close(mesh);
            return unused;
        });
        if (meshes.size() > MAX_MESHES)
            meshes.values().stream().sorted(Comparator.comparingLong(mesh -> mesh.lastUsed)).limit(meshes.size() - MAX_MESHES).toList()
                    .forEach(mesh -> {
                        close(mesh);
                        meshes.remove(mesh.pos.asLong());
                    });
    }

    private static void make(ClientLevel level, Mesh mesh)
    {
        SectionPos pos = mesh.pos;
        mesh.made = true;
        mesh.outdated = false;
        mesh.madeAt = Util.getMillis();
        if (!level.hasChunk(pos.x(), pos.z()) || level.isOutsideBuildHeight(pos.minBlockY()))
        {
            close(mesh);
            return;
        }
        LevelChunkSection section = level.getChunk(pos.x(), pos.z()).getSection(level.getSectionIndexFromSectionY(pos.y()));
        if (section.hasOnlyAir())
        {
            close(mesh);
            return;
        }
        if (bytes == null) bytes = new ByteBufferBuilder(DefaultVertexFormat.BLOCK.getVertexSize() * 4 * 4096);
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
        BlockColors colors = minecraft.getBlockColors();
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        PoseStack poseStack = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        BlockPos origin = pos.origin();
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                {
                    BlockState state = section.getBlockState(x, y, z);
                    if (state.getRenderShape() != RenderShape.MODEL) continue;
                    blockPos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    poseStack.pushPose();
                    Vec3 offset = state.getOffset(level, blockPos);
                    poseStack.translate(x + offset.x, y + offset.y, z + offset.z);
                    block(level, blocks, colors, state, blockPos, neighbor, poseStack, builder, random);
                    poseStack.popPose();
                }
        MeshData data = builder.build();
        if (data == null)
        {
            close(mesh);
            return;
        }
        if (mesh.buffer == null) mesh.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        mesh.buffer.bind();
        mesh.buffer.upload(data);
        VertexBuffer.unbind();
    }

    // the quads vanilla draws in the solid and cutout layers, each face only if it can be seen
    private static void block(ClientLevel level, BlockRenderDispatcher blocks, BlockColors colors, BlockState state, BlockPos pos,
                              BlockPos.MutableBlockPos neighbor, PoseStack poseStack, BufferBuilder builder, RandomSource random)
    {
        BakedModel model = blocks.getBlockModel(state);
        ModelData data = model.getModelData(level, pos, state, level.getModelData(pos));
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        for (RenderType renderType : model.getRenderTypes(state, random, data))
        {
            if (renderType != RenderType.solid() && renderType != RenderType.cutoutMipped() && renderType != RenderType.cutout()) continue;
            for (Direction direction : Direction.values())
            {
                random.setSeed(seed);
                List<BakedQuad> quads = model.getQuads(state, direction, random, data, renderType);
                if (!quads.isEmpty() && Block.shouldRenderFace(state, level, pos, direction, neighbor.setWithOffset(pos, direction)))
                    quads(level, colors, state, pos, quads, poseStack, builder);
            }
            random.setSeed(seed);
            quads(level, colors, state, pos, model.getQuads(state, null, random, data, renderType), poseStack, builder);
        }
    }

    private static void quads(ClientLevel level, BlockColors colors, BlockState state, BlockPos pos, List<BakedQuad> quads,
                              PoseStack poseStack, BufferBuilder builder)
    {
        for (BakedQuad quad : quads)
        {
            float red = 1.0f, green = 1.0f, blue = 1.0f;
            if (quad.isTinted())
            {
                int tint = colors.getColor(state, level, pos, quad.getTintIndex());
                red = FastColor.ARGB32.red(tint) / 255.0f;
                green = FastColor.ARGB32.green(tint) / 255.0f;
                blue = FastColor.ARGB32.blue(tint) / 255.0f;
            }
            builder.putBulkData(poseStack.last(), quad, red, green, blue, 1.0f, 0, 0);
        }
    }

    // a chunk that comes in changes its own sections and the faces of its neighbours toward it
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event)
    {
        if (event.getLevel() != meshLevel) return;
        ChunkPos chunk = event.getChunk().getPos();
        meshes.values().stream()
                .filter(mesh -> Math.abs(mesh.pos.x() - chunk.x) <= 1 && Math.abs(mesh.pos.z() - chunk.z) <= 1)
                .forEach(mesh -> mesh.outdated = true);
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event)
    {
        if (event.getLevel() != meshLevel) return;
        ChunkPos chunk = event.getChunk().getPos();
        meshes.values().removeIf(mesh -> {
            boolean inChunk = mesh.pos.x() == chunk.x && mesh.pos.z() == chunk.z;
            if (inChunk) close(mesh);
            return inChunk;
        });
    }

    private static void clear()
    {
        meshes.values().forEach(BlockMeshes::close);
        meshes.clear();
        wanted.clear();
        meshLevel = null;
    }

    private static void close(Mesh mesh)
    {
        if (mesh.buffer != null)
        {
            mesh.buffer.close();
            mesh.buffer = null;
        }
    }

    @EventBusSubscriber(modid = Abyssia.MODID, value = Dist.CLIENT)
    public static final class Registration
    {
        private Registration() {}

        // the textures may move around in the block atlas
        @SubscribeEvent
        public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event)
        {
            event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> clear());
        }
    }
}
