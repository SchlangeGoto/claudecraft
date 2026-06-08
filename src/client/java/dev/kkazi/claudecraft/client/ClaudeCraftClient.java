package dev.kkazi.claudecraft.client;

import dev.kkazi.claudecraft.client.socketServer.NettyWebSocketServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class ClaudeCraftClient implements ClientModInitializer {
    public static NettyWebSocketServer wsServer;

    @Override
    public void onInitializeClient() {
        wsServer = new NettyWebSocketServer(8887);

        try {
            wsServer.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Stop the server cleanly when Minecraft shuts down
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            wsServer.stop();
        });
    }
}
