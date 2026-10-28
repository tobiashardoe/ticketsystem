package app.routes;

import app.controllers.IpLookupController;
import app.controllers.TicketController;
import app.controllers.UserController;
import app.security.SecurityController;
import io.javalin.Javalin;

// Samler REST-endpoints og det fælles security-filter.
public class Routes {

    public static void addUserRoutes(Javalin app, UserController users) {
        app.post("/api/supporters", users::createSupporter);
    }

    public static void addTicketRoutes(Javalin app, TicketController tickets) {
        app.get("/api/tickets", tickets::getAll);
        app.get("/api/tickets/{id}", tickets::getById);
        app.post("/api/tickets", tickets::create);
        app.put("/api/tickets/{id}", tickets::update);
        app.delete("/api/tickets/{id}", tickets::delete);
        app.put("/api/tickets/{id}/assign", tickets::assignSupporter);
        app.get("/api/tickets/{id}/comments", tickets::getComments);
        app.post("/api/tickets/{id}/comments", tickets::createComment);
    }

    public static void addIpRoutes(Javalin app, IpLookupController ip) {
        app.post("/api/ip/check", ip::checkIpAddresses);
    }

    public static void addSecurityRoutes(Javalin app, SecurityController security) {
        app.post("/api/auth/register", security::register);
        app.post("/api/auth/login", security::login);
        app.before("/api/*", ctx -> {
            String path = ctx.path();
            if (path.equals("/api/auth/register")
                    || path.equals("/api/auth/login")) return;
            security.authenticate(ctx);
            if (path.equals("/api/supporters")) {
                SecurityController.requireStaff(ctx, true);
            }
            if (path.startsWith("/api/ip/")) SecurityController.requireStaff(ctx, false);
        });
    }

}
