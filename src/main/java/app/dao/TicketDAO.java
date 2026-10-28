package app.dao;

import app.entities.Ticket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import java.util.List;

public class TicketDAO {

    private EntityManagerFactory emf;

    public TicketDAO(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Ticket save(Ticket ticket) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(ticket);
        em.getTransaction().commit();
        em.close();
        return ticket;
    }

    public Ticket findById(int id) {
        EntityManager em = emf.createEntityManager();
        Ticket ticket = em.find(Ticket.class, id);
        em.close();
        return ticket;
    }

    public Ticket update(Ticket ticket) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Ticket updatedTicket = em.merge(ticket);
        em.getTransaction().commit();
        em.close();
        return updatedTicket;
    }

    public void delete(int id) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Ticket ticket = em.find(Ticket.class, id);
        if (ticket != null) {
            em.remove(ticket);
        }
        em.getTransaction().commit();
        em.close();
    }

    public List<Ticket> findAll() {
        EntityManager em = emf.createEntityManager();
        TypedQuery<Ticket> query = em.createQuery("SELECT t FROM Ticket t", Ticket.class);
        List<Ticket> tickets = query.getResultList();
        em.close();
        return tickets;
    }

}
