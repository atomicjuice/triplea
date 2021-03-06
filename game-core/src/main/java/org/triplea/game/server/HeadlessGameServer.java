package org.triplea.game.server;

import static games.strategy.engine.framework.CliProperties.LOBBY_GAME_COMMENTS;
import static games.strategy.engine.framework.CliProperties.LOBBY_GAME_SUPPORT_PASSWORD;
import static games.strategy.engine.framework.CliProperties.LOBBY_URI;
import static games.strategy.engine.framework.CliProperties.MAP_FOLDER;
import static games.strategy.engine.framework.CliProperties.TRIPLEA_GAME;
import static games.strategy.engine.framework.CliProperties.TRIPLEA_NAME;
import static games.strategy.engine.framework.CliProperties.TRIPLEA_PORT;
import static games.strategy.engine.framework.CliProperties.TRIPLEA_SERVER;

import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.chat.Chat;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.properties.GameProperties;
import games.strategy.engine.framework.ArgParser;
import games.strategy.engine.framework.GameRunner;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.framework.startup.mc.ServerModel;
import games.strategy.engine.framework.startup.ui.panels.main.game.selector.GameSelectorModel;
import games.strategy.triplea.Constants;
import games.strategy.triplea.settings.ClientSetting;
import java.io.File;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.triplea.game.startup.SetupModel;
import org.triplea.java.Interruptibles;
import org.triplea.util.ExitStatus;

/** A way of hosting a game, but headless. */
@Log
public class HeadlessGameServer {
  public static final String BOT_GAME_HOST_COMMENT = "automated_host";
  public static final String BOT_GAME_HOST_NAME_PREFIX = "Bot";
  private static HeadlessGameServer instance = null;

  private final AvailableGames availableGames = new AvailableGames();
  private final GameSelectorModel gameSelectorModel = new GameSelectorModel();
  private final HeadlessServerSetupPanelModel setupPanelModel =
      new HeadlessServerSetupPanelModel(gameSelectorModel);
  private ServerGame game = null;
  private boolean shutDown = false;

