package com.ayth.urlshortener.users;

import com.ayth.urlshortener.url.URL;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    /**
     * Time-ordered UUID (v7 strategy via Hibernate).
     * Monotonically increasing → no B-tree page-split hotspots,
     * excellent index performance compared to random UUID v4.
     */
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<URL> urls = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
}
