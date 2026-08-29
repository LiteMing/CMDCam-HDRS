package team.creative.cmdcam.common.packet;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import team.creative.cmdcam.client.CMDCamClient;
import team.creative.creativecore.common.network.CreativePacket;

public class StopPathPacket extends CreativePacket {
    
    public StopPathPacket() {}
    
    @Override
    public void executeClient(Player player) {
        // stopServer() cancels any pending tracking start AND stops any actively playing
        // server-synced scene, so the guard must not be isPlaying()-only.
        CMDCamClient.stopServer();
    }

    
    @Override
    public void executeServer(ServerPlayer player) {}
    
}
