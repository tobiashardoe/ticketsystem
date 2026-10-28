package app.dao;

import app.entities.Attachment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import java.util.List;

public class AttachmentDAO {

    private EntityManagerFactory emf;

    public AttachmentDAO(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Attachment save(Attachment attachment) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(attachment);
        em.getTransaction().commit();
        em.close();
        return attachment;
    }

    public Attachment findById(int id) {
        EntityManager em = emf.createEntityManager();
        Attachment attachment = em.find(Attachment.class, id);
        em.close();
        return attachment;
    }

    public Attachment update(Attachment attachment) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Attachment updatedAttachment = em.merge(attachment);
        em.getTransaction().commit();
        em.close();
        return updatedAttachment;
    }

    public void delete(int id) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Attachment attachment = em.find(Attachment.class, id);
        if (attachment != null) {
            em.remove(attachment);
        }
        em.getTransaction().commit();
        em.close();
    }

    public List<Attachment> findAll() {
        EntityManager em = emf.createEntityManager();
        List<Attachment> attachments = em.createQuery(
                        "SELECT a FROM Attachment a",
                        Attachment.class)
                .getResultList();
        em.close();
        return attachments;
    }

    public List<Attachment> findByTicketId(int ticketId) {
        EntityManager em = emf.createEntityManager();
        List<Attachment> attachments = em.createQuery(
                        "SELECT a FROM Attachment a WHERE a.ticket.id = :ticketId",
                        Attachment.class)
                .setParameter("ticketId", ticketId)
                .getResultList();
        em.close();
        return attachments;
    }
}
