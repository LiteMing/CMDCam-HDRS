package team.creative.cmdcam.common.target;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import team.creative.creativecore.common.util.math.vec.Vec3d;
import team.creative.creativecore.common.util.registry.NamedTypeRegistry;
import team.creative.creativecore.common.util.registry.exception.RegistryException;

public abstract class CamTarget {
    
    public static final NamedTypeRegistry<CamTarget> REGISTRY = new NamedTypeRegistry<CamTarget>().addConstructorPattern();
    
    public static CamTarget load(CompoundTag nbt) {
        try {
            CamTarget target = REGISTRY.create(nbt.getString("id"));
            target.loadExtra(nbt);
            return target;
        } catch (RegistryException e) {
            return null;
        }
    }
    
    static {
        REGISTRY.register("pos", BlockTarget.class);
        REGISTRY.register("entity", EntityTarget.class);
        REGISTRY.register("self", SelfTarget.class);
        REGISTRY.register("player", PlayerTarget.class);
    }
    
    public abstract Vec3d position(Level world, float partialTicks);
    
    public CamTargetPose pose(Level level, float partialTicks) {
        Vec3d pos = position(level, partialTicks);
        if (pos == null)
            return CamTargetPose.invalid();
        return new CamTargetPose(pos, 0, 0, 0f, 0f);
    }
    
    protected static CamTargetPose poseOf(Entity entity, float partialTicks) {
        if (entity == null || !entity.isAlive())
            return CamTargetPose.invalid();
        Vec3 eye = entity.getEyePosition(partialTicks);
        float yaw;
        if (entity instanceof LivingEntity living)
            yaw = Mth.rotLerp(partialTicks, living.yBodyRotO, living.yBodyRot);
        else
            yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        if (Float.isNaN(yaw))
            yaw = Mth.rotLerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.rotLerp(partialTicks, entity.xRotO, entity.getXRot());
        return new CamTargetPose(new Vec3d(eye), entity.getBbHeight(), entity.getEyeHeight(), yaw, pitch);
    }
    
    protected abstract void saveExtra(CompoundTag nbt);
    
    protected abstract void loadExtra(CompoundTag nbt);
    
    public CompoundTag save(CompoundTag nbt) {
        nbt.putString("id", REGISTRY.getId(this));
        saveExtra(nbt);
        return nbt;
    }
    
    public void start(Level level) {}
    
    public void finish() {}
    
    public abstract Component print(Level level);
    
    public static class BlockTarget extends CamTarget {
        
        public BlockPos pos;
        
        public BlockTarget() {}
        
        public BlockTarget(BlockPos pos) {
            this.pos = pos;
        }
        
        @Override
        public Vec3d position(Level level, float partialTicks) {
            return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        
        @Override
        protected void saveExtra(CompoundTag nbt) {
            nbt.putIntArray("data", new int[] { pos.getX(), pos.getY(), pos.getZ() });
        }
        
        @Override
        protected void loadExtra(CompoundTag nbt) {
            int[] array = nbt.getIntArray("data");
            if (array == null || array.length != 3)
                throw new IllegalArgumentException("Invalid block target data=" + array);
            pos = new BlockPos(array[0], array[1], array[2]);
        }
        
        @Override
        public Component print(Level level) {
            return Component.translatable("scene.output.pos", pos.getX(), pos.getY(), pos.getZ());
        }
        
    }
    
    public static class EntityTarget extends CamTarget {
        
        public Entity cachedEntity;
        public UUID uuid;
        
        public EntityTarget() {}
        
        public EntityTarget(Entity entity) {
            this.uuid = entity.getUUID();
        }
        
        public EntityTarget(UUID uuid) {
            this.uuid = uuid;
        }
        
        private void resolve(Level level) {
            if (cachedEntity != null && (!cachedEntity.isAlive() || cachedEntity.level() != level))
                cachedEntity = null;
            if (cachedEntity != null)
                return;
            if (level instanceof ServerLevel)
                cachedEntity = ((ServerLevel) level).getEntities().get(uuid);
            else if (level instanceof ClientLevel)
                for (Entity entity : ((ClientLevel) level).entitiesForRendering())
                    if (entity.getUUID().equals(uuid)) {
                        cachedEntity = entity;
                        break;
                    }
        }
        
        @Override
        public void start(Level level) {
            resolve(level);
        }
        
        @Override
        public void finish() {
            cachedEntity = null;
        }
        
        @Override
        public Vec3d position(Level level, float partialTicks) {
            resolve(level);
            if (cachedEntity == null)
                return null;
            return new Vec3d(cachedEntity.getEyePosition(partialTicks));
        }
        
        @Override
        public CamTargetPose pose(Level level, float partialTicks) {
            resolve(level);
            return poseOf(cachedEntity, partialTicks);
        }
        
        @Override
        protected void saveExtra(CompoundTag nbt) {
            nbt.putString("uuid", uuid.toString());
        }
        
        @Override
        protected void loadExtra(CompoundTag nbt) {
            uuid = UUID.fromString(nbt.getString("uuid"));
        }
        
        @Override
        public Component print(Level level) {
            Entity resultEntity = null;
            if (level instanceof ServerLevel)
                resultEntity = ((ServerLevel) level).getEntities().get(uuid);
            else
                for (Entity entity : ((ClientLevel) level).entitiesForRendering())
                    if (entity.getUUID().equals(uuid)) {
                        resultEntity = entity;
                        break;
                    }
            if (resultEntity != null)
                return Component.translatable("scene.output.entity_found", resultEntity.getDisplayName(), uuid.toString());
            return Component.translatable("scene.output.entity", uuid.toString());
        }
    }
    
    public static class SelfTarget extends CamTarget {
        
        public SelfTarget() {}
        
        @Override
        protected void saveExtra(CompoundTag nbt) {}
        
        @Override
        protected void loadExtra(CompoundTag nbt) {}
        
        @Override
        @OnlyIn(Dist.CLIENT)
        public Vec3d position(Level level, float partialTicks) {
            return new Vec3d(Minecraft.getInstance().player.getEyePosition(partialTicks));
        }
        
        @Override
        public Component print(Level level) {
            return Component.translatable("scene.output.self");
        }
        
    }
    
    public static class PlayerTarget extends CamTarget {
        
        public Player cachedPlayer;
        public UUID uuid;
        
        public PlayerTarget() {}
        
        public PlayerTarget(Player player) {
            this.cachedPlayer = player;
            this.uuid = player.getUUID();
        }
        
        @Override
        public void start(Level level) {
            cachedPlayer = level.getPlayerByUUID(uuid);
        }
        
        @Override
        public void finish() {
            cachedPlayer = null;
        }
        
        @Override
        public Vec3d position(Level level, float partialTicks) {
            if (cachedPlayer == null || !cachedPlayer.isAlive())
                return null;
            
            return new Vec3d(cachedPlayer.getEyePosition(partialTicks));
        }
        
        @Override
        public CamTargetPose pose(Level level, float partialTicks) {
            return poseOf(cachedPlayer, partialTicks);
        }
        
        @Override
        protected void saveExtra(CompoundTag nbt) {
            nbt.putString("uuid", uuid.toString());
        }
        
        @Override
        protected void loadExtra(CompoundTag nbt) {
            uuid = UUID.fromString(nbt.getString("uuid"));
        }
        
        @Override
        public Component print(Level level) {
            var player = level.getPlayerByUUID(uuid);
            if (player != null)
                return Component.translatable("scene.output.player_found", player.getDisplayName());
            return Component.translatable("scene.output.player", uuid.toString());
        }
        
    }
    
}