  private HeadlessGameServer() {
    if (instance != null) {
      throw new IllegalStateException("Instance already exists");
    }
    instance = this;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("Running ShutdownHook.");
                  shutDown = true;
                  Optional.ofNullable(game).ifPresent(ServerGame::stopGame);
                  Optional.ofNullable(setupPanelModel.getPanel())
                      .ifPresent(HeadlessServerSetup::cancel);
                }));

    new Thread(
            () -> {
              log.info("Headless Start");
              setupPanelModel.showSelectType();
              log.info("Waiting for users to connect.");
              waitForUsersHeadless();
            },
            "Initialize Headless Server Setup Model")
        .start();

    log.info("Game Server initialized");
  }

  public static synchronized HeadlessGameServer getInstance() {
    return instance;
  }

  public static synchronized boolean headless() {
    return instance != null
        || Boolean.parseBoolean(System.getProperty(GameRunner.TRIPLEA_HEADLESS, "false"));
  }

  public Set<String> getAvailableGames() {
    return availableGames.getGameNames();
  }

  public synchronized void setGameMapTo(final String gameName) {
    // don't change mid-game
    if (setupPanelModel.getPanel() != null && game == null) {
      if (!availableGames.getGameNames().contains(gameName)) {
        return;
      }
      gameSelectorModel.load(
          availableGames.getGameData(gameName), availableGames.getGameFilePath(gameName));
      log.info("Changed to game map: " + gameName);
    }
  }

  public synchronized void loadGameSave(final File file) {
    // don't change mid-game
    if (setupPanelModel.getPanel() != null && game == null) {
      if (file == null || !file.exists()) {
        return;
      }
      try {
        gameSelectorModel.load(file);
        log.info("Changed to save: " + file.getName());
      } catch (final Exception e) {
        log.log(Level.SEVERE, "Error loading game file: " + file.getAbsolutePath(), e);
      }
    }
  }

  /**
   * Loads a save game from the specified stream.
   *
   * @param input The stream containing the save game.
   * @param fileName The label used to identify the save game in the UI. Typically the file name of
   *     the save game on the remote client that requested the save game to be loaded.
   */
  public synchronized void loadGameSave(final InputStream input, final String fileName) {
    // don't change mid-game
    if (setupPanelModel.getPanel() != null && game == null) {
      if (input == null || fileName == null) {
        return;
      }
      final GameData data = gameSelectorModel.getGameData(input);
      if (data == null) {
        log.info("Loading GameData failed for: " + fileName);
        return;
      }
      final String mapNameProperty = data.getProperties().get(Constants.MAP_NAME, "");
      if (!availableGames.containsMapName(mapNameProperty)) {
        log.info("Game mapName not in available games listing: " + mapNameProperty);
        return;
      }
      gameSelectorModel.load(data, fileName);
      log.info("Changed to user savegame: " + fileName);
    }
  }

  /**
   * Loads the game properties from the specified byte array and applies them to the
   * currently-selected game.
   *
   * @param bytes The serialized game properties.
   */
  public synchronized void loadGameOptions(final byte[] bytes) {
    // don't change mid-game
    if (setupPanelModel.getPanel() != null && game == null) {
      if (bytes == null || bytes.length == 0) {
        return;
      }
      final GameData data = gameSelectorModel.getGameData();
      if (data == null) {
        return;
      }
      final GameProperties props = data.getProperties();
      if (props == null) {
        return;
      }
      GameProperties.applyByteMapToChangeProperties(bytes, props);
      log.info("Changed to user game options.");
    }
  }

  /** Updates current 'HeadlessGameServer.game' instance to be set to the given parameter. */
  public static synchronized void setServerGame(final ServerGame serverGame) {
    if (instance != null) {
      instance.game = serverGame;
      if (serverGame != null) {
        log.info(
            "Game starting up: "
                + instance.game.isGameSequenceRunning()
                + ", GameOver: "
                + instance.game.isGameOver()
                + ", Players: "
                + instance.game.getPlayerManager().toString());
      }
    }
  }

  private void waitForUsersHeadless() {
    setServerGame(null);

    new Thread(
            () -> {
              while (!shutDown) {
                if (!Interruptibles.sleep(8000)) {
                  shutDown = true;
                  break;
                }
                if (setupPanelModel.getPanel() != null
                    && setupPanelModel.getPanel().canGameStart()) {
                  final boolean started = startHeadlessGame(setupPanelModel, gameSelectorModel);
                  if (!started) {
                    log.warning("Error in launcher, going back to waiting.");
                  } else {
                    // TODO: need a latch instead?
                    break;
                  }
                }
              }
            },
            "Headless Server Waiting For Users To Connect And Start")
        .start();
  }

  private static synchronized boolean startHeadlessGame(
      final HeadlessServerSetupPanelModel setupPanelModel,
      final GameSelectorModel gameSelectorModel) {
    try {
      if (setupPanelModel != null
          && setupPanelModel.getPanel() != null
          && setupPanelModel.getPanel().canGameStart()) {
        log.info(
            "Starting Game: "
                + gameSelectorModel.getGameData().getGameName()
                + ", Round: "
                + gameSelectorModel.getGameData().getSequence().getRound());

        final boolean launched =
            setupPanelModel
                .getPanel()
                .getLauncher()
                .map(
                    launcher -> {
                      new Thread(launcher::launch).start();
                      return true;
                    })
                .orElse(false);
        setupPanelModel.getPanel().postStartGame();
        return launched;
      }
    } catch (final Exception e) {
      log.log(Level.SEVERE, "Failed to start headless game", e);
      final ServerModel model = getServerModel(setupPanelModel);
      if (model != null) {
        // if we do not do this, we can get into an infinite loop of launching a game, then crashing
        // out,
        // then launching, etc.
        model.setAllPlayersToNullNodes();
      }
    }
    return false;
  }

  public static void waitForUsersHeadlessInstance() {
    log.info("Waiting for users to connect.");
    instance.waitForUsersHeadless();
  }

  private static ServerModel getServerModel(final HeadlessServerSetupPanelModel setupPanelModel) {
    return Optional.ofNullable(setupPanelModel)
        .map(HeadlessServerSetupPanelModel::getPanel)
        .map(HeadlessServerSetup::getModel)
        .orElse(null);
  }

  /** todo, replace with something better Get the chat for the game, or null if there is no chat. */
  public Chat getChat() {
    final SetupModel model = setupPanelModel.getPanel();
    return model != null ? model.getChatModel().getChat() : null;
  }

  /**
   * Starts a new headless game server. This method will return before the headless game server
   * exits. The headless game server runs until the process is killed or the headless game server is
   * shut down via administrative command.
   *
   * <p>Most properties are passed via command line-like arguments.
   */
  public static void start(final String[] args) {
    ClientSetting.initialize();
    System.setProperty(LOBBY_GAME_COMMENTS, BOT_GAME_HOST_COMMENT);
    System.setProperty(GameRunner.TRIPLEA_HEADLESS, "true");
    System.setProperty(TRIPLEA_SERVER, "true");

    ArgParser.handleCommandLineArgs(args);
    handleHeadlessGameServerArgs();
    try {
      new HeadlessGameServer();
    } catch (final Exception e) {
      log.log(Level.SEVERE, "Failed to start game server", e);
    }
  }

  private static void usage() {
    // TODO replace this method with the generated usage of commons-cli
    log.info(
        "\nUsage and Valid Arguments:\n"
            + "   "
            + TRIPLEA_GAME
            + "=<FILE_NAME>\n"
            + "   "
            + TRIPLEA_PORT
            + "=<PORT>\n"
            + "   "
            + TRIPLEA_NAME
            + "=<PLAYER_NAME>\n"
            + "   "
            + LOBBY_URI
            + "=<LOBBY_URI>\n"
            + "   "
            + LOBBY_GAME_SUPPORT_PASSWORD
            + "=<password for remote actions, such as remote stop game>\n"
            + "   "
            + MAP_FOLDER
            + "=<MAP_FOLDER>"
            + "\n");
  }

  private static void handleHeadlessGameServerArgs() {
    boolean printUsage = false;

    if (!ClientSetting.mapFolderOverride.isSet()) {
      ClientSetting.mapFolderOverride.setValue(ClientFileSystemHelper.getUserMapsFolder().toPath());
    }

    final String playerName = System.getProperty(TRIPLEA_NAME, "");
    if ((playerName.length() < 7) || !playerName.startsWith(BOT_GAME_HOST_NAME_PREFIX)) {
      log.warning(
          "Invalid or missing argument: "
              + TRIPLEA_NAME
              + " must at least 7 characters long "
              + "and start with "
              + BOT_GAME_HOST_NAME_PREFIX);
      printUsage = true;
    }

    if (isInvalidPortNumber(System.getProperty(TRIPLEA_PORT, "0"))) {
      log.warning("Invalid or missing argument: " + TRIPLEA_PORT + " must be greater than zero");
      printUsage = true;
    }

    if (System.getProperty(LOBBY_URI, "").isEmpty()) {
      log.warning("Invalid or missing argument: " + LOBBY_URI + " must be set");
      printUsage = true;
    }

    if (printUsage) {
      usage();
      ExitStatus.FAILURE.exit();
    }
  }

  private static boolean isInvalidPortNumber(final String testValue) {
    try {
      return Integer.parseInt(testValue) <= 0;
    } catch (final NumberFormatException e) {
      return true;
    }
  }
}
