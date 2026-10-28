package app.controllers;

import app.entities.User;
import app.entities.Ticket;
import app.security.SecurityController;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TicketWebSocket {
    private SecurityController security;
    private Map<Session, String> clients = new ConcurrentHashMap<>();
    private ObjectMapper mapper = new ObjectMapper();
    private Logger logger = LoggerFactory.getLogger(TicketWebSocket.class);

    public TicketWebSocket(SecurityController security) {
        this.security = security;
    }

    public void addRoutes(Javalin app) {
        app.ws("/ws/tickets", ws -> {
            // The first message must contain a valid JWT. No token in the URL.
            ws.onMessage(ctx -> {
                try {
                    String token = ctx.message();
                    security.verifyToken(token);
                    synchronized (ctx.session) {
                        clients.put(ctx.session, token);
                        ctx.session.getRemote().sendString("{\"type\":\"CONNECTED\"}");
                    }
                } catch (Exception e) {
                    clients.remove(ctx.session);
                    ctx.session.close(1008, "Valid login token required");
                }
            });
            ws.onClose(ctx -> clients.remove(ctx.session));
            ws.onError(ctx -> clients.remove(ctx.session));
        });
    }

    public void ticketCreated(int ticketId) {
        publish("TICKET_CREATED", ticketId, null);
    }

    public void ticketChanged(Ticket ticket, String eventType) {
        Integer ownerId = ticket.getCreatedBy() == null ? null : ticket.getCreatedBy().getId();
        publish(eventType, ticket.getId(), ownerId);
    }

    private void publish(String eventType, int ticketId, Integer ownerId) {
        for (Map.Entry<Session, String> client : clients.entrySet()) {
            Session session = client.getKey();
            try {
                // Check expiry and the user's current role before each notification.
                User user = security.verifyToken(client.getValue());
                boolean staff = "SUPPORT".equals(user.getRole()) || "ADMIN".equals(user.getRole());
                boolean owner = ownerId != null && user.getId() == ownerId;
                if (!staff && !owner) continue;
                String message = mapper.writeValueAsString(
                        Map.of("type", eventType, "ticketId", ticketId));
                synchronized (session) {
                    session.getRemote().sendString(message);
                }
            } catch (Exception e) {
                clients.remove(session);
                session.close(1008, "Notification connection closed");
                logger.debug("Removed unavailable or unauthorized WebSocket client");
            }
        }
    }

}
