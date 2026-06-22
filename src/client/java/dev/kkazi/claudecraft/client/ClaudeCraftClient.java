package dev.kkazi.claudecraft.client;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.kkazi.claudecraft.client.socketserver.NettyWebSocketServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minecraft.client.Minecraft;

public class ClaudeCraftClient implements ClientModInitializer {
    public static NettyWebSocketServer wsServer;
    public static boolean claudeMode = false;
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void onInitializeClient() {
        wsServer = new NettyWebSocketServer(8765);


        try {
            wsServer.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Stop the server cleanly when Minecraft shuts down
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            wsServer.stop();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("claude").executes(context -> {
                claudeMode = true;

                Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Claude Mode is enabled."));
                context.getSource().sendSuccess(() -> Component.literal("Claude Mode is enabled."), false);
                return 1;
            }));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("quit").executes(context -> {
                claudeMode = false;

                Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Claude Mode is disabled."));
                context.getSource().sendSuccess(() -> Component.literal("Claude Mode is disabled."), false);
                return 1;
            }));
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("exit").executes(context -> {
                claudeMode = false;

                Minecraft.getInstance().player.sendOverlayMessage(Component.literal("Closed Claude session"));
                context.getSource().sendSuccess(() -> Component.literal("Closed Claude session"), false);
                //TODO: complete code for exiting the Claude Chat
                /*
                try {
                    ObjectNode responseNode = MAPPER.createObjectNode();
                    responseNode.put("type", "tool_response");
                    responseNode.put("success", "");
                    responseNode.put("output", "");

                    String rawJson = MAPPER.writeValueAsString(responseNode);
                    wsServer.sendMessage(rawJson);
                } catch (Exception e) {
                    e.printStackTrace();
                }*/
                return 1;
            }));
        });

        // Intercept chat BEFORE it's sent to the server
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (!claudeMode) return true; // allow normally

            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message.toString()));
            // Send to Rust via WebSocket
            sendToRust(message);
            return false; // block from being sent to server
        });
    }

    private void sendToRust(String text) {
        try {
            ObjectNode msg = JSON.createObjectNode();
            msg.put("type", "input");
            msg.put("text", text);

            String jsonString = JSON.writeValueAsString(msg);
            ClaudeCraftClient.wsServer.sendMessage(jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
