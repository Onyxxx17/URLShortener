package com.ayth.urlshortener.url;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class URL {
    @Id
    private Long id;
}
