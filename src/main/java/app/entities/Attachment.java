package app.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "attachments")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String fileName;
    private String fileType;
    private String filePath;

    @ManyToOne
    @ToString.Exclude
    private Ticket ticket;

    public Attachment(String fileName, String fileType, String filePath, Ticket ticket) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.filePath = filePath;
        this.ticket = ticket;
    }
}
