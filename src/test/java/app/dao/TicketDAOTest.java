package app.dao;

import app.entities.Ticket;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class TicketDAOTest {

    @Container
    private static PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("resolvedesk_ticket_test")
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
                .addAnnotatedClass(Ticket.class)
                .buildSessionFactory();
    }

    @AfterAll
    static void tearDown() {
        if (emf != null) {
            emf.close();
        }
    }

    @Test
    void saveShouldPersistTicketWithItsCreator() {
        UserDAO userDAO = new UserDAO(emf);
        TicketDAO ticketDAO = new TicketDAO(emf);
        User creator = userDAO.save(
                new User("Test User", "test@example.com", "USER"));
        Ticket ticket = new Ticket(
                "Login problem", "Cannot log in", "OPEN", "HIGH", creator);

        Ticket savedTicket = ticketDAO.save(ticket);
        Ticket foundTicket = ticketDAO.findById(savedTicket.getId());

        assertTrue(savedTicket.getId() > 0);
        assertNotNull(foundTicket);
        assertEquals("Login problem", foundTicket.getTitle());
        assertEquals("Cannot log in", foundTicket.getDescription());
        assertEquals("OPEN", foundTicket.getStatus());
        assertEquals("HIGH", foundTicket.getPriority());
        assertNotNull(foundTicket.getCreatedBy());
        assertEquals(creator.getId(), foundTicket.getCreatedBy().getId());
    }

    @BeforeEach
    void clearTicketsAndUsers() {
        TicketDAO ticketDAO = new TicketDAO(emf);
        UserDAO userDAO = new UserDAO(emf);
        // Tickets must be deleted before the users they reference.
        for (Ticket ticket : ticketDAO.findAll()) {
            ticketDAO.delete(ticket.getId());
        }
        for (User user : userDAO.findAll()) {
            userDAO.delete(user.getId());
        }
    }

    @Test
    void findAllShouldReturnSavedTickets() {
        UserDAO userDAO = new UserDAO(emf);
        TicketDAO ticketDAO = new TicketDAO(emf);
        User creator = userDAO.save(new User("Creator", "creator@example.com", "USER"));
        Ticket first = ticketDAO.save(new Ticket("First", "First problem", "OPEN", "LOW", creator));
        Ticket second = ticketDAO.save(new Ticket("Second", "Second problem", "OPEN", "HIGH", creator));

        List<Ticket> tickets = ticketDAO.findAll();

        assertEquals(2, tickets.size());
        for (Ticket ticket : tickets) {
            assertTrue(ticket.getId() == first.getId() || ticket.getId() == second.getId());
        }
    }

    @Test
    void updateShouldPersistChangedTicketAndSupporter() {
        UserDAO userDAO = new UserDAO(emf);
        TicketDAO ticketDAO = new TicketDAO(emf);
        User creator = userDAO.save(new User("Creator", "creator@example.com", "USER"));
        User supporter = userDAO.save(new User("Supporter", "support@example.com", "SUPPORT"));
        Ticket ticket = ticketDAO.save(new Ticket("Old title", "Old description", "OPEN", "LOW", creator));

        ticket.setTitle("New title");
        ticket.setDescription("New description");
        ticket.setStatus("IN_PROGRESS");
        ticket.setPriority("HIGH");
        ticket.setAssignedTo(supporter);
        ticketDAO.update(ticket);
        Ticket foundTicket = ticketDAO.findById(ticket.getId());

        assertNotNull(foundTicket);
        assertEquals("New title", foundTicket.getTitle());
        assertEquals("New description", foundTicket.getDescription());
        assertEquals("IN_PROGRESS", foundTicket.getStatus());
        assertEquals("HIGH", foundTicket.getPriority());
        assertEquals(creator.getId(), foundTicket.getCreatedBy().getId());
        assertNotNull(foundTicket.getAssignedTo());
        assertEquals(supporter.getId(), foundTicket.getAssignedTo().getId());
    }

    @Test
    void deleteShouldRemoveTicketButKeepCreator() {
        UserDAO userDAO = new UserDAO(emf);
        TicketDAO ticketDAO = new TicketDAO(emf);
        User creator = userDAO.save(new User("Creator", "creator@example.com", "USER"));
        Ticket ticket = ticketDAO.save(new Ticket("Delete", "Delete this ticket", "OPEN", "LOW", creator));

        ticketDAO.delete(ticket.getId());

        assertNull(ticketDAO.findById(ticket.getId()));
        assertNotNull(userDAO.findById(creator.getId()));
    }
}
