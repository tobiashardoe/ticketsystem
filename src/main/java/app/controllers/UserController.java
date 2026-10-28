package app.controllers;

import app.dao.UserDAO;
import app.config.HibernateConfig;
import app.entities.User;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.utils.InputValidation;
import io.javalin.http.Context;

public class UserController {

    private UserDAO userDAO;

    public UserController() {
        this(new UserDAO(HibernateConfig.getEntityManagerFactory()));
    }

    public UserController(UserDAO userDAO) {
        this.userDAO = userDAO;
    }





    public void createSupporter(Context ctx) {
        User admin = SecurityController.currentUser(ctx);

        if (admin == null || !"ADMIN".equals(admin.getRole())) {
            throw new ApiException(403, "Only an admin can create supporters");
        }

        User input = ctx.bodyValidator(User.class).check(body -> body != null, "Request body is required").get();
        validateUser(input);
        User supporter = new User(input.getName(), input.getEmail(), "SUPPORT");
        if (input.getPasswordHash() == null) throw new ApiException(400, "password is required");
        if (userDAO.findByEmail(supporter.getEmail()) != null) throw new ApiException(409, "Email is already registered");
        supporter.setPasswordHash(input.getPasswordHash());
        userDAO.save(supporter);
        ctx.status(201).json(supporter);
    }


    private void validateUser(User user) {
        InputValidation.requireText(user.getName(), "name");
        InputValidation.requireText(user.getEmail(), "email");
        if (!user.getEmail().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {
            throw new ApiException(400, "email must have a valid format");
        }
        if (user.getRole() != null) {
            InputValidation.requireChoice(user.getRole(), "role", "USER", "SUPPORT", "ADMIN");
        }
    }
}
