package dev.kkazi.claudecraft.client.socketServer;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.InetSocketAddress;
import java.util.Map;

    public class ClawBridgeServer extends WebSocketServer {

        private static final ObjectMapper JSON = new ObjectMapper();
        private WebSocket rustConn;

        public ClawBridgeServer(int port) {
            super(new InetSocketAddress(port));
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            this.rustConn = conn;
            System.out.println("[bridge] Rust connected");
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            try {
                Map<?,?> msg = JSON.readValue(message, Map.class);
                String type = (String) msg.get("type");
                switch (type) {
                    case "output"     -> handleOutput((String) msg.get("text"));
                    case "error"      -> handleError((String) msg.get("text"));
                    case "prompt"     -> handlePrompt((String) msg.get("text"), conn);
                    case "tool_start" -> handleToolStart((String) msg.get("name"), (String) msg.get("input"));
                    case "tool_end"   -> handleToolEnd((String) msg.get("name"), (String) msg.get("output"),
                            Boolean.TRUE.equals(msg.get("is_error")));
                    case "thinking"   -> handleThinking((String) msg.get("text"));
                    case "done"       -> handleDone();
                    case "status"     -> handleStatus(msg.get("data"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Send user input to Rust
        public void sendInput(String text) {
            if (rustConn != null && rustConn.isOpen()) {
                ObjectNode msg = JSON.createObjectNode();
                msg.put("type", "input");
                msg.put("text", text);
                rustConn.send(msg.toString());
            }
        }

        // Approve a pending permission prompt
        public void sendApprove() {
            ObjectNode msg = JSON.createObjectNode();
            msg.put("type", "approve");
            rustConn.send(msg.toString());
        }

        // Deny a pending permission prompt
        public void sendDeny(String reason) {
            ObjectNode msg = JSON.createObjectNode();
            msg.put("type", "deny");
            msg.put("reason", reason);
            rustConn.send(msg.toString());
        }

        // Tell Rust to exit cleanly
        public void sendQuit() {
            ObjectNode msg = JSON.createObjectNode();
            msg.put("type", "quit");
            rustConn.send(msg.toString());
        }

        // ── implement these to connect to your UI ──────────────────────────────
        private void handleOutput(String text)  { /* update your chat UI */ }
        private void handleError(String text)   { /* show error in UI */ }
        private void handlePrompt(String q, WebSocket conn) {
            // Show approval dialog to user, then call sendApprove() or sendDeny()
        }
        private void handleToolStart(String name, String input) { /* show tool spinner */ }
        private void handleToolEnd(String name, String output, boolean isError) { /* hide spinner, show result */ }
        private void handleThinking(String text) { /* optional: show thinking indicator */ }
        private void handleDone()               { /* turn complete — enable input field */ }
        private void handleStatus(Object data)  { /* parse and display status JSON */ }

        @Override public void onClose(WebSocket c, int code, String reason, boolean remote) {}
        @Override public void onError(WebSocket c, Exception e) { e.printStackTrace(); }
        @Override public void onStart() { System.out.println("[bridge] listening"); }

        // Launch sequence:
        //   1. new ClawBridgeServer(8765).start()
        //   2. ProcessBuilder to start the Rust binary with env CLAW_WS_URL=ws://127.0.0.1:8765
        //   3. Wait for onOpen callback — then you can send inputs
    }
}
