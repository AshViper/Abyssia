package com.abyssia.habitat.aquarium.client;

import com.abyssia.entity.FaunaAnimated;
import com.abyssia.habitat.aquarium.AquariumBlockEntity;
import com.abyssia.habitat.aquarium.AquariumEntry;
import com.abyssia.habitat.aquarium.CreatureCaptureCanisterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BT01g: the aquarium water (translucent box inside the glass, drawn here instead of a fluid) and its creatures. Each
 * species gets one cached client-only entity instance (never added to the level) drawn with its normal renderer along
 * a looping swim path; juveniles at half size. Origin = the controller block (floor centre).
 */
public class AquariumRenderer implements BlockEntityRenderer<AquariumBlockEntity>
{
    private static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still");
    /** inner glass faces (local to the controller block, which spans 0..1) */
    private static final float INSET = 3.0f / 16.0f;
    private static final float WATER_BOTTOM = 5.0f / 16.0f;
    private static final float WATER_TOP = AquariumEntry.HEIGHT - 3.0f / 16.0f;
    /** Entity.wasTouchingWater (NeoForge runs mojmap names in dev and production): set so renderers draw swimming, not stranded, poses */
    private static final Field IN_WATER = findInWater();

    private final EntityRenderDispatcher dispatcher;
    private final Map<EntityType<?>, Entity> cache = new HashMap<>();

    public AquariumRenderer(BlockEntityRendererProvider.Context context)
    {
        this.dispatcher = context.getEntityRenderer();
    }

    @Override
    public void render(AquariumBlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay)
    {
        Level level = be.getLevel();
        if (level == null) return;
        int lit = brightLight(level, be.getBlockPos());
        renderWater(pose, buffers, lit);

        List<AquariumBlockEntity.Creature> creatures = be.creatures();
        if (creatures.isEmpty()) return;
        long gameTime = level.getGameTime();
        float time = gameTime + partialTick;
        boolean shadows = Minecraft.getInstance().options.entityShadows().get();
        dispatcher.setRenderShadow(false);
        try
        {
            for (int i = 0; i < creatures.size(); i++)
                renderCreature(level, be.getBlockPos(), creatures.get(i), i, creatures.size(), gameTime, time, partialTick, pose, buffers, lit);
        }
        finally
        {
            dispatcher.setRenderShadow(shadows);
        }
    }

    private void renderCreature(Level level, BlockPos origin, AquariumBlockEntity.Creature creature, int index, int count, long gameTime,
                                float time, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        Entity entity = entity(level, creature.species());
        if (entity == null) return;
        // ellipse around the tank centre, alternate creatures swim the other way, slow vertical bob
        float dir = (index & 1) == 0 ? 1.0f : -1.0f;
        float speed = 0.018f + 0.004f * (index % 3);
        double angle = dir * time * speed + index * (Math.PI * 2.0 / Math.max(1, count));
        float radius = 1.05f + 0.35f * ((index * 37) % 5) / 4.0f;
        double x = 0.5 + Math.cos(angle) * radius;
        double z = 0.5 + Math.sin(angle) * radius;
        double low = WATER_BOTTOM + 0.35, high = WATER_TOP - 0.45;
        double y = low + (high - low) * (0.5 + 0.45 * Math.sin(time * 0.01 + index * 1.7));
        // heading = derivative of the path; Minecraft yaw faces (-sin, cos)
        double dx = -Math.sin(angle) * dir, dz = Math.cos(angle) * dir;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        float scale = creature.adult() ? 1.0f : 0.5f;
        float size = Math.max(entity.getBbWidth(), entity.getBbHeight());
        if (size > 1.0f) scale /= size;

        entity.setPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        entity.setXRot(0.0f);
        entity.xRotO = 0.0f;
        entity.tickCount = (int) gameTime + index * 13;
        if (entity instanceof LivingEntity living)
        {
            living.yBodyRot = living.yBodyRotO = yaw;
            living.yHeadRot = living.yHeadRotO = yaw;
            living.walkAnimation.setSpeed(0.6f);
        }
        setInWater(entity);
        if (entity instanceof FaunaAnimated animated) animated.animations().when("swim", true);

        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(scale, scale, scale);
        try
        {
            dispatcher.render(entity, 0.0, 0.0, 0.0, yaw, partialTick, pose, buffers, light);
        }
        catch (RuntimeException e)
        {
            // a renderer that cannot cope with a detached entity: drop it from the cache, skip this frame
            cache.remove(entity.getType());
        }
        pose.popPose();
    }

