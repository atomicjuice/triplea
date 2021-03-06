package org.triplea.modules.moderation.bad.words;

import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.moderator.toolbox.words.ToolboxBadWordsClient;
import org.triplea.modules.http.AllowedUserRole;
import org.triplea.modules.http.ProtectedEndpointTest;

class BadWordsControllerIntegrationTest extends ProtectedEndpointTest<ToolboxBadWordsClient> {

  BadWordsControllerIntegrationTest() {
    super(AllowedUserRole.MODERATOR, ToolboxBadWordsClient::newClient);
  }

  @Test
  void removeBadWord() {
    verifyEndpoint(client -> client.removeBadWord("awful"));
  }

  @Test
  void addBadWord() {
    verifyEndpoint(client -> client.addBadWord("horrible"));
  }

  @Test
  void getBadWords() {
    verifyEndpointReturningCollection(ToolboxBadWordsClient::getBadWords);
  }
}
