package dev.kkazi.claudecraft.client.socketServer;

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
        System.out.println("[WS] Client connected: " + ctx.channel().remoteAddress());
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        System.out.println("[WS] Client disconnected");
    }
}