    private Entity entity(Level level, String species)
    {
        EntityType<?> type = CreatureCaptureCanisterItem.type(species);
        if (type == null) return null;
        Entity entity = cache.get(type);
        if (entity == null || entity.level() != level)
        {
            entity = type.create(level);
            if (entity == null) return null;
            entity.setSilent(true);
            entity.noPhysics = true;
            cache.put(type, entity);
        }
        return entity;
    }

    private static void renderWater(PoseStack pose, MultiBufferSource buffers, int light)
    {
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        int half = AquariumEntry.SIZE / 2;
        float x0 = -half + INSET, x1 = half + 1 - INSET, z0 = -half + INSET, z1 = half + 1 - INSET;
        float y0 = WATER_BOTTOM, y1 = WATER_TOP;
        PoseStack.Pose m = pose.last();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        int r = 0x3F, g = 0x9A, b = 0xE4, a = 110;
        // top
        quad(vc, m, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1, r, g, b, a, light);
        // bottom
        quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, v0, u1, v1, r, g, b, a, light);
        // north (-z)
        quad(vc, m, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, u0, v0, u1, v1, r, g, b, a, light);
        // south (+z)
        quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, u0, v0, u1, v1, r, g, b, a, light);
        // west (-x)
        quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light);
        // east (+x)
        quad(vc, m, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, u0, v0, u1, v1, r, g, b, a, light);
    }

    /** One quad, corners counter-clockwise seen from outside (cull keeps the outer side). */
    private static void quad(VertexConsumer vc, PoseStack.Pose m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1,
                             int r, int g, int b, int a, int light)
    {
        vertex(vc, m, ax, ay, az, u0, v1, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, bx, by, bz, u1, v1, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, cx, cy, cz, u1, v0, nx, ny, nz, r, g, b, a, light);
        vertex(vc, m, dx, dy, dz, u0, v0, nx, ny, nz, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose m, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz, int r, int g, int b, int a, int light)
    {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, ny, nz);
    }

    /** light of the tank's air above the controller, at least block light 10 (the room is lit) */
    private static int brightLight(Level level, BlockPos pos)
    {
        int packed = LevelRenderer.getLightColor(level, pos.above());
        return LightTexture.pack(Math.max(10, LightTexture.block(packed)), LightTexture.sky(packed));
    }

    private static Field findInWater()
    {
        try
        {
            Field f = Entity.class.getDeclaredField("wasTouchingWater");
            f.setAccessible(true);
            return f;
        }
        catch (ReflectiveOperationException | RuntimeException e)
        {
            return null;
        }
    }

    private static void setInWater(Entity entity)
    {
        if (IN_WATER == null) return;
        try
        {
            IN_WATER.setBoolean(entity, true);
        }
        catch (IllegalAccessException ignored) {}
    }

    @Override
    public AABB getRenderBoundingBox(AquariumBlockEntity be)
    {
        int half = AquariumEntry.SIZE / 2;
        BlockPos pos = be.getBlockPos();
        return new AABB(pos.getX() - half, pos.getY(), pos.getZ() - half,
                pos.getX() + half + 1, pos.getY() + AquariumEntry.HEIGHT, pos.getZ() + half + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(AquariumBlockEntity be)
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 48;
    }
}
