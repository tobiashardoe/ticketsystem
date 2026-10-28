package app;

import app.routes.Routes;

import app.controllers.IpLookupController;
import app.controllers.TicketController;
import app.controllers.TicketWebSocket;
import app.controllers.UserController;
import app.services.AbuseIpService;
import app.security.SecurityController;
import app.dao.UserDAO;
import app.config.HibernateConfig;
import app.utils.Utils;
import io.javalin.Javalin;
import app.exceptions.ApiException;
import io.javalin.http.HttpResponseException;
import io.javalin.validation.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class Main {
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Javalin app = Javalin.create();
        configureErrorHandling(app);
        UserDAO userDAO = new UserDAO(HibernateConfig.getEntityManagerFactory());
        String jwtSecret = System.getenv("JWT_SECRET");
        if ((jwtSecret == null || jwtSecret.isBlank()) && System.getenv("DEPLOYED") == null) {
            jwtSecret = Utils.getPropertyValue("JWT_SECRET", "config.properties");
        }
        SecurityController security = new SecurityController(userDAO, jwtSecret);
        createInitialAdmin(userDAO);
        Routes.addSecurityRoutes(app, security);

        Routes.addUserRoutes(app, new UserController(userDAO));

        String apiKey = System.getenv("ABUSEIPDB_API_KEY");
        AbuseIpService abuseIpService = new AbuseIpService(apiKey);
        TicketWebSocket notifications = new TicketWebSocket(security);
        notifications.addRoutes(app);
        TicketController tickets = new TicketController(abuseIpService);
        tickets.setNotifications(notifications);
        Routes.addTicketRoutes(app, tickets);
        Routes.addIpRoutes(app, new IpLookupController(abuseIpService));

        app.start(7070);
    }

    public static void configureErrorHandling(Javalin app) {
        app.exception(ApiException.class, (e, ctx) ->
                ctx.status(e.getCode()).json(Map.of("message", e.getMessage())));
        app.exception(ValidationException.class, (e, ctx) ->
                ctx.status(400).json(Map.of("message", "Invalid request input", "errors", e.getErrors())));
        app.exception(HttpResponseException.class, (e, ctx) ->
                ctx.status(e.getStatus()).json(Map.of("message", e.getMessage())));
        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Unexpected API error", e);
            ctx.status(500).json(Map.of("message", "Internal server error"));
        });
        app.before(ctx -> logger.info("{} {}", ctx.method(), ctx.path()));

    }

    private static void createInitialAdmin(UserDAO userDAO) {
        String email = System.getenv("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");
        if (email == null && password == null) return;
        if (email == null || email.isBlank() || password == null) {
            throw new IllegalArgumentException("Set both ADMIN_EMAIL and ADMIN_PASSWORD");
        }
        if (userDAO.findByEmail(email) == null) {
            app.entities.User admin = new app.entities.User("Administrator", email, "ADMIN");
            admin.setPassword(password);
            userDAO.save(admin);
        }
    }
}
