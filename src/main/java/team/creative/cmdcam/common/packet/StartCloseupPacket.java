package team.creative.cmdcam.common.packet;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import team.creative.cmdcam.CMDCam;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.cmdcam.common.scene.CamScene;
import team.creative.cmdcam.common.scene.run.CamStopReason;
import team.creative.creativecore.common.network.CreativePacket;
import team.creative.creativecore.common.util.registry.exception.RegistryException;

public class StartCloseupPacket extends CreativePacket {
    
    public CompoundTag sceneNbt;
    public UUID targetUuid;
    public long returnDuration;
    public double targetHeightFactor;
    public String modeId;
    
    public StartCloseupPacket() {}
    
    public StartCloseupPacket(CamScene scene, UUID targetUuid, long returnDuration, double targetHeightFactor, String modeId) {
        this.sceneNbt = scene.save(new CompoundTag());
        this.targetUuid = targetUuid;
        this.returnDuration = returnDuration;
        this.targetHeightFactor = targetHeightFactor;
        this.modeId = modeId != null ? modeId : "closeup";
    }
    
    @Override
    public void executeClient(Player player) {
        try {
            CamScene scene = new CamScene(sceneNbt);
            scene.setMode(modeId != null ? modeId : "closeup");
            scene.setServerSynced();
            scene.bindTracking(targetUuid, targetHeightFactor, returnDuration);
            CMDCamClient.startCloseup(scene);
        } catch (RegistryException | RuntimeException | LinkageError e) {
            CMDCam.LOGGER.error("Failed to start tracking camera for target {}, mode {}", targetUuid, modeId, e);
            CMDCamClient.finishImmediately(CamStopReason.INVALID_PACKET);
            if (player != null)
                player.sendSystemMessage(Component.translatable("scene.closeup.invalid_packet"));
        }
    }
    
    @Override
    public void executeServer(ServerPlayer player) {}
    
}
