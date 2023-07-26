package com.demo.tool;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

import java.io.IOException;

public class Tool extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(Tool.class.getResource("exterior.fxml"));
        Parent root = fxmlLoader.load();


        ComboBox<String> comboBox =(ComboBox)fxmlLoader.getNamespace().get("allocation");
        comboBox.setItems(FXCollections.observableArrayList(
                "Option 1", "Option 2", "Option 3", "Option 4", "Option 5"
        ));
//        HBox full = (HBox) fxmlLoader.getNamespace().get("asda");
//        Stage stage = (Stage) full.getScene().getWindow();
//        stage.setFullScreen(!stage.isFullScreen());
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}