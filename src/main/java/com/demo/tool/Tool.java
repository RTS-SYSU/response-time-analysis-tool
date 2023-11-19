package com.demo.tool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.kordamp.bootstrapfx.BootstrapFX;



import java.io.IOException;

public class Tool extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Tool.class.getResource("exterior.fxml"));
        Parent root = fxmlLoader.load();
        var sence = new Scene(root);
        sence.getStylesheets().add(BootstrapFX.bootstrapFXStylesheet());
        primaryStage.setScene(sence);
        primaryStage.show();
    }

    public static void main() {
        launch();
    }
}