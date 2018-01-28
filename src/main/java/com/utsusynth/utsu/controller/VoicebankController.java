package com.utsusynth.utsu.controller;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.util.ResourceBundle;
import org.apache.commons.io.FileUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.utsusynth.utsu.common.UndoService;
import com.utsusynth.utsu.common.data.AddResponse;
import com.utsusynth.utsu.common.data.NoteData;
import com.utsusynth.utsu.common.data.RemoveResponse;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;
import com.utsusynth.utsu.common.i18n.Localizable;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.files.Ust12Reader;
import com.utsusynth.utsu.files.Ust12Writer;
import com.utsusynth.utsu.files.Ust20Reader;
import com.utsusynth.utsu.files.Ust20Writer;
import com.utsusynth.utsu.model.song.SongContainer;
import com.utsusynth.utsu.view.song.SongCallback;
import com.utsusynth.utsu.view.song.SongEditor;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * 'VoicebankScene.fxml' Controller Class
 */
public class VoicebankController implements EditorController, Localizable {
    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();

    // User session data goes here.
    private EditorCallback callback;

    // Helper classes go here.
    private final SongContainer songContainer;
    private final SongEditor track;
    private final Localizer localizer;
    private final UndoService undoService;
    private final Ust12Reader ust12Reader;
    private final Ust20Reader ust20Reader;
    private final Ust12Writer ust12Writer;
    private final Ust20Writer ust20Writer;
    private final Provider<FXMLLoader> fxmlLoaderProvider;

    @FXML // fx:id="scrollPaneRight"
    private ScrollPane scrollPaneRight; // Value injected by FXMLLoader

    @FXML // fx:id="anchorRight"
    private AnchorPane anchorRight; // Value injected by FXMLLoader

    @FXML // fx:id="scrollPaneBottom"
    private ScrollPane scrollPaneBottom; // Value injected by FXMLLoader

    @FXML // fx:id="anchorBottom"
    private AnchorPane anchorBottom; // Value injected by FXMLLoader

    @FXML // fx:id="voicebankImage"
    private ImageView voicebankImage; // Value injected by FXMLLoader

    @Inject
    public VoicebankController(
            SongContainer songContainer, // Inject an empty song.
            SongEditor track,
            Localizer localizer,
            UndoService undoService,
            Ust12Reader ust12Reader,
            Ust20Reader ust20Reader,
            Ust12Writer ust12Writer,
            Ust20Writer ust20Writer,
            Provider<FXMLLoader> fxmlLoaders) {
        this.songContainer = songContainer;
        this.track = track;
        this.localizer = localizer;
        this.undoService = undoService;
        this.ust12Reader = ust12Reader;
        this.ust20Reader = ust20Reader;
        this.ust12Writer = ust12Writer;
        this.ust20Writer = ust20Writer;
        this.fxmlLoaderProvider = fxmlLoaders;
    }

    // Provide setup for other frontend song management.
    // This is called automatically when fxml loads.
    public void initialize() {
        DoubleProperty scrollbarTracker = new SimpleDoubleProperty();
        scrollbarTracker.bind(scrollPaneRight.hvalueProperty());
        track.initialize(new SongCallback() {
            @Override
            public AddResponse addNote(NoteData toAdd) throws NoteAlreadyExistsException {
                onSongChange();
                return songContainer.getSong().addNote(toAdd);
            }

            @Override
            public RemoveResponse removeNote(int position) {
                onSongChange();
                return songContainer.getSong().removeNote(position);
            }

            @Override
            public void modifyNote(NoteData toModify) {
                onSongChange();
                songContainer.getSong().modifyNote(toModify);
            }

            @Override
            public SongController.Mode getCurrentMode() {
                return null;
            }

            @Override
            public void adjustScrollbar(double oldWidth, double newWidth) {
                // Note down what scrollbar position will be next time anchorRight's width changes.
                double scrollPosition =
                        scrollPaneRight.getHvalue() * (oldWidth - scrollPaneRight.getWidth());
                scrollbarTracker.unbind();
                scrollbarTracker.set(scrollPosition / (newWidth - scrollPaneRight.getWidth()));
            }
        });
        anchorRight.widthProperty().addListener(observable -> {
            // Sync up the scrollbar's position with where the track thinks it should be.
            if (!scrollbarTracker.isBound()) {
                scrollPaneRight.setHvalue(scrollbarTracker.get());
                scrollbarTracker.bind(scrollPaneRight.hvalueProperty());
            }
        });

        refreshView();

        // Set up localization.
        localizer.localize(this);
    }

    @FXML
    private Label nameLabel; // Value injected by FXMLLoader
    @FXML
    private Label authorLabel; // Value injected by FXMLLoader
    @FXML
    private Button changeNameButton; // Value injected by FXMLLoader
    @FXML
    private Button changeAuthorButton; // Value injected by FXMLLoader

    @Override
    public void localize(ResourceBundle bundle) {
        nameLabel.setText(bundle.getString("top.mode"));
        authorLabel.setText(bundle.getString("top.quantization"));
        nameLabel.setText(bundle.getString("top.render"));
        authorLabel.setText(bundle.getString("top.exportWav"));
    }

    @Override
    public void refreshView() {
        // Set song image.
        Image image = new Image("file:" + songContainer.getSong().getVoicebank().getImagePath());
        voicebankImage.setImage(image);

        // Reloads current
        anchorRight.getChildren().clear();
        anchorRight.getChildren().add(track.createNewTrack(songContainer.getSong().getNotes()));
        anchorRight.getChildren().add(track.getNotesElement());
        anchorRight.getChildren().add(track.getPitchbendsElement());
        anchorRight.getChildren().add(track.getPlaybackElement());
        anchorBottom.getChildren().clear();
        anchorBottom.getChildren().add(track.getDynamicsElement());
        anchorBottom.getChildren().add(track.getEnvelopesElement());
    }

