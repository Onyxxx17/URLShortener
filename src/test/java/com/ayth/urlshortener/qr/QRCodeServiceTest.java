package com.ayth.urlshortener.qr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QRCodeServiceTest {

    private final QRCodeService service = new QRCodeService();

    @Test
    void generateQRCodeImage_producesNonEmptyPngBytes() throws Exception {
        byte[] png = service.generateQRCodeImage("https://short.link/abc1234", 200, 200);

        assertThat(png).isNotEmpty();
        // PNG file signature: 89 50 4E 47 0D 0A 1A 0A
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 0x50);
        assertThat(png[2]).isEqualTo((byte) 0x4E);
        assertThat(png[3]).isEqualTo((byte) 0x47);
    }

    @Test
    void generateQRCodeImage_rejectsEmptyText() {
        assertThatThrownBy(() -> service.generateQRCodeImage("", 200, 200))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateQRCodeImage_differentTextProducesDifferentImage() throws Exception {
        byte[] first = service.generateQRCodeImage("https://short.link/aaa1111", 150, 150);
        byte[] second = service.generateQRCodeImage("https://short.link/zzz9999", 150, 150);

        assertThat(first).isNotEqualTo(second);
    }
}
