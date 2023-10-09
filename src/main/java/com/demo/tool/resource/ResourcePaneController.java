package com.demo.tool.resource;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class ResourcePaneController extends StackPane {
    private int id;
    private Label idLabel;
    private Label csl;
    private Label taskAccessTime;
    public StackPane root;

    public int getID() {
        return id;
    }

    public void setID(int pID) {
        id = pID;
    }

    public Label getResourceId() {
        return idLabel;
    }

    public Label getCsl() {
        return csl;
    }

    public Label getTaskAccessTime() {
        return taskAccessTime;
    }

    public ResourcePaneController() {
        root = new StackPane();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("resource-pane.fxml"));
            fxmlLoader.setController(this);
            root.getChildren().add(fxmlLoader.load());
            idLabel = (Label) fxmlLoader.getNamespace().get("resourceId");
            csl = (Label) fxmlLoader.getNamespace().get("csl");
            taskAccessTime = (Label) fxmlLoader.getNamespace().get("accessTime");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        getChildren().add(root);
    }

    public int onPaneClicked() {
        if (root.getChildren().get(0).getStyleClass().contains("resource-clicked-change-all")) {
            root.getChildren().get(0).getStyleClass().remove("resource-clicked-change-all"); // 移除单击样式类
        } else {
            root.getChildren().get(0).getStyleClass().add("resource-clicked-change-all"); // 添加单击样式类
        }

        HBox outer = (HBox) root.getParent().getParent();
        for (int j = 0; j < outer.getChildren().size(); j++) {
            ResourcePaneController otherRpc = (ResourcePaneController) outer.getChildren().get(j);
            StackPane otherRoot = (StackPane) otherRpc.getChildren().get(0);
            if (otherRoot != root) otherRoot.getChildren().get(0).getStyleClass().remove("resource-clicked-change-all");
        }
        return id;
    }

    public void onRespondTask() {
        if (!root.getChildren().get(0).getStyleClass().contains("resource-change-when-task-clicked")) {
            if (root.getChildren().get(0).getStyleClass().contains("resource-clicked-change-all"))
                root.getChildren().get(0).getStyleClass().remove("resource-clicked-change-all");
//            if (root.getChildren().get(0).getStyleClass().contains("basic"))
//                root.getChildren().get(0).getStyleClass().remove("basic");
            root.getChildren().get(0).getStyleClass().add("resource-change-when-task-clicked"); // 添加单击样式类
        }
    }

    public void backToNormal(String style) {
        if (root.getChildren().get(0).getStyleClass().contains(style))
            root.getChildren().get(0).getStyleClass().remove(style);
//        root.getChildren().get(0).getStyleClass().add("basic");
        taskAccessTime.setText("");

        HBox outer = (HBox) root.getParent().getParent();
        for (int j = 0; j < outer.getChildren().size(); j++) {
            ResourcePaneController otherRpc = (ResourcePaneController) outer.getChildren().get(j);
            otherRpc.getTaskAccessTime().setText("");
            StackPane otherRoot = (StackPane) otherRpc.getChildren().get(0);
            if (otherRoot != root) {
                otherRoot.getChildren().get(0).getStyleClass().remove(style);
            }
        }
    }
}

