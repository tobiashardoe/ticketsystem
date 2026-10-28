package app.dao;

import app.entities.Comment;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class CommentDAO {

    private EntityManagerFactory emf;

    public CommentDAO(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public Comment save(Comment comment) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(comment);
        em.getTransaction().commit();
        em.close();
        return comment;
    }

    public Comment findById(int id) {
        EntityManager em = emf.createEntityManager();
        Comment comment = em.find(Comment.class, id);
        em.close();
        return comment;
    }

    public Comment update(Comment comment) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Comment updatedComment = em.merge(comment);
        em.getTransaction().commit();
        em.close();
        return updatedComment;
    }

    public void delete(int id) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        Comment comment = em.find(Comment.class, id);
        if (comment != null) {
            em.remove(comment);
        }
        em.getTransaction().commit();
        em.close();
    }

    public List<Comment> findAll() {
        EntityManager em = emf.createEntityManager();
        TypedQuery<Comment> query = em.createQuery("SELECT c FROM Comment c", Comment.class);
        List<Comment> comments = query.getResultList();
        em.close();
        return comments;
    }

    public List<Comment> findByTicketId(int ticketId) {
        try (EntityManager em = emf.createEntityManager()) {
            return em.createQuery(
                            "SELECT c FROM Comment c "
                                    + "JOIN FETCH c.author "
                                    + "WHERE c.ticket.id = :ticketId "
                                    + "ORDER BY c.id",
                            Comment.class)
                    .setParameter("ticketId", ticketId)
                    .getResultList();
        }
    }
}
