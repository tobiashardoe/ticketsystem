package app.controllers;

import app.dao.UserDAO;
import app.dao.TicketDAO;
import app.dao.CommentDAO;

import app.Main;
import app.routes.Routes;
import app.security.SecurityController;
import app.dtos.AbuseIpResponseDTO;
import app.entities.User;
import app.entities.Ticket;
import app.entities.Comment;
import app.entities.Attachment;
import app.services.AbuseIpService;
import io.javalin.Javalin;
import io.restassured.http.ContentType;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.Properties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Testcontainers
class TicketControllerTest {
    @Container
    private static PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("resolvedesk_ticket_api_test").withUsername("test").withPassword("test");
    private static EntityManagerFactory emf;
    private static Javalin app;
    private static RequestSpecification requestSpec;
    private static SecurityController security;
    private String token;
    private static UserDAO userDAO;
    private static TicketDAO ticketDAO;
    private static TestIpService ipService = new TestIpService();
    private User creator;

    @BeforeAll
    static void startServer() {
        Properties properties = new Properties();
        properties.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        properties.put("hibernate.connection.url", POSTGRES.getJdbcUrl());
        properties.put("hibernate.connection.username", POSTGRES.getUsername());
        properties.put("hibernate.connection.password", POSTGRES.getPassword());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        emf = new Configuration().setProperties(properties)
                .addAnnotatedClass(User.class).addAnnotatedClass(Ticket.class)
                .addAnnotatedClass(Comment.class).addAnnotatedClass(Attachment.class)
                .buildSessionFactory();
        userDAO = new UserDAO(emf);
        ticketDAO = new TicketDAO(emf);
        app = Javalin.create();
        Main.configureErrorHandling(app);
        security = new SecurityController(userDAO, "test-secret-only-not-for-production-123456789");
        Routes.addSecurityRoutes(app, security);
        TicketWebSocket notifications = new TicketWebSocket(security);
        notifications.addRoutes(app);
        TicketController tickets = new TicketController(ipService, ticketDAO, userDAO,
                new CommentDAO(emf));
        tickets.setNotifications(notifications);
        Routes.addTicketRoutes(app, tickets);
        app.start(0);
        requestSpec = new RequestSpecBuilder()
                .setBaseUri("http://localhost")
                .setPort(app.port())
                .setBasePath("/api")
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .build();
    }

    @BeforeEach
    void prepareData() {
        CommentDAO commentDAO = new CommentDAO(emf);
        for (Comment comment : commentDAO.findAll()) commentDAO.delete(comment.getId());
        for (Ticket ticket : ticketDAO.findAll()) {
            ticketDAO.delete(ticket.getId());
        }
        for (User user : userDAO.findAll()) {
            userDAO.delete(user.getId());
        }
        creator = new User("Creator", "creator@example.com", "USER");
        creator.setPassword("test-password");
        userDAO.save(creator);
        token = login("creator@example.com");
        ipService.score = 0;
        ipService.unavailable = false;
    }

    @AfterAll
    static void stopServer() {
        if (app != null) app.stop();
        if (emf != null) emf.close();
    }

