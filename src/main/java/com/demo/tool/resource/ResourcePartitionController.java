package com.demo.tool.resource;

import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class ResourcePartitionController extends StackPane {
    private Label idLabel;
    private int id;
    public StackPane root;

    public Label getPartitionId() {
        return idLabel;
    }

    public int getID() {
        return id;
    }

    public void setID(int pID) {
        pID = id;
    }

    public ResourcePartitionController() {
        root = new StackPane();
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("resource-partition.fxml"));
            fxmlLoader.setController(this);
            root.getChildren().add(fxmlLoader.load());
            idLabel = (Label) fxmlLoader.getNamespace().get("partitionId");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        getChildren().add(root);
    }

    public void onRespond(String style) {
        if (!root.getChildren().get(0).getStyleClass().contains(style)) {
            if (root.getChildren().get(0).getStyleClass().contains("basic"))
                root.getChildren().get(0).getStyleClass().remove("basic");
            root.getChildren().get(0).getStyleClass().add(style); // 添加单击样式类
        }
    }


    public void backToNormal(String style) {
        if (root.getChildren().get(0).getStyleClass().contains(style)) {
            root.getChildren().get(0).getStyleClass().remove(style);
            root.getChildren().get(0).getStyleClass().add("basic");
        }
        HBox outer = (HBox) root.getParent().getParent();
        for (int j = 0; j < outer.getChildren().size(); j++) {
            ResourcePartitionController otherRpc = (ResourcePartitionController) outer.getChildren().get(j);
            StackPane otherRoot = (StackPane) otherRpc.getChildren().get(0);
            if (otherRoot != root)
                otherRoot.getChildren().get(0).getStyleClass().remove(style);
        }
    }

}

