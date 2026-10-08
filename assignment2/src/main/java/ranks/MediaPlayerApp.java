package ranks;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;


public class MediaPlayerApp extends Application {

    // Icon paths. 
    private static final String ICON_PLAY = "/images-two/play.png", 
    ICON_PAUSE = "/images-two/pause.png", 
    ICON_STOP = "/images-two/square.png", 
    ICON_PREV = "/images-two/skip-back.png",
    ICON_NEXT = "/images-two/skip-forward.png", 
    ICON_MUTE = "/images-two/volume-off.png", 
    ICON_UNMUTE = "/images-two/volume.png", 
    ICON_ADD = "/images-two/plus.png", 
    ICON_REMOVE = "/images-two/minus.png";

    // Background image shown behind the entire UI
    private static final String BACKGROUND_IMAGE = "";  

    // Fallback glyphs used when the icon path above is empty 
    private static final String GLYPH_PLAY = "\u25B6", GLYPH_PAUSE = "\u23F8", GLYPH_STOP = "\u23F9",
            GLYPH_PREV = "\u23EE", GLYPH_NEXT = "\u23ED", GLYPH_MUTE = "\uD83D\uDD07",
            GLYPH_UNMUTE = "\uD83D\uDD0A", GLYPH_ADD = "+", GLYPH_REMOVE = "\u2212";

    private static final double VOLUME_STEP = 0.05, SEEK_STEP_SECONDS = 5;

    private final ObservableList<File> playlist = FXCollections.observableArrayList();
    private MediaPlayer player;
    private int currentIndex = -1;
    private boolean muted = false, userSeeking = false, ignoreSelection = false;

    private Stage stage;
    private final MediaView mediaView = new MediaView();
    private final Label overlayLabel = new Label("No media loaded\nPress O or click the + button");
    private final ListView<File> playlistView = new ListView<File>(playlist);

    // Neon animated "heartbeat" bar that replaces the plain seek slider.
    private final NeonSeekBar seekSlider = new NeonSeekBar();

    private final Slider volumeSlider = new Slider(0, 1, 0.5);
    private final Label timeLabel = new Label("00:00 / 00:00");
    private final Label volumeLabel = new Label("50%");
    private final Label statusLabel = new Label("Ready");
    private final Button playPauseButton = new Button();
    private final Button muteButton = new Button();

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setCenter(buildVideoPane());
        root.setRight(buildPlaylistPanel());
        root.setBottom(buildControlBar());

        // Background layers
        Region background = new Region();
        background.getStyleClass().add("background-layer");
        background.setMouseTransparent(true);

        String bgUrl = resolveUrl(BACKGROUND_IMAGE);
        if (bgUrl != null) {
            background.setStyle("-fx-background-image: url(\"" + bgUrl + "\");"
                    + "-fx-background-size: cover;"
                    + "-fx-background-position: center center;"
                    + "-fx-background-repeat: no-repeat;");
        }

        Region vignette = new Region();
        vignette.getStyleClass().add("vignette");
        vignette.setMouseTransparent(true);

        Scene scene = new Scene(new StackPane(background, vignette, root), 1100, 680);
        scene.getStylesheets().add(buildStylesheet());
        installKeyboardControls(scene);
        updatePlayPauseIcon(false);