    @Test
    @DisplayName("Given a USER and IP score 79, when creating a ticket, then return 201 and persist its owner")
    void acceptedIpShouldAllowTicketCreation() {
        ipService.score = 79;
        createTicket(201);
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/tickets")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].title", equalTo("Login problem"))
                .body("[0].createdBy.id", equalTo(creator.getId()));
    }

    @Test
    @DisplayName("Given IP score 80, when creating a ticket, then return 403 without saving")
    void highScoreShouldRejectTicketWithoutSaving() {
        ipService.score = 80;
        createTicket(403);
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/tickets")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("Given an unavailable IP check, when creating a ticket, then return 503 without saving")
    void unavailableIpCheckShouldNotSaveTicket() {
        ipService.unavailable = true;
        createTicket(503);
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/tickets")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    private void createTicket(int expectedStatus) {
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .queryParam("userId", 999999)
                .body("""
                        {"title":"Login problem","description":"Cannot log in",
                         "status":"OPEN","priority":"HIGH"}
                        """)
        .when()
                .post("/tickets")
        .then()
                .statusCode(expectedStatus)
                .contentType(ContentType.JSON);
    }

    @Test
    @DisplayName("Given another user's ticket, when a USER requests it, then deny access with 403")
    void userShouldNotReadAnotherUsersTicket() {
        User other = userDAO.save(new User("Other", "other@example.com", "USER"));
        Ticket ticket = ticketDAO.save(new Ticket("Private", "Other users ticket", "OPEN", "LOW", other));
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/tickets/" + ticket.getId())
        .then()
                .statusCode(403);
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
        .when()
                .get("/tickets")
        .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("Given a connected SUPPORT user, when a ticket is created, then send a notification")
    void supporterShouldReceiveNotificationWhenTicketIsCreated() throws Exception {
        User supporter = userDAO.save(new User("Support", "support@example.com", "SUPPORT"));
        SocketMessages listener = new SocketMessages();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + app.port() + "/ws/tickets"), listener)
                .get(5, TimeUnit.SECONDS);
        try {
            socket.sendText(security.createToken(supporter), true).get(5, TimeUnit.SECONDS);
            assertEquals("{\"type\":\"CONNECTED\"}", listener.messages.poll(5, TimeUnit.SECONDS));
            createTicket(201);
            String message = listener.messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message);
            var event = new ObjectMapper().readTree(message);
            assertEquals("TICKET_CREATED", event.get("type").asText());
            assertEquals(ticketDAO.findAll().get(0).getId(), event.get("ticketId").asInt());
            assertEquals(2, event.size());
        } finally {
            socket.abort();
        }
    }

    @Test
    @DisplayName("Given an invalid token, when subscribing, then close the connection with 1008")
    void invalidTokenShouldNotSubscribeToNotifications() throws Exception {
        SocketMessages listener = new SocketMessages();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + app.port() + "/ws/tickets"), listener)
                .get(5, TimeUnit.SECONDS);
        try {
            socket.sendText("invalid-token", true).get(5, TimeUnit.SECONDS);
            assertEquals(1008, listener.closed.get(5, TimeUnit.SECONDS));
            assertTrue(listener.messages.isEmpty());
        } finally {
            socket.abort();
        }
    }

    @Test
    @DisplayName("Given connected owners, when SUPPORT changes and replies, then notify only the relevant owner")
    void ownerShouldReceiveStaffChangesWithoutLeakingToOtherUsers() throws Exception {
        User supporter = userDAO.save(new User("Support", "support@example.com", "SUPPORT"));
        User other = userDAO.save(new User("Other", "other@example.com", "USER"));
        String supportToken = security.createToken(supporter);
        Ticket ownedTicket = ticketDAO.save(new Ticket("Mine", "My problem", "OPEN", "LOW", creator));
        Ticket otherTicket = ticketDAO.save(new Ticket("Other", "Other problem", "OPEN", "LOW", other));
        SocketMessages ownerMessages = new SocketMessages();
        SocketMessages otherMessages = new SocketMessages();
        WebSocket ownerSocket = openSocket(token, ownerMessages);
        WebSocket otherSocket = openSocket(security.createToken(other), otherMessages);
        try {
            given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + supportToken)
                    .contentType(ContentType.JSON)
                .body("""
                            {"title":"Mine","description":"My problem","status":"IN_PROGRESS","priority":"HIGH"}
                            """)
            .when()
                .put("/tickets/" + ownedTicket.getId())
            .then()
                .statusCode(200);
            assertEvent(ownerMessages, "TICKET_UPDATED", ownedTicket.getId());

            given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + supportToken)
                    .contentType(ContentType.JSON)
                .body("{\"message\":\"We are looking into it\"}")
            .when()
                .post("/tickets/" + ownedTicket.getId() + "/comments")
            .then()
                .statusCode(201);
            assertEvent(ownerMessages, "COMMENT_CREATED", ownedTicket.getId());

            // A later event for the other user proves earlier private events were not queued for them.
            given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + supportToken)
                    .contentType(ContentType.JSON)
                .body("{\"message\":\"Reply to other user\"}")
            .when()
                .post("/tickets/" + otherTicket.getId() + "/comments")
            .then()
                .statusCode(201);
            assertEvent(otherMessages, "COMMENT_CREATED", otherTicket.getId());
        } finally {
            ownerSocket.abort();
            otherSocket.abort();
        }
    }


    private String login(String email) {
        return given()
                .spec(requestSpec)
                .body(java.util.Map.of("email", email, "password", "test-password"))
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");
    }

    @Test
    @DisplayName("Given a USER, when assigning a ticket to SUPPORT, then return 403 without changing the ticket")
    void userShouldNotAssignTicket() {
        User supporter = new User("Support", "assign-support@example.com", "SUPPORT");
        supporter.setPassword("test-password");
        userDAO.save(supporter);
        Ticket ticket = ticketDAO.save(new Ticket("Help", "Need support", "OPEN", "LOW", creator));
        String actorToken = token;

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + actorToken)
                .pathParam("id", ticket.getId())
                .queryParam("supporterId", supporter.getId())
        .when()
                .put("/tickets/{id}/assign")
        .then()
                .statusCode(403);

        // Et separat GET kontrollerer den gemte tilstand gennem API'et.
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
                .pathParam("id", ticket.getId())
        .when()
                .get("/tickets/{id}")
        .then()
                .statusCode(200)
                .body("assignedTo", nullValue())
                .body("status", equalTo("OPEN"));
    }

    @Test
    @DisplayName("Given a SUPPORT, when assigning a ticket to SUPPORT, then return 200 and persist the assignment")
    void supportShouldAssignTicketToSupporter() {
        User supporter = new User("Support", "assign-support@example.com", "SUPPORT");
        supporter.setPassword("test-password");
        userDAO.save(supporter);
        Ticket ticket = ticketDAO.save(new Ticket("Help", "Need support", "OPEN", "LOW", creator));
        String actorToken = login(supporter.getEmail());

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + actorToken)
                .pathParam("id", ticket.getId())
                .queryParam("supporterId", supporter.getId())
        .when()
                .put("/tickets/{id}/assign")
        .then()
                .statusCode(200)
                .body("assignedTo.id", equalTo(supporter.getId()))
                .body("status", equalTo("IN_PROGRESS"));

        // Et separat GET kontrollerer den gemte tilstand gennem API'et.
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
                .pathParam("id", ticket.getId())
        .when()
                .get("/tickets/{id}")
        .then()
                .statusCode(200)
                .body("assignedTo.id", equalTo(supporter.getId()))
                .body("status", equalTo("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Given a ADMIN, when assigning a ticket to SUPPORT, then return 200 and persist the assignment")
    void adminShouldAssignTicketToSupporter() {
        User supporter = new User("Support", "assign-support@example.com", "SUPPORT");
        supporter.setPassword("test-password");
        userDAO.save(supporter);
        User admin = new User("Admin", "admin@example.com", "ADMIN");
        admin.setPassword("test-password");
        userDAO.save(admin);
        Ticket ticket = ticketDAO.save(new Ticket("Help", "Need support", "OPEN", "LOW", creator));
        String actorToken = login(admin.getEmail());

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + actorToken)
                .pathParam("id", ticket.getId())
                .queryParam("supporterId", supporter.getId())
        .when()
                .put("/tickets/{id}/assign")
        .then()
                .statusCode(200)
                .body("assignedTo.id", equalTo(supporter.getId()))
                .body("status", equalTo("IN_PROGRESS"));

        // Et separat GET kontrollerer den gemte tilstand gennem API'et.
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
                .pathParam("id", ticket.getId())
        .when()
                .get("/tickets/{id}")
        .then()
                .statusCode(200)
                .body("assignedTo.id", equalTo(supporter.getId()))
                .body("status", equalTo("IN_PROGRESS"));
    }

    private WebSocket openSocket(String loginToken, SocketMessages messages) throws Exception {
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + app.port() + "/ws/tickets"), messages)
                .get(5, TimeUnit.SECONDS);
        socket.sendText(loginToken, true).get(5, TimeUnit.SECONDS);
        assertEquals("{\"type\":\"CONNECTED\"}", messages.messages.poll(5, TimeUnit.SECONDS));
        return socket;
    }

    private void assertEvent(SocketMessages messages, String type, int ticketId) throws Exception {
        String message = messages.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(message);
        var event = new ObjectMapper().readTree(message);
        assertEquals(type, event.get("type").asText());
        assertEquals(ticketId, event.get("ticketId").asInt());
        assertEquals(2, event.size());
    }

    private static class SocketMessages implements WebSocket.Listener {
        private BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private CompletableFuture<Integer> closed = new CompletableFuture<>();
        private StringBuilder text = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            text.append(data);
            if (last) {
                messages.add(text.toString());
                text.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            closed.complete(status);
            return null;
        }
    }

    // A simple subclass supplies a known result instead of calling the external API.
    private static class TestIpService extends AbuseIpService {
        private int score;
        private boolean unavailable;

        TestIpService() {
            super("test-key");
        }

        @Override
        public AbuseIpResponseDTO checkIp(String ipAddress) {
            if (unavailable) throw new IllegalStateException("Simulated API failure");
            AbuseIpResponseDTO result = new AbuseIpResponseDTO();
            AbuseIpResponseDTO.Data data = new AbuseIpResponseDTO.Data();
            data.setAbuseConfidenceScore(score);
            result.setData(data);
            return result;
        }
    }
}
