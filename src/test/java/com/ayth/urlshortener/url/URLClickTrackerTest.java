package com.ayth.urlshortener.url;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class URLClickTrackerTest {

    private URLRepository urlRepository;
    private URLClickEventRepository urlClickEventRepository;
    private URLClickTracker tracker;

    @BeforeEach
    void setUp() {
        urlRepository = mock(URLRepository.class);
        urlClickEventRepository = mock(URLClickEventRepository.class);
        tracker = new URLClickTracker(urlRepository, urlClickEventRepository);
    }

    @Test
    void incrementClickCountAndUpdateLastAccessed_incrementsCountAndRecordsClickEvent() {
        URL url = new URL();
        url.setId(1L);
        url.setShortCode("abc1234");
        url.setClickCount(4);
        when(urlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(url));

        tracker.incrementClickCountAndUpdateLastAccessed("abc1234", "https://referer.example", "some-agent");

        assertThat(url.getClickCount()).isEqualTo(5);
        assertThat(url.getLastAccessedAt()).isNotNull();
        verify(urlRepository).save(url);

        ArgumentCaptor<URLClickEvent> captor = ArgumentCaptor.forClass(URLClickEvent.class);
        verify(urlClickEventRepository).save(captor.capture());
        URLClickEvent event = captor.getValue();
        assertThat(event.getUrl()).isEqualTo(url);
        assertThat(event.getReferer()).isEqualTo("https://referer.example");
        assertThat(event.getUserAgent()).isEqualTo("some-agent");
        assertThat(event.getClickTimestamp()).isNotNull();
    }

    @Test
    void incrementClickCountAndUpdateLastAccessed_doesNothing_whenShortCodeNotFound() {
        when(urlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        tracker.incrementClickCountAndUpdateLastAccessed("missing", "ref", "agent");

        verify(urlRepository, never()).save(any());
        verifyNoInteractions(urlClickEventRepository);
    }
}
