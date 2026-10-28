package app.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String message;

    @ManyToOne
    @ToString.Exclude
    private Ticket ticket;

    @ManyToOne
    @ToString.Exclude
    private User author;

    public Comment(String message, Ticket ticket, User author) {
        this.message = message;
        this.ticket = ticket;
        this.author = author;
    }
}
