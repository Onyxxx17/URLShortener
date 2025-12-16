package com.ayth.urlshortener.url;

import org.springframework.data.jpa.repository.JpaRepository;

interface URLRepository extends JpaRepository<URL, Long> {

}
