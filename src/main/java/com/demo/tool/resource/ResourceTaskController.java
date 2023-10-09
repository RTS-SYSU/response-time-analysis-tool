// Temporarily Abandoned

// package com.demo.tool.resource;
//
//import javafx.fxml.FXMLLoader;
//import javafx.scene.control.Label;
//import javafx.scene.layout.StackPane;
//
//import java.io.IOException;
//
//public class ResourceTaskController extends StackPane {
//    private Label id;
//    private String name;
//    public StackPane root;
//
//    public Label getTaskId() {
//        return id;
//    }
//
//    public String getName() {
//        return name;
//    }
//
//    public void setName(String pName) {
//        pName = name;
//    }
//
//    public ResourceTaskController() {
//        root = new StackPane();
//        try {
//            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("resource-task.fxml"));
//            fxmlLoader.setController(this);
//            root.getChildren().add(fxmlLoader.load());
//            id = (Label) fxmlLoader.getNamespace().get("taskId");
//        } catch (IOException exception) {
//            throw new RuntimeException(exception);
//        }
//        getChildren().add(root);
//    }
//}
//