        stage.setTitle("Retro Player");
        stage.setMinWidth(800);
        stage.setMinHeight(500);
        stage.setScene(scene);
        stage.show();
    }

    private StackPane buildVideoPane() {
        overlayLabel.getStyleClass().add("overlay-label");
        overlayLabel.setWrapText(true);
        overlayLabel.setAlignment(Pos.CENTER);
        overlayLabel.setMaxWidth(560);
        mediaView.setPreserveRatio(true);

        StackPane pane = new StackPane(new CyberpunkBackdrop(), mediaView, overlayLabel);
        pane.getStyleClass().add("video-pane");
        pane.setMinSize(0, 0);
        mediaView.fitWidthProperty().bind(pane.widthProperty());
        mediaView.fitHeightProperty().bind(pane.heightProperty());
        return pane;
    }

    private VBox buildPlaylistPanel() {
        Label title = new Label("P L A Y L I S T");
        title.getStyleClass().add("title-label");

        Region underline = new Region();
        underline.getStyleClass().add("neon-underline");

        playlistView.setPlaceholder(new Label("Playlist is empty"));
        playlistView.setCellFactory(lv -> new ListCell<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                getStyleClass().remove("now-playing");
                if (empty || file == null) {
                    setText(null);
                } else {
                    setText(String.format("%02d   %s", getIndex() + 1, file.getName()));
                    if (getIndex() == currentIndex) getStyleClass().add("now-playing");
                }
            }
        });

        playlistView.getSelectionModel().selectedIndexProperty().addListener((obs, old, now) -> {
            if (ignoreSelection) return;
            int index = now.intValue();
            if (index >= 0 && index != currentIndex) playIndex(index);
        });
        VBox.setVgrow(playlistView, Priority.ALWAYS);

        Button addButton = iconButton(ICON_ADD, GLYPH_ADD, "Add files (O)");
        addButton.setOnAction(e -> addFiles());
        Button removeButton = iconButton(ICON_REMOVE, GLYPH_REMOVE, "Remove selected (Del)");
        removeButton.setOnAction(e -> removeSelected());

        HBox buttons = new HBox(10, addButton, removeButton);
        HBox.setHgrow(addButton, Priority.ALWAYS);
        HBox.setHgrow(removeButton, Priority.ALWAYS);
        addButton.setMaxWidth(Double.MAX_VALUE);
        removeButton.setMaxWidth(Double.MAX_VALUE);

        VBox panel = new VBox(10, title, underline, playlistView, buttons);
        panel.getStyleClass().add("playlist-panel");
        panel.setPadding(new Insets(16));
        panel.setPrefWidth(300);
        return panel;
    }

    private VBox buildControlBar() {
        seekSlider.setDisable(true);
        seekSlider.setFocusTraversable(false);
        HBox.setHgrow(seekSlider, Priority.ALWAYS);
        timeLabel.getStyleClass().add("time-label");
        timeLabel.setMinWidth(120);
        timeLabel.setAlignment(Pos.CENTER_RIGHT);

        HBox seekRow = new HBox(14, seekSlider, timeLabel);
        seekRow.setAlignment(Pos.CENTER);

        seekSlider.setOnMousePressed(e -> userSeeking = true);
        seekSlider.setOnMouseReleased(e -> {
            if (player != null) player.seek(Duration.seconds(seekSlider.getValue()));
            userSeeking = false;
        });

        // control icons
        Button prevButton = iconButton(ICON_PREV, GLYPH_PREV, "Previous (P)");
        Button stopButton = iconButton(ICON_STOP, GLYPH_STOP, "Stop (S)");
        Button nextButton = iconButton(ICON_NEXT, GLYPH_NEXT, "Next (N)");
        playPauseButton.getStyleClass().add("primary-button");
        muteButton.getStyleClass().add("primary-button");

        prevButton.setOnAction(e -> previous());
        playPauseButton.setOnAction(e -> togglePlayPause());
        stopButton.setOnAction(e -> stopPlayback());
        nextButton.setOnAction(e -> next());
        muteButton.setOnAction(e -> toggleMute());

        volumeSlider.getStyleClass().add("neon-slider");
        volumeSlider.setPrefWidth(140);
        volumeSlider.setFocusTraversable(false);
        volumeSlider.valueProperty().addListener((obs, old, now) -> {
            volumeLabel.setText(Math.round(now.doubleValue() * 100) + "%");
            if (player != null) player.setVolume(now.doubleValue());
        });
        volumeLabel.getStyleClass().add("time-label");
        volumeLabel.setMinWidth(46);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label volText = new Label("VOL");
        volText.getStyleClass().add("status-label");

        HBox buttonRow = new HBox(12, prevButton, playPauseButton, stopButton, nextButton,
                spacer, muteButton, volText, volumeSlider, volumeLabel);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.getStyleClass().add("status-label");
        Label hint = new Label("SPACE Play/Pause  \u2022  S Stop  \u2022  N/P Next/Prev  \u2022  "
                + "\u2191/\u2193 Volume  \u2022  M Mute  \u2022  \u2190/\u2192 Seek  \u2022  F Fullscreen");
        hint.getStyleClass().add("status-label");
        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        HBox statusRow = new HBox(10, statusLabel, spacer2, hint);

        VBox bar = new VBox(12, seekRow, buttonRow, statusRow);
        bar.getStyleClass().add("control-bar");
        bar.setPadding(new Insets(14, 18, 12, 18));
        return bar;
    }

    private Button iconButton(String iconPath, String glyph, String tooltip) {
        Button b = new Button();
        b.setGraphic(buildIcon(iconPath, glyph));
        b.setTooltip(new Tooltip(tooltip));
        b.getStyleClass().add("icon-button");
        b.setFocusTraversable(false);
        return b;
    }

    private void setIcon(Button button, String iconPath, String glyph) {
        button.setGraphic(buildIcon(iconPath, glyph));
    }

    private Node buildIcon(String iconPath, String glyph) {
        Image img = loadImage(iconPath);
        if (img != null) {
            ImageView view = new ImageView(img);
            view.setFitWidth(22);
            view.setFitHeight(22);
            view.setPreserveRatio(true);
            view.setSmooth(true);
            return view;
        }
        Label label = new Label(glyph);
        label.getStyleClass().add("glyph");
        return label;
    }

    private void updatePlayPauseIcon(boolean isPlaying) {
        setIcon(playPauseButton, isPlaying ? ICON_PAUSE : ICON_PLAY,
                isPlaying ? GLYPH_PAUSE : GLYPH_PLAY);
        playPauseButton.setTooltip(new Tooltip(isPlaying ? "Pause (Space)" : "Play (Space)"));
    }

    private static Image loadImage(String path) {
        String url = resolveUrl(path);
        if (url == null) return null;
        try {
            Image img = new Image(url, false);
            return img.isError() ? null : img;
        } catch (Exception ex) {
            return null;
        }
    }

    // Accepts a plain file path, a file: URL, or a classpath resource.
    private static String resolveUrl(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        String p = path.trim();
        try {
            File f = new File(p);
            if (f.isFile()) return f.toURI().toString();
        } catch (Exception ignored) { }
        try {
            URL res = MediaPlayerApp.class.getResource(p);
            if (res != null) return res.toExternalForm();
        } catch (Exception ignored) { }
        return null;
    }

    private void installKeyboardControls(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isShortcutDown() || e.isAltDown()) return;
            switch (e.getCode()) {
                case SPACE:  togglePlayPause(); break;
                case S:      stopPlayback();    break;
                case N:      next();            break;
                case P:      previous();        break;
                case UP:     changeVolume(VOLUME_STEP);  break;
                case DOWN:   changeVolume(-VOLUME_STEP); break;
                case M:      toggleMute();      break;
                case LEFT:   seekBy(-SEEK_STEP_SECONDS); break;
                case RIGHT:  seekBy(SEEK_STEP_SECONDS);  break;
                case O:      addFiles();        break;
                case DELETE:
                case BACK_SPACE:
                case R:      removeSelected();  break;
                case F:      stage.setFullScreen(!stage.isFullScreen()); break;
                default:     return;
            }
            e.consume();
        });
    }

    private void playIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;
        final File file = playlist.get(index);

        disposePlayer();
        currentIndex = index;

        try {
            final Media media = new Media(file.toURI().toString());
            media.setOnError(() -> {
                MediaException me = media.getError();
                if (me != null) me.printStackTrace();
                statusLabel.setText("Unsupported file: " + file.getName());
                showPlainAlert("Cannot play \"" + file.getName() + "\"",
                        "This file is not supported by the JavaFX media engine.");
                System.out.println("FAILED FILE: " + file.getAbsolutePath());
            });

            final MediaPlayer mp = new MediaPlayer(media);
            player = mp;
            mp.setVolume(volumeSlider.getValue());
            mp.setMute(muted);
            mediaView.setMediaPlayer(mp);
            overlayLabel.setText("Loading: " + file.getName());
            overlayLabel.setVisible(true);

            mp.setOnError(() -> {
                if (player != mp) return;
                MediaException me = mp.getError();
                if (me != null) me.printStackTrace();
                statusLabel.setText("Unsupported file: " + file.getName());
                showPlainAlert("Cannot play \"" + file.getName() + "\"",
                        "This file is not supported by the JavaFX media engine.");
                System.out.println("FAILED FILE: " + file.getAbsolutePath());
            });

            mp.setOnReady(() -> {
                if (player != mp) return;
                Duration total = mp.getTotalDuration();
                boolean seekable = isFinite(total);
                seekSlider.setDisable(!seekable);
                seekSlider.setMax(seekable ? total.toSeconds() : 1);
                seekSlider.setValue(0);

                double w = mp.getMedia().getWidth();
                double h = mp.getMedia().getHeight();
                boolean hasVideo = w > 0 && h > 0;
                overlayLabel.setVisible(!hasVideo);
                if (!hasVideo) overlayLabel.setText(file.getName());
                updateTimeLabel(Duration.ZERO);
            });

            mp.currentTimeProperty().addListener((obs, old, now) -> {
                if (player != mp) return;
                if (!userSeeking && !seekSlider.isDisabled()) seekSlider.setValue(now.toSeconds());
                updateTimeLabel(now);
            });
            mp.setOnPlaying(() -> { seekSlider.setPlaying(true);  updatePlayPauseIcon(true);  });
            mp.setOnPaused(()  -> { seekSlider.setPlaying(false); updatePlayPauseIcon(false); });
            mp.setOnStopped(() -> { seekSlider.setPlaying(false); updatePlayPauseIcon(false); });
            mp.setOnEndOfMedia(() -> {
                if (player != mp) return;
                if (currentIndex < playlist.size() - 1) {
                    playIndex(currentIndex + 1);
                } else {
                    stopPlayback();
                    statusLabel.setText("Reached the end of the playlist");
                }
            });

            statusLabel.setText("Now playing: " + file.getName());
            mp.play();
        } catch (Exception ex) {
            ex.printStackTrace();
            statusLabel.setText("Could not open " + file.getName());
            showPlainAlert("Could not open \"" + file.getName() + "\"",
                    "This file is not supported by the JavaFX media engine.");
        }

        ignoreSelection = true;
        playlistView.getSelectionModel().select(index);
        playlistView.scrollTo(index);
        ignoreSelection = false;
        playlistView.refresh();
    }

    private void togglePlayPause() {
        if (player == null) {
            if (playlist.isEmpty()) {
                statusLabel.setText("Playlist is empty - add some files first");
                return;
            }
            int selected = playlistView.getSelectionModel().getSelectedIndex();
            playIndex(selected >= 0 ? selected : 0);
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
            statusLabel.setText("Paused");
        } else {
            player.play();
            statusLabel.setText("Playing");
        }
    }

    private void stopPlayback() {
        if (player == null) return;
        player.stop();
        seekSlider.setValue(0);
        updateTimeLabel(Duration.ZERO);
        statusLabel.setText("Stopped");
    }

    private void next() {
        if (playlist.isEmpty()) return;
        playIndex((currentIndex + 1) % playlist.size());
    }

    private void previous() {
        if (playlist.isEmpty()) return;
        playIndex(currentIndex <= 0 ? playlist.size() - 1 : currentIndex - 1);
    }

    private void changeVolume(double delta) {
        double v = Math.max(0, Math.min(1, volumeSlider.getValue() + delta));
        volumeSlider.setValue(v);
        if (muted && delta > 0) toggleMute();
        statusLabel.setText("Volume: " + Math.round(v * 100) + "%");
    }

    private void toggleMute() {
        muted = !muted;
        if (player != null) player.setMute(muted);
        setIcon(muteButton, muted ? ICON_MUTE : ICON_UNMUTE, muted ? GLYPH_MUTE : GLYPH_UNMUTE);
        muteButton.setTooltip(new Tooltip(muted ? "Unmute (M)" : "Mute (M)"));
        statusLabel.setText(muted ? "Muted" : "Unmuted");
    }

    private void seekBy(double seconds) {
        if (player == null) return;
        Duration total = player.getTotalDuration();
        if (!isFinite(total)) return;
        double target = player.getCurrentTime().toSeconds() + seconds;
        target = Math.max(0, Math.min(total.toSeconds(), target));
        player.seek(Duration.seconds(target));
    }

    private void disposePlayer() {
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            old.stop();
            old.dispose();
        }
        mediaView.setMediaPlayer(null);
        seekSlider.setPlaying(false);
        seekSlider.setValue(0);
        seekSlider.setDisable(true);
        updateTimeLabel(Duration.ZERO);
        updatePlayPauseIcon(false);
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add media files");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Audio & Video",
                        "*.mp4", "*.m4v", "*.flv", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.aif", "*.aiff"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.m4v", "*.flv"),
                new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav", "*.aac", "*.m4a", "*.aif", "*.aiff"),
                new FileChooser.ExtensionFilter("All files", "*.*"));

        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        boolean wasEmpty = playlist.isEmpty();
        int firstNew = playlist.size();
        playlist.addAll(files);
        statusLabel.setText("Added " + files.size() + " file(s) to the playlist");
        if (wasEmpty || player == null) playIndex(firstNew);
    }

    private void removeSelected() {
        int selected = playlistView.getSelectionModel().getSelectedIndex();
        if (selected < 0) {
            statusLabel.setText("Select an item in the playlist to remove it");
            return;
        }
        String name = playlist.get(selected).getName();
        boolean removingCurrent = (selected == currentIndex);

        ignoreSelection = true;
        playlist.remove(selected);
        if (selected < currentIndex) currentIndex--;
        ignoreSelection = false;

        if (removingCurrent) {
            disposePlayer();
            currentIndex = -1;
            if (playlist.isEmpty()) {
                overlayLabel.setText("No media loaded\nPress O or click the + button");
                overlayLabel.setVisible(true);
            } else {
                playIndex(Math.min(selected, playlist.size() - 1));
            }
        } else if (currentIndex >= 0) {
            ignoreSelection = true;
            playlistView.getSelectionModel().select(currentIndex);
            ignoreSelection = false;
        }
        playlistView.refresh();
        statusLabel.setText("Removed: " + name);
    }

    private void updateTimeLabel(Duration current) {
        Duration total = player != null ? player.getTotalDuration() : Duration.ZERO;
        timeLabel.setText(format(current) + " / " + format(total));
    }

    private static boolean isFinite(Duration d) {
        return d != null && !d.isUnknown() && !d.isIndefinite();
    }

    private static String format(Duration d) {
        if (!isFinite(d)) return "--:--";
        int total = (int) d.toSeconds();
        int h = total / 3600, m = (total % 3600) / 60, s = total % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private void showPlainAlert(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Media Player");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.setGraphic(null);
        alert.initOwner(stage);
        alert.show();
    }

    /**
     * Draws a synthwave / cyberpunk scene: gradient sky, twinkling stars, a
     * slit-scan sun sitting on the horizon, a silhouette city skyline with
     * neon windows, an animated perspective grid floor, and subtle scanlines.
     *
     * Shown behind the MediaView, so it also fills the letterbox area around
     * video and acts as the artwork panel while audio-only tracks play.
     */
    // drawing a synthwave cyberpunk scene with a slit-scan sun, twinkling stars, and a neon city skyline
    private static final class CyberpunkBackdrop extends Pane {

        private final Canvas canvas = new Canvas();
        private double phase = 0;

        CyberpunkBackdrop() {
            getStyleClass().add("cyber-backdrop");
            setMouseTransparent(true);
            canvas.setManaged(false);
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());
            getChildren().add(canvas);

            new AnimationTimer() {
                private long last = 0;
                @Override public void handle(long now) {
                    if (last == 0) { last = now; return; }
                    phase += Math.min(0.05, (now - last) / 1_000_000_000.0);
                    last = now;
                    render();
                }
            }.start();
        }

        private void render() {
            double w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            GraphicsContext g = canvas.getGraphicsContext2D();
            g.clearRect(0, 0, w, h);
            double horizon = h * 0.62;

            // Sky gradient.
            g.setFill(new LinearGradient(0, 0, 0, horizon, false, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.web("#04050a")),
                    new Stop(0.55, Color.web("#160a33")),
                    new Stop(1.00, Color.web("#42093f"))));
            g.fillRect(0, 0, w, horizon);

            // Twinkling stars.
            for (int i = 0; i < 70; i++) {
                double sx = pseudo(i * 1.7) * w;
                double sy = pseudo(i * 2.3) * horizon * 0.72;
                double tw = 0.5 + 0.5 * Math.sin(phase * 1.6 + i * 1.3);
                g.setGlobalAlpha(0.20 + 0.55 * tw);
                g.setFill(Color.web("#a7e8ff"));
                g.fillOval(sx, sy, 1.6, 1.6);
            }
            g.setGlobalAlpha(1);

            // Slit-scan sun with a soft outer glow.
            double sunR = Math.min(w, h) * 0.23, sunCx = w * 0.5, sunCy = horizon - sunR * 0.12;

            g.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.web("#ff2bd6", 0.55)),
                    new Stop(0.55, Color.web("#ff2bd6", 0.15)),
                    new Stop(1.00, Color.web("#ff2bd6", 0.00))));
            g.fillOval(sunCx - sunR * 2.2, sunCy - sunR * 2.2, sunR * 4.4, sunR * 4.4);

            g.save();
            g.beginPath();
            g.rect(0, 0, w, horizon);
            g.clip();

            g.setFill(new LinearGradient(0, sunCy - sunR, 0, sunCy + sunR, false, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.web("#ffe066")),
                    new Stop(0.45, Color.web("#ff6ec7")),
                    new Stop(1.00, Color.web("#7b2ff7"))));
            g.fillOval(sunCx - sunR, sunCy - sunR, sunR * 2, sunR * 2);

            // Horizontal slits cut out of the lower half of the sun.
            g.setFill(Color.web("#04050a"));
            for (int i = 0; i < 11; i++) {
                g.fillRect(sunCx - sunR, sunCy - sunR * 0.05 + i * (sunR * 0.15), sunR * 2, 2 + i * 1.15);
            }
            g.restore();

            drawSkyline(g, w, horizon);

            // Floor.
            g.setFill(new LinearGradient(0, horizon, 0, h, false, CycleMethod.NO_CYCLE,
                    new Stop(0.00, Color.web("#0a0518")),
                    new Stop(1.00, Color.web("#1c0a30"))));
            g.fillRect(0, horizon, w, h - horizon);

            // Perspective grid: horizontal lines drift toward the viewer.
            g.setStroke(Color.web("#00f0ff", 0.55));
            g.setLineWidth(1);
            double speed = (phase * 0.35) % 1.0;
            for (int i = 0; i < 18; i++) {
                double t = (i + speed) / 18.0;
                double yy = horizon + (h - horizon) * t * t;
                g.setGlobalAlpha(0.22 + 0.55 * t);
                g.strokeLine(0, yy, w, yy);
            }

            // Perspective grid: vertical lines converging at the horizon.
            double cx = w * 0.5;
            g.setGlobalAlpha(0.45);
            for (int i = -22; i <= 22; i++) {
                g.strokeLine(cx, horizon, cx + (i / 22.0) * w * 1.4, h);
            }
            g.setGlobalAlpha(1);

            // Glowing horizon line.
            g.setStroke(Color.web("#00f0ff", 0.9));
            g.setLineWidth(2);
            g.strokeLine(0, horizon, w, horizon);

            // Subtle scanlines over the whole scene.
            g.setGlobalAlpha(0.07);
            g.setFill(Color.BLACK);
            for (double yy = 0; yy < h; yy += 3) g.fillRect(0, yy, w, 1);
            g.setGlobalAlpha(1);
        }

        private void drawSkyline(GraphicsContext g, double w, double horizon) {
            // Far layer: flat silhouettes.
            for (int i = 0; i < 16; i++) {
                double t = pseudo(i * 3.1);
                g.setFill(Color.web("#0a0418"));
                g.fillRect((i / 16.0) * w + (t - 0.5) * 20, horizon - (30 + pseudo(i * 5.7) * 110),
                        w / 16.0 * (0.5 + t * 0.7), 30 + pseudo(i * 5.7) * 110);
            }

            // Near layer: taller buildings with randomly lit neon windows.
            for (int i = 0; i < 16; i++) {
                double t = pseudo(i * 4.3);
                double bw = w / 16.0 * (0.6 + t * 0.5);
                double bx = (i / 16.0) * w + (t - 0.5) * 24;
                double bh = 20 + pseudo(i * 6.9) * 80;
                double by = horizon - bh;

                g.setFill(Color.web("#05030f"));
                g.fillRect(bx, by, bw, bh);

                int cols = (int) Math.max(2, bw / 9), rows = (int) Math.max(2, bh / 12);
                for (int c = 0; c < cols; c++) {
                    for (int r = 0; r < rows; r++) {
                        double nz = pseudo(i * 13 + c * 7 + r * 31);
                        if (nz > 0.55) {
                            g.setGlobalAlpha(0.40 + 0.60 * nz);
                            g.setFill(nz > 0.85 ? Color.web("#ff2bd6") : Color.web("#00f0ff"));
                            g.fillRect(bx + 3 + c * (bw - 6) / cols, by + 4 + r * (bh - 8) / rows, 2.2, 3.2);
                        }
                    }
                }
            }
            g.setGlobalAlpha(1);
        }

        private static double pseudo(double x) {
            double s = Math.sin(x * 12.9898) * 43758.5453;
            return s - Math.floor(s);
        }
    }

    // A custom seek bar that draws animated neon bars instead of a plain slider.
    private static final class NeonSeekBar extends Pane {

        private static final int BAR_COUNT = 58;

        private final Canvas canvas = new Canvas();
        private final double[] level = new double[BAR_COUNT];
        private final double[] noise = new double[BAR_COUNT];

        private double max = 1, value = 0, phase = 0, energy = 0.18;
        private boolean playing = false, dragging = false;

        NeonSeekBar() {
            getStyleClass().add("neon-seek");
            setPickOnBounds(true);
            canvas.setManaged(false);
            canvas.widthProperty().bind(widthProperty());
            canvas.heightProperty().bind(heightProperty());
            getChildren().add(canvas);
            setMinHeight(62);
            setPrefHeight(62);
            setMaxHeight(62);

            for (int i = 0; i < BAR_COUNT; i++) {
                double s = Math.sin(i * 12.9898) * 43758.5453;
                noise[i] = s - Math.floor(s);
            }

            // Filters run before the handlers set by the owning class, so the
            // value is already up to date when onMouseReleased seeks the player.
            addEventFilter(MouseEvent.MOUSE_PRESSED, e -> { dragging = true; seekFromX(e.getX()); });
            addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> { if (dragging) seekFromX(e.getX()); });
            addEventFilter(MouseEvent.MOUSE_RELEASED, e -> dragging = false);

            new AnimationTimer() {
                private long last = 0;
                @Override public void handle(long now) {
                    if (last == 0) { last = now; return; }
                    double dt = Math.min(0.05, (now - last) / 1_000_000_000.0);
                    last = now;
                    phase += dt;
                    step(dt);
                    render();
                }
            }.start();
        }

        void setPlaying(boolean p) { this.playing = p; }
        double getValue() { return value; }
        void setValue(double v) { value = Math.max(0, Math.min(max, v)); }

        void setMax(double m) {
            max = (m <= 0 || Double.isNaN(m) || Double.isInfinite(m)) ? 1 : m;
            value = Math.min(value, max);
        }

        private void seekFromX(double x) {
            double w = getWidth();
            if (w <= 0 || isDisabled()) return;
            value = Math.max(0, Math.min(1, x / w)) * max;
        }

        private void step(double dt) {
            energy += ((playing ? 1.0 : 0.22) - energy) * Math.min(1, dt * 2.5);
            double centre = (BAR_COUNT - 1) / 2.0;
            for (int i = 0; i < BAR_COUNT; i++) {
                double n = noise[i];
                double v = 0.42
                        + 0.26 * Math.sin(phase * (2.0 + n * 3.2) + i * 0.62)
                        + 0.16 * Math.sin(phase * (5.4 + n * 4.5) - i * 1.27)
                        + 0.10 * Math.sin(phase * 11.3 + i * 2.11);
                double edge = 1.0 - 0.45 * Math.abs(i - centre) / centre;
                v = Math.max(0.05, Math.min(1.0, v * edge)) * (0.35 + 0.65 * energy);
                level[i] += (v - level[i]) * Math.min(1, dt * (v > level[i] ? 16 : 5)); // fast attack / slow release
            }
        }

        private void render() {
            double w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            GraphicsContext g = canvas.getGraphicsContext2D();
            g.clearRect(0, 0, w, h);

            double progress = max > 0 ? Math.max(0, Math.min(1, value / max)) : 0;
            double playheadX = progress * w;
            double slot = w / BAR_COUNT, barW = Math.max(2, slot * 0.55);
            double mid = h / 2.0, maxBar = h * 0.84;

            // Centre line.
            g.setGlobalAlpha(0.35);
            g.setFill(Color.web("#00f0ff"));
            g.fillRect(0, mid - 0.5, w, 1);
            g.setGlobalAlpha(1);

            for (int i = 0; i < BAR_COUNT; i++) {
                double cx = i * slot + slot / 2.0;
                double bh = Math.max(3, level[i] * maxBar);
                double x = cx - barW / 2.0, y = mid - bh / 2.0;
                boolean played = cx <= playheadX;
                Color core = played ? playedColor(i) : idleColor(i);

                g.setGlobalAlpha(played ? 0.25 : 0.12);
                g.setFill(core);
                g.fillRoundRect(x - 2, y - 2, barW + 4, bh + 4, 8, 8);

                g.setGlobalAlpha(played ? 0.95 : 0.42);
                g.setFill(core);
                g.fillRoundRect(x, y, barW, bh, 5, 5);
            }
            g.setGlobalAlpha(1);

            // Play head.
            g.setGlobalAlpha(0.28);
            g.setFill(Color.web("#00f0ff"));
            g.fillRect(playheadX - 5, 3, 10, h - 6);
            g.setGlobalAlpha(0.95);
            g.setFill(Color.WHITE);
            g.fillRect(playheadX - 1, 3, 2, h - 6);
            g.setGlobalAlpha(1);
        }

        private static Color playedColor(int i) {
            return Color.web("#00f0ff").interpolate(Color.web("#ff2bd6"), (double) i / (BAR_COUNT - 1));
        }

        private static Color idleColor(int i) {
            return Color.web("#123a52").interpolate(Color.web("#3a1745"), (double) i / (BAR_COUNT - 1));
        }
    }

    // the css
    private static String buildStylesheet() {
        String css =
            ".root { -fx-background-color: transparent; -fx-font-family: \"Segoe UI\", \"Helvetica Neue\", Arial; -fx-font-size: 13px; }\n" +
            ".app-root { -fx-background-color: transparent; }\n" +
            ".background-layer { -fx-background-color: radial-gradient(center 18% 10%, radius 75%, rgba(0,240,255,0.16), transparent), radial-gradient(center 88% 90%, radius 70%, rgba(255,43,214,0.16), transparent), linear-gradient(to bottom right, #04050a, #090d1a 45%, #14061f); }\n" +
            ".vignette { -fx-background-color: radial-gradient(center 50% 45%, radius 85%, transparent 28%, rgba(0,0,0,0.75) 100%); }\n" +
            ".label { -fx-text-fill: #b9c6d6; }\n" +
            ".title-label { -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #eafcff; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.9), 16, 0.35, 0, 0); }\n" +
            ".status-label { -fx-text-fill: #64768c; -fx-font-size: 11.5px; }\n" +
            ".time-label { -fx-text-fill: #00f0ff; -fx-font-size: 13px; -fx-font-family: \"Consolas\", \"Courier New\", monospace; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.7), 12, 0.35, 0, 0); }\n" +
            ".overlay-label { -fx-text-fill: #cdf6ff; -fx-font-size: 19px; -fx-text-alignment: center; -fx-alignment: center; -fx-background-color: rgba(5, 9, 18, 0.55); -fx-background-radius: 14; -fx-border-color: rgba(0,240,255,0.45); -fx-border-radius: 14; -fx-border-width: 1; -fx-padding: 22 30 22 30; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.45), 26, 0.30, 0, 0); }\n" +
            ".glyph { -fx-text-fill: #cdf6ff; -fx-font-size: 15px; }\n" +
            ".primary-button .glyph { -fx-text-fill: #ffe9fb; }\n" +
            ".video-pane { -fx-background-color: transparent; -fx-border-color: rgba(0,240,255,0.28); -fx-border-width: 1; }\n" +
            ".cyber-backdrop { -fx-background-color: transparent; }\n" +
            ".playlist-panel { -fx-background-color: rgba(7,11,20,0.74); -fx-border-color: transparent transparent transparent rgba(0,240,255,0.40); -fx-border-width: 0 0 0 1; }\n" +
            ".control-bar { -fx-background-color: rgba(7,11,20,0.80); -fx-border-color: rgba(0,240,255,0.40) transparent transparent transparent; -fx-border-width: 1 0 0 0; }\n" +
            ".neon-underline { -fx-pref-height: 1; -fx-background-color: linear-gradient(to right, rgba(0,240,255,0.9), rgba(255,43,214,0.9), transparent); }\n" +
            ".icon-button { -fx-background-color: rgba(0,240,255,0.07); -fx-border-color: rgba(0,240,255,0.35); -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 13 8 13; -fx-cursor: hand; }\n" +
            ".icon-button:hover { -fx-background-color: rgba(0,240,255,0.18); -fx-border-color: #00f0ff; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.75), 18, 0.3, 0, 0); }\n" +
            ".icon-button:pressed { -fx-background-color: rgba(0,240,255,0.32); }\n" +
            ".primary-button { -fx-background-color: rgba(255,43,214,0.14); -fx-border-color: rgba(255,43,214,0.70); -fx-effect: dropshadow(gaussian, rgba(255,43,214,0.55), 14, 0.25, 0, 0); }\n" +
            ".primary-button:hover { -fx-background-color: rgba(255,43,214,0.30); -fx-border-color: #ff2bd6; -fx-effect: dropshadow(gaussian, rgba(255,43,214,0.95), 24, 0.35, 0, 0); }\n" +
            ".primary-button:pressed { -fx-background-color: rgba(255,43,214,0.45); }\n" +
            ".neon-seek { -fx-background-color: rgba(0,240,255,0.035); -fx-background-radius: 10; -fx-border-color: rgba(0,240,255,0.22); -fx-border-width: 1; -fx-border-radius: 10; }\n" +
            ".neon-seek:hover { -fx-border-color: rgba(0,240,255,0.55); }\n" +
            ".neon-seek:disabled { -fx-opacity: 1; }\n" +
            ".list-view { -fx-background-color: transparent; -fx-control-inner-background: transparent; -fx-background-insets: 0; -fx-padding: 0; }\n" +
            ".list-view .virtual-flow .clipped-container .sheet { -fx-background-color: transparent; }\n" +
            ".list-view .placeholder .label { -fx-text-fill: #4c5b70; }\n" +
            ".list-cell { -fx-background-color: transparent; -fx-text-fill: #9fb3c8; -fx-padding: 9 10 9 10; -fx-border-color: transparent transparent rgba(0,240,255,0.10) transparent; -fx-border-width: 0 0 1 0; }\n" +
            ".list-cell:filled:hover { -fx-background-color: rgba(0,240,255,0.08); -fx-text-fill: #eafcff; }\n" +
            ".list-cell:filled:selected { -fx-background-color: rgba(255,43,214,0.16); -fx-text-fill: #ffffff; -fx-border-color: transparent transparent rgba(255,43,214,0.55) transparent; }\n" +
            ".list-cell.now-playing { -fx-text-fill: #00f0ff; -fx-font-weight: bold; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.8), 12, 0.3, 0, 0); }\n" +
            ".slider .track { -fx-background-color: rgba(0,240,255,0.18); -fx-background-radius: 3; -fx-pref-height: 4; }\n" +
            ".slider .thumb { -fx-background-color: #00f0ff; -fx-background-radius: 9; -fx-padding: 7; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.9), 14, 0.4, 0, 0); }\n" +
            ".slider:disabled .thumb { -fx-background-color: #3c4a5c; -fx-effect: none; }\n" +
            ".scroll-bar:vertical { -fx-background-color: transparent; -fx-pref-width: 9; }\n" +
            ".scroll-bar:vertical .track { -fx-background-color: transparent; }\n" +
            ".scroll-bar:vertical .thumb { -fx-background-color: rgba(0,240,255,0.35); -fx-background-radius: 5; }\n" +
            ".scroll-bar:vertical .thumb:hover { -fx-background-color: rgba(0,240,255,0.75); }\n" +
            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-padding: 0; }\n" +
            ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: \" \"; -fx-padding: 0; }\n" +
            ".tooltip { -fx-background-color: rgba(5,9,16,0.96); -fx-text-fill: #cdf6ff; -fx-border-color: rgba(0,240,255,0.55); -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 11.5px; -fx-effect: dropshadow(gaussian, rgba(0,240,255,0.55), 14, 0.3, 0, 0); }\n" +
            ".dialog-pane { -fx-background-color: #0b101c; -fx-border-color: rgba(0,240,255,0.5); -fx-border-width: 1; }\n" +
            ".dialog-pane .label { -fx-text-fill: #cdf6ff; }\n" +
            ".dialog-pane .header-panel { -fx-background-color: #0f1524; }\n" +
            ".dialog-pane .header-panel .label { -fx-text-fill: #00f0ff; -fx-font-size: 14px; -fx-font-weight: bold; }\n" +
            ".dialog-pane .button { -fx-background-color: rgba(0,240,255,0.12); -fx-text-fill: #eafcff; -fx-border-color: rgba(0,240,255,0.5); -fx-border-radius: 6; -fx-background-radius: 6; }\n" +
            ".dialog-pane .button:hover { -fx-background-color: rgba(0,240,255,0.28); }\n";

        return "data:text/css;base64,"
                + Base64.getEncoder().encodeToString(css.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void stop() {
        disposePlayer();
    }

    public static void main(String[] args) {
        launch(args);
    }
}