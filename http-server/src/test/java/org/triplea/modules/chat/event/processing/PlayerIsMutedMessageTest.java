package org.triplea.modules.chat.event.processing;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.triplea.java.DateTimeUtil.utcInstantOf;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerIsMutedMessageTest {
  private static final Instant CURRENT_TIME = utcInstantOf(2020, 11, 1, 2, 20);

  @Test
  @DisplayName("Verify an example mute message calculation with 10 minutes remaining")
  void verifyTimeDurationComputation() {
    final Instant banExpiry = CURRENT_TIME.plus(10, ChronoUnit.MINUTES);

    final String result =
        PlayerIsMutedMessage.muteDurationRemainingToString.apply(
            Clock.fixed(CURRENT_TIME, ZoneOffset.UTC), banExpiry);

    assertThat(result, is("10 minutes"));
  }

  @Test
  @DisplayName("Verify an example mute message calculation with seconds remaining")
  void verifyTimeDurationComputationWithSecondsRemaining() {
    final Instant banExpiry = CURRENT_TIME.plus(20, ChronoUnit.SECONDS);

    final String result =
        PlayerIsMutedMessage.muteDurationRemainingToString.apply(
            Clock.fixed(CURRENT_TIME, ZoneOffset.UTC), banExpiry);

    assertThat(result, is("20 seconds"));
  }
}