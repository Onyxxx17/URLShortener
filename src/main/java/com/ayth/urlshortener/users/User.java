package com.ayth.urlshortener.users;

import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.ayth.urlshortener.url.URL;

import java.util.ArrayList;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private String password;
    
    private String email;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<URL> urls = new ArrayList<>();
}
