package app.config;

import app.entities.*;
import org.hibernate.cfg.Configuration;

final class EntityRegistry {

    private EntityRegistry() {}

    static void registerEntities(Configuration configuration) {
        configuration.addAnnotatedClass(User.class);
        configuration.addAnnotatedClass(Ticket.class);
        configuration.addAnnotatedClass(Comment.class);
        configuration.addAnnotatedClass(Attachment.class);
    }
}
