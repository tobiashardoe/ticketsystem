package app.controllers;

import app.Main;
import app.routes.Routes;
import app.security.SecurityController;
import app.dao.UserDAO;
import app.entities.User;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Testcontainers
class UserControllerTest {

    @Container
    private static PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("resolvedesk_api_test")
            .withUsername("test")
            .withPassword("test");

    private static EntityManagerFactory emf;
    private static UserDAO userDAO;
    private static Javalin app;
    private static SecurityController security;
    private static RequestSpecification requestSpec;
    private String token;

    @BeforeAll
    static void startServer() {
        Properties properties = new Properties();
        properties.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        properties.put("hibernate.connection.url", POSTGRES.getJdbcUrl());
        properties.put("hibernate.connection.username", POSTGRES.getUsername());
        properties.put("hibernate.connection.password", POSTGRES.getPassword());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        emf = new Configuration().setProperties(properties)
                .addAnnotatedClass(User.class).buildSessionFactory();
        userDAO = new UserDAO(emf);

        app = Javalin.create();
        Main.configureErrorHandling(app);
        security = new SecurityController(userDAO, "test-secret-only-not-for-production-123456789");
        Routes.addSecurityRoutes(app, security);
        Routes.addUserRoutes(app, new UserController(userDAO));
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
    void seedDatabase() {
        for (User user : userDAO.findAll()) {
            userDAO.delete(user.getId());
        }
        User admin = new User("Admin", "admin@example.com", "ADMIN");
        admin.setPassword("admin-test-password");
        userDAO.save(admin);

        // Hent token gennem login, ligesom en klient gør.
        token = given()
                .spec(requestSpec)
                .body("""
                        {"email":"admin@example.com","password":"admin-test-password"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");
    }

    @AfterAll
    static void stopServer() {
        if (app != null) {
            app.stop();
        }
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    @DisplayName("Given valid registration data, when registering and logging in, then return a token without exposing the password")
    void registerAndLoginShouldReturnTokenAndHidePassword() {
        String body = """
                {"name":"New User","email":"new@example.com","password":"test-password","role":"ADMIN"}
                """;
        given()
                .spec(requestSpec)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/auth/register")
        .then()
                .statusCode(201)
                .body("token", not(emptyString()))
                .body("user.role", equalTo("USER"))
                .body("user", not(hasKey("passwordHash")))
                .body("user", not(hasKey("password")));

        User saved = userDAO.findByEmail("new@example.com");
        org.junit.jupiter.api.Assertions.assertNotEquals("test-password", saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(saved.verifyPassword("test-password"));

        String loginToken = given()
                .spec(requestSpec)
                .contentType(ContentType.JSON)
                .body(body)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .body("user", not(hasKey("passwordHash")))
                .extract().path("token");

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + loginToken)
        .when()
                .post("/supporters")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Given an existing user, when login uses a wrong password, then return 401")
    void wrongPasswordShouldRejectLogin() {
        User user = new User("User", "login@example.com", "USER");
        user.setPassword("correct-password");
        userDAO.save(user);
        given()
                .spec(requestSpec)
                .contentType(ContentType.JSON)
                .body("""
                        {"email":"login@example.com","password":"wrong-password"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(401)
                .body("message", equalTo("Invalid email or password"));
    }

    @Test
    @DisplayName("Given a missing or invalid token, when POST supporters is called, then return 401")
    void protectedEndpointShouldRejectMissingAndInvalidToken() {
        given()
                .spec(requestSpec)
        .when()
                .post("/supporters")
        .then()
                .statusCode(401);
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer not-a-token")
        .when()
                .post("/supporters")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Given an expired token, when POST supporters is called, then return 401")
    void expiredTokenShouldBeRejected() throws Exception {
        User admin = userDAO.findByEmail("admin@example.com");
        String expired = new dk.bugelhartmann.TokenSecurity().createToken(
                new dk.bugelhartmann.UserDTO(String.valueOf(admin.getId()), java.util.Set.of("ADMIN")),
                "TicketSystem", "-10000", "test-secret-only-not-for-production-123456789");
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + expired)
        .when()
                .post("/supporters")
        .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("Given an ADMIN, when creating a supporter, then return 201 and allow the supporter to log in")
    void adminShouldCreateSupporterWhoCanLogin() {
        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"Supporter","email":"support@example.com","password":"support-password"}
                        """)
        .when()
                .post("/supporters")
        .then()
                .statusCode(201)
                .body("role", equalTo("SUPPORT"))
                .body("$", not(hasKey("passwordHash")));

        given()
                .spec(requestSpec)
                .contentType(ContentType.JSON)
                .body("""
                        {"email":"support@example.com","password":"support-password"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .body("token", not(emptyString()))
                .body("user.role", equalTo("SUPPORT"));
    }

    @Test
    @DisplayName("Given a USER, when POST supporters is called, then deny admin access with 403")
    void userShouldNotCreateSupporters() {
        User user = new User("User", "ordinary@example.com", "USER");
        user.setPassword("user-test-password");
        userDAO.save(user);

        String userToken = given()
                .spec(requestSpec)
                .body("""
                        {"email":"ordinary@example.com","password":"user-test-password"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + userToken)
        .when()
                .post("/supporters")
        .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("Given a SUPPORT user, when POST supporters is called, then deny admin access with 403")
    void supporterShouldNotCreateSupporters() {
        User supporter = new User("Support", "staff@example.com", "SUPPORT");
        supporter.setPassword("support-test-password");
        userDAO.save(supporter);

        String supportToken = given()
                .spec(requestSpec)
                .body("""
                        {"email":"staff@example.com","password":"support-test-password"}
                        """)
        .when()
                .post("/auth/login")
        .then()
                .statusCode(200)
                .extract().path("token");

        given()
                .spec(requestSpec)
                .header("Authorization", "Bearer " + supportToken)
                .body("""
                        {"name":"Not allowed","email":"blocked@example.com","password":"support-password"}
                        """)
        .when()
                .post("/supporters")
        .then()
                .statusCode(403);

        // Afvisningen må ikke have oprettet brugeren.
        org.junit.jupiter.api.Assertions.assertNull(userDAO.findByEmail("blocked@example.com"));
    }
}
