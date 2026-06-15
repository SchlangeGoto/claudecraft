package dev.kkazi.claudecraft.client.socketServer;

import dev.kkazi.claudecraft.client.ClaudeCraftClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        //Logik Hier!!!!!
        String message = frame.text();
        ctx.channel().writeAndFlush(new TextWebSocketFrame("Echo: " + message));
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
