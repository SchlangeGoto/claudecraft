package dev.kkazi.claudecraft.client.socketServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class NettyWebSocketServer {

    private final int port;
    private Channel serverChannel;           // represents the server's open port
    private EventLoopGroup bossGroup;        // thread that accepts new connections
    private EventLoopGroup workerGroup;      // threads that handle connected clients

    public NettyWebSocketServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);   // 1 thread is enough for accepting
        workerGroup = new NioEventLoopGroup();  // defaults to CPU core count

        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                .addLast(new HttpServerCodec())
                                .addLast(new HttpObjectAggregator(65536))
                                .addLast(new WebSocketServerProtocolHandler("/"))
                                .addLast(new WebSocketFrameHandler());
                    }
                });

        // bind to the port and start listening
        // .sync() blocks until the port is successfully opened
        serverChannel = bootstrap.bind(port).sync().channel();
        System.out.println("[WS] WebSocket server started on port " + port);
    }

    public void stop() {
        if (serverChannel != null) {
            serverChannel.close();  // closes the port, stops accepting new connections
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();   // shuts down the accept thread
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(); // shuts down the client handler threads
        }
        System.out.println("[WS] WebSocket server stopped");
    }

    // Call this from WebSocketFrameHandler when a client connects
    public void setRustChannel(Channel channel) {
        this.serverChannel = channel;
    }

    public void sendMessage(String json) {
        if (serverChannel != null && serverChannel.isOpen()) {
            serverChannel.writeAndFlush(new TextWebSocketFrame(json));
        }
    }
}