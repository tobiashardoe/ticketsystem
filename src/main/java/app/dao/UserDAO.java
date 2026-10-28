package app.dao;

import app.entities.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class UserDAO {

    private EntityManagerFactory emf;

    public UserDAO(EntityManagerFactory emf) {
        this.emf = emf;
    }

    public User save(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        em.persist(user);
        em.getTransaction().commit();
        em.close();
        return user;
    }

    public User findById(int id) {
        EntityManager em = emf.createEntityManager();
        User user = em.find(User.class, id);
        em.close();
        return user;
    }

    public User findByEmail(String email) {
        try (EntityManager em = emf.createEntityManager()) {
            List<User> users = em.createQuery("SELECT u FROM User u WHERE u.email = :email", User.class)
                    .setParameter("email", email).getResultList();
            return users.isEmpty() ? null : users.get(0);
        }
    }

    public User update(User user) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        User updatedUser = em.merge(user);
        em.getTransaction().commit();
        em.close();
        return updatedUser;
    }

    public void delete(int id) {
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        User user = em.find(User.class, id);
        if (user != null) {
            em.remove(user);
        }
        em.getTransaction().commit();
        em.close();
    }

    public List<User> findAll() {
        EntityManager em = emf.createEntityManager();
        TypedQuery<User> query = em.createQuery("SELECT u FROM User u", User.class);
        List<User> users = query.getResultList();
        em.close();
        return users;
    }

}
