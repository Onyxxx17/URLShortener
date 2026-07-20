package com.ayth.urlshortener.url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface URLClickEventRepository extends JpaRepository<URLClickEvent, UUID> {
}
