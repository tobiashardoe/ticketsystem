package app.security;

import app.dao.UserDAO;
import app.dtos.LoginDTO;
import app.entities.User;
import app.exceptions.ApiException;
import app.utils.InputValidation;
import dk.bugelhartmann.TokenSecurity;
import dk.bugelhartmann.TokenCreationException;
import dk.bugelhartmann.TokenVerificationException;
import dk.bugelhartmann.UserDTO;
import io.javalin.http.Context;
import java.text.ParseException;
import java.util.Map;
import java.util.Set;
import java.nio.charset.StandardCharsets;

public class SecurityController {
    private UserDAO userDAO;
    private String secret;
    private TokenSecurity tokens = new TokenSecurity();

    public SecurityController(UserDAO userDAO, String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        this.userDAO = userDAO;
        this.secret = secret;
    }


    public void register(Context ctx) {
        LoginDTO input = ctx.bodyValidator(LoginDTO.class).get();
        validateCredentials(input);
        InputValidation.requireText(input.getName(), "name");
        if (!input.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ApiException(400, "email must have a valid format");
        }
        if (input.getPassword().length() < 8
                || input.getPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(400, "password must be at least 8 characters and at most 72 bytes");
        }
        if (userDAO.findByEmail(input.getEmail()) != null) {
            throw new ApiException(409, "Email is already registered");
        }
        User user = new User(input.getName(), input.getEmail(), "USER");
        user.setPassword(input.getPassword());
        userDAO.save(user);
        ctx.status(201).json(Map.of("token", createToken(user), "user", user));
    }

    public void login(Context ctx) {
        LoginDTO input = ctx.bodyValidator(LoginDTO.class).get();
        validateCredentials(input);
        User user = userDAO.findByEmail(input.getEmail());
        if (user == null || input.getPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || !user.verifyPassword(input.getPassword())) {
            throw new ApiException(401, "Invalid email or password");
        }
        ctx.json(Map.of("token", createToken(user), "user", user));
    }

    private void validateCredentials(LoginDTO input) {
        if (input == null) throw new ApiException(400, "Request body is required");
        InputValidation.requireText(input.getEmail(), "email");
        InputValidation.requireText(input.getPassword(), "password");
    }

    public String createToken(User user) {
        try {
            return tokens.createToken(new UserDTO(String.valueOf(user.getId()), Set.of(user.getRole())),
                    "TicketSystem", "3600000", secret);
        } catch (TokenCreationException e) {
            throw new ApiException(500, "Could not create login token");
        }
    }

    public void authenticate(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(401, "Login required");
        }
        ctx.attribute("currentUser", verifyToken(header.substring(7)));
    }

    public User verifyToken(String token) {
        int id;
        try {
            if (!tokens.tokenIsValid(token, secret) || !tokens.tokenNotExpired(token)) {
                throw new ApiException(401, "Invalid or expired token");
            }
            id = Integer.parseInt(tokens.getUserWithRolesFromToken(token).getUsername());
        } catch (ParseException | TokenVerificationException | IllegalArgumentException | NullPointerException e) {
            throw new ApiException(401, "Invalid or expired token");
        }
        User user = userDAO.findById(id);
        if (user == null) throw new ApiException(401, "User no longer exists");
        return user;
    }

    public static User currentUser(Context ctx) {
        User user = ctx.attribute("currentUser");
        if (user == null) throw new ApiException(401, "Login required");
        return user;
    }

    public static void requireStaff(Context ctx, boolean adminOnly) {
        String role = currentUser(ctx).getRole();
        if (!"ADMIN".equals(role) && (adminOnly || !"SUPPORT".equals(role))) {
            throw new ApiException(403, "Insufficient permissions");
        }
    }

    public static void requireTicketAccess(Context ctx, app.entities.Ticket ticket) {
        User user = currentUser(ctx);
        if ("ADMIN".equals(user.getRole()) || "SUPPORT".equals(user.getRole())) return;
        if (ticket.getCreatedBy() == null || ticket.getCreatedBy().getId() != user.getId()) {
            throw new ApiException(403, "You may only access your own tickets");
        }
    }
}
