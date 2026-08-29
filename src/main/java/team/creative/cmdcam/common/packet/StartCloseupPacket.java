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
import team.creative.cmdcam.common.scene.tracking.CamPreset;
import team.creative.cmdcam.common.scene.tracking.TrackingOptions;
import team.creative.creativecore.common.network.CreativePacket;
import team.creative.creativecore.common.util.registry.exception.RegistryException;

public class StartCloseupPacket extends CreativePacket {
    
    public CompoundTag sceneNbt;
    public UUID targetUuid;
    /** id of the camera mode, kept as a plain string so the dedicated server never has to touch a camera mode class */
    public String modeId;
    public CompoundTag optionsNbt;
    
    public StartCloseupPacket() {}
    
    public StartCloseupPacket(CamScene scene, UUID targetUuid, String modeId, TrackingOptions options) {
        this.sceneNbt = scene.save(new CompoundTag());
        this.targetUuid = targetUuid;
        this.modeId = modeId != null ? modeId : CamPreset.CLOSEUP;
        this.optionsNbt = (options != null ? options : new TrackingOptions(this.modeId)).save(new CompoundTag());
    }
    
    @Override
    public void executeClient(Player player) {
        try {
            CamScene scene = new CamScene(sceneNbt);
            TrackingOptions options = TrackingOptions.load(optionsNbt);
            options.modeId = modeId != null ? modeId : CamPreset.CLOSEUP;
            scene.setMode(options.modeId);
            scene.setServerSynced();
            scene.bindTracking(targetUuid, options);
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
