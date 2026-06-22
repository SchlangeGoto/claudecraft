package dev.kkazi.claudecraft.client.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.kkazi.claudecraft.client.ClaudeCraftClient;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                handleBatchBlocks(input);
            } else if ("scan_region".equals(toolName)) {
                handleScanRegion(input);
            } else {
                sendResponse(false, "Unknown client tool requested: " + toolName);
            }
        } catch (Exception e) {
            sendResponse(false, "Client failed execution: " + e.getMessage());
        }
    }

    public static void handleScanRegion(JsonNode input) {
        Minecraft client = Minecraft.getInstance();
        try {
            int minX = input.get("x1").asInt(), minY = input.get("y1").asInt(), minZ = input.get("z1").asInt();
            int maxX = input.get("x2").asInt(), maxY = input.get("y2").asInt(), maxZ = input.get("z2").asInt();

            // Now returns structured JSON matching batch_blocks syntax natively
            String result = MinecraftDSL.encodeScan(client.level, minX, minY, minZ, maxX, maxY, maxZ);
            sendResponse(true, result);
        } catch (Exception e) {
            sendResponse(false, "scan_region error: " + e.getMessage());
        }
    }

    public static void handleBatchBlocks(JsonNode input) {
        Minecraft client = Minecraft.getInstance();
        try {
            // Read origin array [x, y, z] from the Tool Call schema
            int[] origin = new int[]{0, 0, 0};
            JsonNode originNode = input.get("origin");
            if (originNode != null && originNode.isArray() && originNode.size() == 3) {
                origin[0] = originNode.get(0).asInt();
                origin[1] = originNode.get(1).asInt();
                origin[2] = originNode.get(2).asInt();
            }

            // Read blocks array ["...", "..."] from the Tool Call schema
            List<String> blocks = new ArrayList<>();
            JsonNode blocksNode = input.get("blocks");
            if (blocksNode != null && blocksNode.isArray()) {
                for (JsonNode element : blocksNode) {
                    blocks.add(element.asText());
                }
            }

            List<MinecraftDSL.SetBlockCommand> commands = MinecraftDSL.parseDSL(origin, blocks);
            for (MinecraftDSL.SetBlockCommand cmd : commands) {
                StringBuilder sb = new StringBuilder();
                sb.append("setblock ").append(cmd.x).append(" ").append(cmd.y).append(" ").append(cmd.z)
                        .append(" ").append(cmd.blockType);
                if (!cmd.properties.isEmpty()) {
                    sb.append("[");
                    boolean first = true;
                    for (Map.Entry<String, String> e : cmd.properties.entrySet()) {
                        if (!first) sb.append(",");
                        sb.append(e.getKey()).append("=").append(e.getValue());
                        first = false;
                    }
                    sb.append("]");
                    sb.append(" replace");
                }
                client.player.connection.sendCommand(sb.toString());
            }
            sendResponse(true, "Placed " + commands.size() + " blocks");
        } catch (Exception e) {
            sendResponse(false, "batch_blocks error: " + e.getMessage());
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