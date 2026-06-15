package dev.kkazi.claudecraft.client.socketServer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kkazi.claudecraft.client.ClaudeCraftClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String jsonStr = frame.text();

        try {
            JsonNode root = MAPPER.readTree(jsonStr);
            String type = root.has("type") ? root.get("type").asText() : "";

            if ("tool_request".equals(type)) {
                String toolName = root.get("name").asText();
                JsonNode inputNode = root.get("input");

                // Netty runs on a separate thread! Hand execution over to Minecraft's client thread safely.
                Minecraft.getInstance().execute(() -> {
                    handleClientTool(toolName, inputNode);
                });

            } else if ("output".equals(type)) {
                String text = root.get("text").asText();
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§6[Claude]§r " + text));
                    }
                });
            } else if ("error".equals(type)) {
                String text = root.get("text").asText();
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.sendSystemMessage(
                                Component.literal("§c[Claude Error]§r " + text) // red prefix
                        );
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleClientTool(String name, JsonNode input) {
        ClientToolExecutor.dispatch(name, input);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        System.out.println("[WS] Rust connected: " + ctx.channel().remoteAddress());
        ClaudeCraftClient.wsServer.setRustChannel(ctx.channel());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        System.out.println("[WS] Rust disconnected");
        ClaudeCraftClient.wsServer.setRustChannel(null);
    }
}