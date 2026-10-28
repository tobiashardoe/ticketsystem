package app.controllers;

import app.dao.CommentDAO;
import app.dao.TicketDAO;
import app.dao.UserDAO;
import app.config.HibernateConfig;
import app.dtos.AbuseIpResponseDTO;
import app.services.AbuseIpService;
import app.entities.Comment;
import app.entities.Ticket;
import app.entities.User;
import app.exceptions.ApiException;
import app.security.SecurityController;
import app.utils.InputValidation;
import io.javalin.http.Context;

public class TicketController {

    private TicketDAO ticketDAO;
    private UserDAO userDAO;
    private CommentDAO commentDAO;
    private AbuseIpService abuseIpService;
    private TicketWebSocket notifications;

    public TicketController(AbuseIpService abuseIpService) {
        this(abuseIpService,
                new TicketDAO(HibernateConfig.getEntityManagerFactory()),
                new UserDAO(HibernateConfig.getEntityManagerFactory()),
                new CommentDAO(HibernateConfig.getEntityManagerFactory()));
    }

    public TicketController(AbuseIpService abuseIpService, TicketDAO ticketDAO,
                            UserDAO userDAO, CommentDAO commentDAO) {
        this.abuseIpService = abuseIpService;
        this.ticketDAO = ticketDAO;
        this.userDAO = userDAO;
        this.commentDAO = commentDAO;
    }


    public void setNotifications(TicketWebSocket notifications) {
        this.notifications = notifications;
    }

    public void getAll(Context ctx) {
        User user = SecurityController.currentUser(ctx);
        java.util.List<Ticket> tickets = ticketDAO.findAll();
        if ("USER".equals(user.getRole())) {
            tickets.removeIf(ticket -> ticket.getCreatedBy() == null
                    || ticket.getCreatedBy().getId() != user.getId());
        }
        ctx.json(tickets);
    }

    public void getById(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();
        Ticket ticket = ticketDAO.findById(id);

        if (ticket == null) {
            throw new ApiException(404, "Ticket not found");
        } else {
            SecurityController.requireTicketAccess(ctx, ticket);
            ctx.json(ticket);
        }
    }

    public void create(Context ctx) {
        User user = SecurityController.currentUser(ctx);

        if (user == null) {
            throw new ApiException(404, "User not found");
        }

        Ticket input = ctx.bodyValidator(Ticket.class).check(body -> body != null, "Request body is required").get();
        validateTicket(input);
        // Use the connection address, not an IP supplied in the JSON or headers.
        String ipAddress = ctx.req().getRemoteAddr();
        AbuseIpResponseDTO ipResult;
        try {
            ipResult = abuseIpService.checkIp(ipAddress);
        } catch (RuntimeException e) {
            throw new ApiException(503, "IP safety check unavailable. Please try again later.");
        }

        // Project rule: a score of 80 or higher prevents ticket creation.
        if (ipResult.getData().getAbuseConfidenceScore() >= 80) {
            throw new ApiException(403, "Ticket creation rejected by IP safety check.");
        }

        Ticket ticket = new Ticket(
                input.getTitle(), input.getDescription(), input.getStatus(),
                input.getPriority(), user);
        ticketDAO.save(ticket);
        if (notifications != null) {
            notifications.ticketCreated(ticket.getId());
        }
        ctx.status(201).json(ticket);
    }

    public void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();
        Ticket ticket = ticketDAO.findById(id);

        if (ticket == null) {
            throw new ApiException(404, "Ticket not found");
        }

        SecurityController.requireTicketAccess(ctx, ticket);
        Ticket input = ctx.bodyValidator(Ticket.class).check(body -> body != null, "Request body is required").get();
        validateTicket(input);
        ticket.setTitle(input.getTitle());
        ticket.setDescription(input.getDescription());
        ticket.setStatus(input.getStatus());
        ticket.setPriority(input.getPriority());
        Ticket updatedTicket = ticketDAO.update(ticket);
        notifyChange(updatedTicket, "TICKET_UPDATED");
        ctx.json(updatedTicket);
    }

    public void assignSupporter(Context ctx) {
        SecurityController.requireStaff(ctx, false);
        int ticketId = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();
        int supporterId = ctx.queryParamAsClass("supporterId", Integer.class).check(value -> value > 0, "supporterId must be positive").get();
        Ticket ticket = ticketDAO.findById(ticketId);
        User supporter = userDAO.findById(supporterId);

        if (ticket == null || supporter == null) {
            throw new ApiException(404, "Ticket or supporter not found");
        }
        if (!"SUPPORT".equals(supporter.getRole())) {
            throw new ApiException(400, "The selected user is not a supporter");
        }

        ticket.setAssignedTo(supporter);
        ticket.setStatus("IN_PROGRESS");
        Ticket updatedTicket = ticketDAO.update(ticket);
        notifyChange(updatedTicket, "TICKET_ASSIGNED");
        ctx.json(updatedTicket);
    }

    public void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();
        if (ticketDAO.findById(id) == null) {
            throw new ApiException(404, "Ticket not found");
        }
        Ticket ticket = ticketDAO.findById(id);
        SecurityController.requireTicketAccess(ctx, ticket);
        ticketDAO.delete(id);
        notifyChange(ticket, "TICKET_DELETED");
        ctx.status(204);
    }

    public void getComments(Context ctx) {
        int ticketId = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();
        requireTicket(ctx, ticketId);
        ctx.json(commentDAO.findByTicketId(ticketId));
    }

    public void createComment(Context ctx) {
        int ticketId = ctx.pathParamAsClass("id", Integer.class).check(value -> value > 0, "id must be positive").get();

        Ticket ticket = ticketDAO.findById(ticketId);
        User user = SecurityController.currentUser(ctx);

        if (ticket == null || user == null) {
            throw new ApiException(404, "Ticket or user not found");
        }

        SecurityController.requireTicketAccess(ctx, ticket);
        Comment input = ctx.bodyValidator(Comment.class).check(body -> body != null, "Request body is required").get();
        InputValidation.requireText(input.getMessage(), "message");
        Comment comment = new Comment(input.getMessage(), ticket, user);
        commentDAO.save(comment);
        notifyChange(ticket, "COMMENT_CREATED");
        ctx.status(201).json(comment);
    }


    private void validateTicket(Ticket ticket) {
        InputValidation.requireText(ticket.getTitle(), "title");
        InputValidation.requireText(ticket.getDescription(), "description");
        InputValidation.requireChoice(ticket.getStatus(), "status", "OPEN", "IN_PROGRESS", "CLOSED");
        InputValidation.requireChoice(ticket.getPriority(), "priority", "LOW", "MEDIUM", "HIGH");
    }

    private void notifyChange(Ticket ticket, String eventType) {
        if (notifications != null) {
            notifications.ticketChanged(ticket, eventType);
        }
    }

    private void requireTicket(Context ctx, int id) {
        Ticket ticket = ticketDAO.findById(id);
        if (ticket == null) {
            throw new ApiException(404, "Ticket not found");
        }
        SecurityController.requireTicketAccess(ctx, ticket);
    }
}
