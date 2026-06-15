package dev.kkazi.claudecraft.client;

import com.google.gson.*;
import dev.kkazi.claudecraft.client.socketServer.NettyWebSocketServer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ClaudeCraftClient implements ClientModInitializer {
    public static NettyWebSocketServer wsServer;
    public static boolean claudeMode = false;

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

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("claude").executes(context -> {
                claudeMode = true;

                if (claudeMode) {
                    context.getSource().sendSuccess(() -> Component.literal("Claude Mode is enabled."), false);
                    return 1;
                } else {
                    context.getSource().sendSuccess(() -> Component.literal("Claude Mode failed"), false);
                    return -1;
                }
            }));
        });

        // Intercept chat messages
        ServerTickEvents.END_SERVER_TICK.register(server -> {}); // keeps server reference alive

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {});

        // This fires before the message is sent to chat
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (claudeMode) {
                String text = message.decoratedContent().getString();
                String playerName = sender.getName().getString();

                if (text.equalsIgnoreCase("quit")) {
                    claudeMode = false;
                    sender.sendSystemMessage(Component.literal("Claude mode OFF"));
                    return false; // block "quit" from appearing in chat
                }

                writeToJson(playerName, text);
                return false; // blocks the message from appearing in chat
            }
            return true; // allow normally
        });
    }

    private void writeToJson(String player, String message) {
        try {
            // Read existing file or start fresh
            File file = new File("claude_chat.json");
            JsonArray array = new JsonArray();

            if (file.exists()) {
                JsonElement existing = JsonParser.parseReader(new FileReader(file));
                if (existing.isJsonArray()) {
                    array = existing.getAsJsonArray();
                }
            }

            // Add new entry
            JsonObject entry = new JsonObject();
            entry.addProperty("player", player);
            entry.addProperty("message", message);
            entry.addProperty("timestamp", System.currentTimeMillis());
            array.add(entry);

            // Write back to file
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(array));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
