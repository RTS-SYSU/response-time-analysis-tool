package com.demo.tool.resource;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class ResourcePaneController extends StackPane {
    private int id;
    private final Label idLabel;

    private Tooltip tipForCsl;
//    private final Label csl;
    private final Label taskAccessTime;
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

//    public Label getCsl() {
//        return csl;
//    }

    public Label getTaskAccessTime() { return taskAccessTime; }
    public Tooltip getTipForCsl() { return tipForCsl; }

    public ResourcePaneController(VBox page11) {
        root = new StackPane();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("resource-pane.fxml"));
            fxmlLoader.setController(this);
            root.getChildren().add(fxmlLoader.load());
            idLabel = (Label) fxmlLoader.getNamespace().get("resourceId");
//            csl = (Label) fxmlLoader.getNamespace().get("csl");
            taskAccessTime = (Label) fxmlLoader.getNamespace().get("accessTime");
            taskAccessTime.setText("--");
            tipForCsl = (Tooltip) fxmlLoader.getNamespace().get("cslT");

            tipForCsl.setShowDelay(javafx.util.Duration.millis(100));
            tipForCsl.setHideDelay(javafx.util.Duration.millis(100));
            // 添加悬浮框提示
            idLabel.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    Bounds screenBounds = page11.localToScreen(page11.getBoundsInLocal());
                    tipForCsl.show(idLabel, screenBounds.getCenterX() + idLabel.getWidth() * 0.4, screenBounds.getCenterY() - idLabel.getParent().getBoundsInLocal().getHeight() * 2.75);
                } else {
                    tipForCsl.hide();
                }
            });

        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        getChildren().add(root);
    }

    public int onPaneClicked() {
        VBox s = (VBox) root.getChildren().get(0);
        if (s.getChildren().get(0).getStyleClass().contains("resource-clicked-change-all")) {
            s.getChildren().get(0).getStyleClass().remove("resource-clicked-change-all"); // 移除单击样式类
        } else {
            s.getChildren().get(0).getStyleClass().add("resource-clicked-change-all"); // 添加单击样式类
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
        VBox s = (VBox) root.getChildren().get(0);
        if (!s.getChildren().get(0).getStyleClass().contains("resource-change-when-task-clicked")) {
            if (s.getChildren().get(0).getStyleClass().contains("resource-clicked-change-all"))
                s.getChildren().get(0).getStyleClass().remove("resource-clicked-change-all");
//            if (s.getChildren().get(0).getStyleClass().contains("basic"))
//                s.getChildren().get(0).getStyleClass().remove("basic");
            s.getChildren().get(0).getStyleClass().add("resource-change-when-task-clicked"); // 添加单击样式类
        }
    }

    public void backToNormal(String style) {
        VBox s = (VBox) root.getChildren().get(0);
        if (s.getChildren().get(0).getStyleClass().contains(style))
            s.getChildren().get(0).getStyleClass().remove(style);
//        s.getChildren().get(0).getStyleClass().add("basic");
        taskAccessTime.setText("--");
        tipForCsl.setText("length: --");

        HBox outer = (HBox) root.getParent().getParent();
        for (int j = 0; j < outer.getChildren().size(); j++) {
            ResourcePaneController otherRpc = (ResourcePaneController) outer.getChildren().get(j);
            otherRpc.getTaskAccessTime().setText("--");
            otherRpc.getTipForCsl().setText("length: --");
            StackPane otherRoot = (StackPane) otherRpc.getChildren().get(0);
            if (otherRoot != root) {
                otherRoot.getChildren().get(0).getStyleClass().remove(style);
            }
        }
    }
}

