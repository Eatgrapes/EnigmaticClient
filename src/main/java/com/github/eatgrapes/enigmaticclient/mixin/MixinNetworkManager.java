package com.github.eatgrapes.enigmaticclient.mixin;

import com.github.eatgrapes.enigmaticclient.EnigmaticClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C01PacketChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public abstract class MixinNetworkManager {

    @Inject(
        method = "sendPacket(Lnet/minecraft/network/Packet;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof C01PacketChatMessage) {
            C01PacketChatMessage chatPacket = (C01PacketChatMessage) packet;
            String message = chatPacket.getMessage();
            
            System.out.println("[Enigmatic] Command check: " + message);
            
            if (message.toLowerCase().startsWith(".eni")) {
                EnigmaticClient.handleCommand(message);
                ci.cancel();
                System.out.println("[Enigmatic] Blocked command packet");
            }
        }
    }
}