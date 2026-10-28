package app.entities;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.mindrot.jbcrypt.BCrypt;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    private String name;

    @Column(unique = true)
    private String email;

    private String role;

    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;

    public void setPassword(String password) {
        if (password == null || password.length() < 8
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("password must be at least 8 characters and at most 72 bytes");
        }
        passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
    }

    public boolean verifyPassword(String password) {
        return passwordHash != null && BCrypt.checkpw(password, passwordHash);
    }

    public User(String name, String email, String role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }
}
