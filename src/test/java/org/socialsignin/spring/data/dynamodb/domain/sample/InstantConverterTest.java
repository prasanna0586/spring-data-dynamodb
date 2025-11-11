package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InstantConverter Unit Tests")
class InstantConverterTest {

    private InstantConverter instantConverter;

    @BeforeEach
    void setUp() {
        instantConverter = new InstantConverter();
    }

    // ==================== Convert Tests (Instant -> String) ====================

    @Test
    @DisplayName("Should convert Instant to ISO-8601 String")
    void testConvert_Success() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");

        // When
        String result = instantConverter.convert(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("2024-01-15T10:30:00Z");
    }

    @Test
    @DisplayName("Should convert null Instant to null String")
    void testConvert_Null() {
        // When
        String result = instantConverter.convert(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should convert Instant with milliseconds correctly")
    void testConvert_WithMilliseconds() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00.123Z");

        // When
        String result = instantConverter.convert(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("2024-01-15T10:30:00.123Z");
    }

    @Test
    @DisplayName("Should convert Instant with nanoseconds correctly")
    void testConvert_WithNanoseconds() {
        // Given
        Instant instant = Instant.parse("2024-01-15T10:30:00.123456789Z");

        // When
        String result = instantConverter.convert(instant);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("2024-01-15T10:30:00.123456789Z");
    }

    @Test
    @DisplayName("Should convert epoch time correctly")
    void testConvert_Epoch() {
        // Given
        Instant epoch = Instant.EPOCH; // 1970-01-01T00:00:00Z

        // When
        String result = instantConverter.convert(epoch);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("1970-01-01T00:00:00Z");
    }

    // ==================== Unconvert Tests (String -> Instant) ====================

    @Test
    @DisplayName("Should unconvert ISO-8601 String to Instant")
    void testUnconvert_Success() {
        // Given
        String isoString = "2024-01-15T10:30:00Z";

        // When
        Instant result = instantConverter.unconvert(isoString);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    }

    @Test
    @DisplayName("Should unconvert null String to null Instant")
    void testUnconvert_Null() {
        // When
        Instant result = instantConverter.unconvert(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should unconvert String with milliseconds correctly")
    void testUnconvert_WithMilliseconds() {
        // Given
        String isoString = "2024-01-15T10:30:00.123Z";

        // When
        Instant result = instantConverter.unconvert(isoString);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00.123Z"));
    }

    @Test
    @DisplayName("Should unconvert String with nanoseconds correctly")
    void testUnconvert_WithNanoseconds() {
        // Given
        String isoString = "2024-01-15T10:30:00.123456789Z";

        // When
        Instant result = instantConverter.unconvert(isoString);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.parse("2024-01-15T10:30:00.123456789Z"));
    }

    @Test
    @DisplayName("Should unconvert epoch time correctly")
    void testUnconvert_Epoch() {
        // Given
        String epochString = "1970-01-01T00:00:00Z";

        // When
        Instant result = instantConverter.unconvert(epochString);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(Instant.EPOCH);
    }

    // ==================== Round-trip Tests ====================

    @Test
    @DisplayName("Should maintain precision through convert and unconvert round-trip")
    void testRoundTrip_MaintainsPrecision() {
        // Given
        Instant original = Instant.parse("2024-01-15T10:30:00.123456789Z");

        // When - Convert to String and back to Instant
        String converted = instantConverter.convert(original);
        Instant unconverted = instantConverter.unconvert(converted);

        // Then
        assertThat(unconverted).isEqualTo(original);
    }

    @Test
    @DisplayName("Should handle null values in round-trip")
    void testRoundTrip_Null() {
        // Given
        Instant original = null;

        // When - Convert to String and back to Instant
        String converted = instantConverter.convert(original);
        Instant unconverted = instantConverter.unconvert(converted);

        // Then
        assertThat(converted).isNull();
        assertThat(unconverted).isNull();
    }

    @Test
    @DisplayName("Should handle current time in round-trip")
    void testRoundTrip_CurrentTime() {
        // Given
        Instant now = Instant.now();

        // When - Convert to String and back to Instant
        String converted = instantConverter.convert(now);
        Instant unconverted = instantConverter.unconvert(converted);

        // Then
        assertThat(unconverted).isEqualTo(now);
    }
}
