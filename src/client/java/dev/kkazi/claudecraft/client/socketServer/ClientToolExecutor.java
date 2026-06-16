package dev.kkazi.claudecraft.client.socketServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.kkazi.claudecraft.client.ClaudeCraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
            if ("batch_blocks".equals(toolName)) {
                int x = 0, y = 0, z = 0;
                //get the origin and set x,y,z to it
                JsonNode originNode = input.get("origin");
                if (originNode != null && originNode.isArray() && originNode.size() == 3) {
                    x = originNode.get(0).asInt();
                    y = originNode.get(1).asInt();
                    z = originNode.get(2).asInt();
                }
                JsonNode blocksNode = input.get("blocks");
                if (blocksNode == null || !blocksNode.isArray()) {
                    sendResponse(false, "Error: Blocks are not in a list.");
                    return;
                }

                for (JsonNode blockNode : blocksNode) {
                    String block = blockNode.asText();
                    if (block.startsWith("FILL")) {
                        String blockName = "minecraft:" + block.substring(5, block.indexOf(':'));
                        block = block.substring(block.indexOf(':') + 1);
                        String[] parts = block.split("[,:;]");
                        int x1 = x + Integer.parseInt(parts[0]);
                        int y1 = y + Integer.parseInt(parts[1]);
                        int z1 = z + Integer.parseInt(parts[2]);
                        int x2 = x + Integer.parseInt(parts[3]);
                        int y2 = y + Integer.parseInt(parts[4]);
                        int z2 = z + Integer.parseInt(parts[5]);
                        String props = "[";
                        if (parts.length > 6){
                            for (int i = 0; i < parts.length-6; i++) {
                                props += parts[i+6];
                                if(i+7 != parts.length) props += ",";
                            }
                        }
                        props += "]";
                        String command = String.format("fill %d %d %d %d %d %d %s%s", x1, y1, z1, x2, y2, z2, blockName, props);

                        client.player.connection.sendCommand(command);
                    } else {
                        String[] parts = block.split("[,:;]");
                        String blockName = "minecraft:" + parts[0];
                        int x1 = x + Integer.parseInt(parts[1]);
                        int y1 = y + Integer.parseInt(parts[2]);
                        int z1 = z + Integer.parseInt(parts[3]);
                        String props = "[";
                        if (parts.length > 4){
                            for (int i = 0; i < parts.length-4; i++) {
                                props += parts[i+4];
                                if(i+5!= parts.length) props += ",";
                            }
                        }
                        props += "]";
                        String command = String.format("setblock %d %d %d %s%s", x1, y1, z1, blockName, props);

                        client.player.connection.sendCommand(command);
                    }
                }

                sendResponse(true, "Dispatched setblock command successfully.");

            } else if ("scan_region".equals(toolName)) {
                int minX = 0;
                int minY = 0;
                int minZ = 0;
                int maxX = 0;
                int maxY = 0;
                int maxZ = 0;

                // Read from local client-cached blocks directly
                try {
                    minX = input.get("x1").asInt();
                    minY = input.get("y1").asInt();
                    minZ = input.get("z1").asInt();
                    maxX = input.get("x2").asInt();
                    maxY = input.get("y2").asInt();
                    maxZ = input.get("z2").asInt();
                } catch (Exception e) {
                    Minecraft.getInstance().player.sendSystemMessage(Component.literal("Error when reading the scan_region specfications with the message: " + e.getMessage()));
                    throw new Exception("Error when reading the scan_region specfications with the message: " + e.getMessage(), e);
                }

                ClientLevel level = client.level;
                StringBuilder worldData = new StringBuilder();

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            BlockPos pos = new BlockPos(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            String blockName = state.getBlock().toString().substring(10); // removes the "minecraft:" parte.g. "Block{stone}"

                            // Clean up name string formatting
                            blockName = blockName.replace("Block{", "").replace("}", "");

                            if (!blockName.equals("air")) {
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