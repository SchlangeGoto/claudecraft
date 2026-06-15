package dev.kkazi.claudecraft.client.socketServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.kkazi.claudecraft.client.ClaudeCraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class ClientToolExecutor {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void dispatch(String toolName, JsonNode input) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            sendResponse(false, "Error: Player is not fully spawned into a world.");
            return;
        }

        try {
            if ("set_block".equals(toolName)) {
                int x = input.get("x").asInt();
                int y = input.get("y").asInt();
                int z = input.get("z").asInt();
                String blockType = input.get("block_type").asText();

                // Format the command string out loud exactly like a player manual input
                String command = String.format("setblock %d %d %d %s", x, y, z, blockType);

                // Force client connection to execute the command string to server
                client.player.connection.sendCommand(command);

                sendResponse(true, "Dispatched setblock command successfully.");

            } else if ("scan_region".equals(toolName)) {
                // Read from local client-cached blocks directly
                int minX = input.get("min_x").asInt();
                int minY = input.get("min_y").asInt();
                int minZ = input.get("min_z").asInt();
                int maxX = input.get("max_x").asInt();
                int maxY = input.get("max_y").asInt();
                int maxZ = input.get("max_z").asInt();

                ClientLevel level = client.level;
                StringBuilder worldData = new StringBuilder();

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            String blockName = state.getBlock().toString(); // e.g. "Block{minecraft:stone}"

                            // Clean up name string formatting
                            blockName = blockName.replace("Block{", "").replace("}", "");

                            if (!blockName.equals("minecraft:air")) {
                                worldData.append(String.format("[%d,%d,%d -> %s] ", x, y, z, blockName));
                            }
                        }
                    }
                }

                String outputText = worldData.length() == 0 ? "Region contains only air." : worldData.toString();
                sendResponse(true, outputText);

            } else {
                sendResponse(false, "Unknown client tool requested: " + toolName);
            }
        } catch (Exception e) {
            sendResponse(false, "Client failed execution: " + e.getMessage());
        }
    }

    private static void sendResponse(boolean success, String message) {
        try {
            ObjectNode responseNode = MAPPER.createObjectNode();
            responseNode.put("type", "tool_response");
            responseNode.put("success", success);
            responseNode.put("output", message);

            String rawJson = MAPPER.writeValueAsString(responseNode);
            ClaudeCraftClient.wsServer.sendMessage(rawJson);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}