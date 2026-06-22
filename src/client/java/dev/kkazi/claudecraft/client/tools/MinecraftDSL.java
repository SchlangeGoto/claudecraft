package dev.kkazi.claudecraft.client.tools;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

public class MinecraftDSL {

    public static String encodeScan(ClientLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        // key = "blockname;prop=val,prop=val" (or just "blockname")
        // value = list of positions that need placing
        Map<String, List<int[]>> blockGroups = new LinkedHashMap<>();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;

                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    String props = encodeProperties(state);
                    String key = props.isEmpty() ? name : name + ";" + props;

                    blockGroups.computeIfAbsent(key, k -> new ArrayList<>())
                            .add(new int[]{x - minX, y - minY, z - minZ});
                }
            }
        }

        // Format the output exactly like the batch_blocks input schema expects it
        StringBuilder sb = new StringBuilder();
        sb.append("{\"origin\":[").append(minX).append(",").append(minY).append(",").append(minZ).append("],\"blocks\":[");

        boolean firstBlock = true;
        for (Map.Entry<String, List<int[]>> entry : blockGroups.entrySet()) {
            if (!firstBlock) sb.append(",");
            String key = entry.getKey();
            List<int[]> positions = entry.getValue();

            // Greedy fill: find largest axis-aligned boxes
            List<String> encoded = greedyEncode(positions);

            sb.append("\"").append(key).append(":").append(String.join("|", encoded)).append("\"");
            firstBlock = false;
        }
        sb.append("]}");

        return sb.toString();
    }

    // Greedy encode: find FILL regions first, remainder as singles
    private static List<String> greedyEncode(List<int[]> positions) {
        Set<String> remaining = new LinkedHashSet<>();
        for (int[] p : positions) remaining.add(key(p[0], p[1], p[2]));

        List<String> result = new ArrayList<>();

        for (int[] origin : positions) {
            if (!remaining.contains(key(origin[0], origin[1], origin[2]))) continue;

            // Expand along X
            int ex = origin[0];
            while (remaining.contains(key(ex + 1, origin[1], origin[2]))) ex++;

            // Expand along Z
            int ez = origin[2];
            outer_z:
            while (true) {
                for (int x = origin[0]; x <= ex; x++) {
                    if (!remaining.contains(key(x, origin[1], ez + 1))) break outer_z;
                }
                ez++;
            }

            // Expand along Y
            int ey = origin[1];
            outer_y:
            while (true) {
                for (int x = origin[0]; x <= ex; x++) {
                    for (int z = origin[2]; z <= ez; z++) {
                        if (!remaining.contains(key(x, ey + 1, z))) break outer_y;
                    }
                }
                ey++;
            }

            // Remove all consumed positions
            for (int x = origin[0]; x <= ex; x++)
                for (int y = origin[1]; y <= ey; y++)
                    for (int z = origin[2]; z <= ez; z++)
                        remaining.remove(key(x, y, z));

            boolean isSingle = ex == origin[0] && ey == origin[1] && ez == origin[2];
            if (isSingle) {
                result.add(origin[0] + "," + origin[1] + "," + origin[2]);
            } else {
                result.add(origin[0] + "," + origin[1] + "," + origin[2]
                        + ":" + ex + "," + ey + "," + ez);
            }
        }

        return result;
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private static String encodeProperties(BlockState state) {
        StringBuilder props = new StringBuilder();
        for (Property<?> prop : state.getProperties()) {
            String k = prop.getName();
            String v = state.getValue(prop).toString().toLowerCase();
            if (isDefaultProperty(k, v)) continue;
            if (props.length() > 0) props.append(",");
            props.append(k).append("=").append(v);
        }
        return props.toString();
    }

    private static boolean isDefaultProperty(String key, String val) {
        return switch (key) {
            case "waterlogged" -> val.equals("false");
            case "powered" -> val.equals("false");
            case "lit" -> val.equals("false");
            case "open" -> val.equals("false");
            case "triggered" -> val.equals("false");
            case "enabled" -> val.equals("true");
            case "persistent" -> val.equals("true");
            case "distance" -> val.equals("7");
            case "shape" -> val.equals("straight");
            case "half" -> val.equals("bottom");
            case "type" -> val.equals("bottom");
            default -> false;
        };
    }

    public static class SetBlockCommand {
        public final int x, y, z;
        public final String blockType;
        public final Map<String, String> properties;

        public SetBlockCommand(int x, int y, int z, String blockType, Map<String, String> properties) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockType = blockType;
            this.properties = properties;
        }
    }

    // Overloaded parseDSL to accept structured data from the tool call directly
    public static List<SetBlockCommand> parseDSL(int[] origin, List<String> blocks) {
        List<SetBlockCommand> commands = new ArrayList<>();

        for (String line : blocks) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Split block key from positions: "blockname;props:positions"
            int firstColon = line.indexOf(':');
            if (firstColon < 0) continue;

            String blockKey = line.substring(0, firstColon);
            String positionsPart = line.substring(firstColon + 1);

            String blockName;
            Map<String, String> props = new HashMap<>();

            int semiIdx = blockKey.indexOf(';');
            if (semiIdx >= 0) {
                blockName = "minecraft:" + blockKey.substring(0, semiIdx);
                for (String kv : blockKey.substring(semiIdx + 1).split(",")) {
                    String[] parts = kv.split("=", 2);
                    if (parts.length == 2) props.put(parts[0].trim(), parts[1].trim());
                }
            } else {
                blockName = "minecraft:" + blockKey;
            }

            // Parse positions — split by |, each is either x,y,z or x1,y1,z1:x2,y2,z2
            for (String posEntry : positionsPart.split("\\|")) {
                posEntry = posEntry.trim();

                if (posEntry.contains(":")) {
                    // Fill region: x1,y1,z1:x2,y2,z2
                    String[] halves = posEntry.split(":", 2);
                    int[] p1 = parseXYZ(halves[0]);
                    int[] p2 = parseXYZ(halves[1]);
                    if (p1 == null || p2 == null) continue;

                    for (int x = Math.min(p1[0], p2[0]); x <= Math.max(p1[0], p2[0]); x++)
                        for (int y = Math.min(p1[1], p2[1]); y <= Math.max(p1[1], p2[1]); y++)
                            for (int z = Math.min(p1[2], p2[2]); z <= Math.max(p1[2], p2[2]); z++)
                                commands.add(new SetBlockCommand(
                                        origin[0] + x, origin[1] + y, origin[2] + z,
                                        blockName, props));
                } else {
                    // Single block: x,y,z
                    int[] p = parseXYZ(posEntry);
                    if (p == null) continue;
                    commands.add(new SetBlockCommand(
                            origin[0] + p[0], origin[1] + p[1], origin[2] + p[2],
                            blockName, props));
                }
            }
        }

        return commands;
    }

    // Kept legacy string parser fallback intact just in case
    public static List<SetBlockCommand> parseDSL(String input) {
        List<SetBlockCommand> commands = new ArrayList<>();
        int[] origin = {0, 0, 0};
        List<String> blockLines = new ArrayList<>();

        for (String raw : input.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("REL:")) {
                int[] parsed = parseXYZ(line.substring(4));
                if (parsed != null) origin = parsed;
                continue;
            }
            blockLines.add(line);
        }
        return parseDSL(origin, blockLines);
    }

    private static int[] parseXYZ(String s) {
        String[] parts = s.trim().split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}