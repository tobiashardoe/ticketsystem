package app.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String title;

    private String description;

    private String status;

    private String priority;

    @ManyToOne
    @JoinColumn(name = "created_by_id")
    @ToString.Exclude
    private User createdBy;

    @ManyToOne
    @JoinColumn(name = "assigned_to_id")
    @ToString.Exclude
    private User assignedTo;

    public Ticket(String title, String description, String status,
                  String priority, User createdBy) {
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.createdBy = createdBy;
    }
}
