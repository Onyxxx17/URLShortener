package com.ayth.urlshortener.url;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "url_click_events", indexes = {
        @Index(name = "idx_url_click_events_url_id", columnList = "url_id"),
        @Index(name = "idx_url_click_events_timestamp", columnList = "click_timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class URLClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private URL url;

    @Column(name = "click_timestamp", nullable = false)
    private Instant clickTimestamp;

    @Column(name = "referer", length = 1000)
    private String referer;

    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    @Column(name = "country")
    private String country;

    @Column(name = "city")
    private String city;

}
