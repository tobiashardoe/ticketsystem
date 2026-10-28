package app.dao;

import app.entities.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Properties;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class UserDAOTest {

    @Container
    private static PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("resolvedesk_test")
                    .withUsername("test")
                    .withPassword("test");

    private static EntityManagerFactory emf;

    @BeforeAll
    static void setUp() {
        Properties properties = new Properties();
        properties.put("hibernate.connection.driver_class", "org.postgresql.Driver");
        properties.put("hibernate.connection.url", POSTGRES.getJdbcUrl());
        properties.put("hibernate.connection.username", POSTGRES.getUsername());
        properties.put("hibernate.connection.password", POSTGRES.getPassword());
        properties.put("hibernate.hbm2ddl.auto", "create-drop");

        emf = new Configuration()
                .setProperties(properties)
                .addAnnotatedClass(User.class)
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    void saveShouldPersistUserAndGenerateId() {
        UserDAO userDAO = new UserDAO(emf);
        User user = new User("Test User", "test@example.com", "USER");

        User savedUser = userDAO.save(user);

        assertTrue(savedUser.getId() > 0);
        User foundUser = userDAO.findById(savedUser.getId());
        assertNotNull(foundUser);
        assertEquals("Test User", foundUser.getName());
        assertEquals("test@example.com", foundUser.getEmail());
        assertEquals("USER", foundUser.getRole());
    }

    @BeforeEach
    void clearUsers() {
        UserDAO userDAO = new UserDAO(emf);
        for (User user : userDAO.findAll()) {
            userDAO.delete(user.getId());
        }
    }

    @Test
    void findAllShouldReturnSavedUsers() {
        UserDAO userDAO = new UserDAO(emf);
        User first = userDAO.save(new User("First User", "first@example.com", "USER"));
        User second = userDAO.save(new User("Second User", "second@example.com", "SUPPORT"));

        List<User> users = userDAO.findAll();

        assertEquals(2, users.size());
        for (User user : users) {
            assertTrue(user.getId() == first.getId() || user.getId() == second.getId());
        }
    }

    @Test
    void updateShouldPersistChangedUser() {
        UserDAO userDAO = new UserDAO(emf);
        User user = userDAO.save(new User("Old Name", "old@example.com", "USER"));

        user.setName("New Name");
        user.setEmail("new@example.com");
        user.setRole("SUPPORT");
        userDAO.update(user);
        User foundUser = userDAO.findById(user.getId());

        assertNotNull(foundUser);
        assertEquals("New Name", foundUser.getName());
        assertEquals("new@example.com", foundUser.getEmail());
        assertEquals("SUPPORT", foundUser.getRole());
    }

    @Test
    void deleteShouldRemoveUser() {
        UserDAO userDAO = new UserDAO(emf);
        User user = userDAO.save(new User("Delete User", "delete@example.com", "USER"));

        userDAO.delete(user.getId());

        assertNull(userDAO.findById(user.getId()));
    }
}