    @Override
    public void openEditor(EditorCallback callback) {
        this.callback = callback;
    }

    @Override
    public String openFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select UST File");
        fc.getExtensionFilters().addAll(
                new ExtensionFilter("UST files", "*.ust"),
                new ExtensionFilter("All files", "*.*"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            try {
                String saveFormat; // Format to save this song in the future.
                String charset = "UTF-8";
                CharsetDecoder utf8Decoder = Charset.forName("UTF-8").newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                try {
                    utf8Decoder.decode(ByteBuffer.wrap(FileUtils.readFileToByteArray(file)));
                } catch (MalformedInputException | UnmappableCharacterException e) {
                    charset = "SJIS";
                }
                String content = FileUtils.readFileToString(file, charset);
                if (content.contains("UST Version1.2")) {
                    songContainer.setSong(ust12Reader.loadSong(content));
                    saveFormat = "UST 1.2 (Shift JIS)";
                } else if (content.contains("UST Version2.0")) {
                    songContainer.setSong(ust20Reader.loadSong(content));
                    saveFormat = "UST 2.0 " + (charset.equals("UTF-8") ? "(UTF-8)" : "(Shift JIS)");
                } else {
                    // TODO: Deal with this error.
                    System.out.println("UST format not found!");
                    return "*Untitled";
                }
                undoService.clearActions();
                callback.enableSave(false);
                songContainer.setLocation(file);
                songContainer.setSaveFormat(saveFormat);
                refreshView();
                return file.getName();
            } catch (IOException e) {
                // TODO Handle this.
                errorLogger.logError(e);
            }
        }
        return "*Untitled";
    }

    @Override
    public String saveFile() {
        callback.enableSave(false);
        if (songContainer.hasPermanentLocation()) {
            String saveFormat = songContainer.getSaveFormat();
            String charset = "UTF-8";
            if (saveFormat.contains("Shift JIS")) {
                charset = "SJIS";
            }
            File saveLocation = songContainer.getLocation();
            try (PrintStream ps = new PrintStream(saveLocation, charset)) {
                if (saveFormat.contains("UST 1.2")) {
                    ust12Writer.writeSong(songContainer.getSong(), ps);
                } else {
                    ust20Writer.writeSong(songContainer.getSong(), ps, charset);
                }
                ps.close();
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                // TODO: Handle this.
                errorLogger.logError(e);
            }
            return saveLocation.getName();
        }
        return "*Untitled";
    }

    @Override
    public String saveFileAs() {
        callback.enableSave(false);
        FileChooser fc = new FileChooser();
        fc.setTitle("Select UST File");
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            fc.getExtensionFilters().addAll(
                    new ExtensionFilter("UST 2.0 (UTF-8)", "*.ust"),
                    new ExtensionFilter("UST 2.0 (Shift JIS)", "*.ust"),
                    new ExtensionFilter("UST 1.2 (Shift JIS)", "*.ust"));
        } else {
            // For now, default to 1.2 format for Windows and Linux users.
            fc.getExtensionFilters().addAll(
                    new ExtensionFilter("UST 1.2 (Shift JIS)", "*.ust"),
                    new ExtensionFilter("UST 2.0 (UTF-8)", "*.ust"),
                    new ExtensionFilter("UST 2.0 (Shift JIS)", "*.ust"));
        }
        File file = fc.showSaveDialog(null);
        if (file != null) {
            ExtensionFilter chosenFormat = fc.getSelectedExtensionFilter();
            String charset = "UTF-8";
            if (chosenFormat.getDescription().contains("Shift JIS")) {
                charset = "SJIS";
            }
            try (PrintStream ps = new PrintStream(file, charset)) {
                if (chosenFormat.getDescription().contains("UST 1.2")) {
                    ust12Writer.writeSong(songContainer.getSong(), ps);
                } else {
                    ust20Writer.writeSong(songContainer.getSong(), ps, charset);
                }
                ps.close();
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                // TODO: Handle this.
                errorLogger.logError(e);
            }
            songContainer.setLocation(file);
            songContainer.setSaveFormat(chosenFormat.getDescription());
            return file.getName();
        }
        // Default file name.
        return "*Untitled";
    }

    /** Called whenever a Song is changed. */
    private void onSongChange() {
        if (songContainer.hasPermanentLocation()) {
            callback.enableSave(true);
        } else {
            callback.enableSave(false);
        }
    }

    @Override
    public void openProperties() {
        // Open properties modal.
        InputStream fxml = getClass().getResourceAsStream("/fxml/PropertiesScene.fxml");
        FXMLLoader loader = fxmlLoaderProvider.get();
        try {
            Stage currentStage = (Stage) anchorRight.getScene().getWindow();
            Stage propertiesWindow = new Stage();
            propertiesWindow.initModality(Modality.APPLICATION_MODAL);
            propertiesWindow.initOwner(currentStage);
            BorderPane propertiesPane = loader.load(fxml);
            PropertiesController controller = (PropertiesController) loader.getController();
            controller.setSongContainer(songContainer);
            propertiesWindow.setScene(new Scene(propertiesPane));
            propertiesWindow.showAndWait();
        } catch (IOException e) {
            // TODO Handle this.
            errorLogger.logError(e);
        }
        refreshView();
    }
}
