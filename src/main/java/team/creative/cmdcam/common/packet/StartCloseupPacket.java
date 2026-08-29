package team.creative.cmdcam.common.packet;

import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamStopReason;
import team.creative.creativecore.common.network.CreativePacket;
import team.creative.creativecore.common.util.registry.exception.RegistryException;

public class StartCloseupPacket extends CreativePacket {
    
    private static final Logger LOGGER = LogManager.getLogger("cmdcam");
    
    public CompoundTag sceneNbt;
    public UUID targetUuid;
    public long returnDuration;
    public double targetHeightFactor;
    
    public StartCloseupPacket() {}
    
    public StartCloseupPacket(CamScene scene, UUID targetUuid, long returnDuration, double targetHeightFactor) {
        this.sceneNbt = scene.save(new CompoundTag());
        this.targetUuid = targetUuid;
        this.returnDuration = returnDuration;
        this.targetHeightFactor = targetHeightFactor;
    }
    
    @Override
    public void executeClient(Player player) {
        try {
            CamScene scene = new CamScene(sceneNbt);
            scene.setServerSynced();
            scene.bindTracking(targetUuid, targetHeightFactor, returnDuration);
            CMDCamClient.startCloseup(scene);
        } catch (Throwable e) {
            LOGGER.error("Failed to start tracking camera for target {}", targetUuid, e);
            CMDCamClient.finishImmediately(CamStopReason.INVALID_PACKET);
            if (player != null)
                player.sendSystemMessage(Component.translatable("scene.closeup.invalid_packet"));
        }
    }
    
    @Override
    public void executeServer(ServerPlayer player) {}
    
}
