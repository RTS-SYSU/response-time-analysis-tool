package com.demo.tool;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class Controller {
    @FXML
    private ComboBox<?> allocation;

    @FXML
    private TextField coreNum;

    @FXML
    private TextField maxAccess;

    @FXML
    private ComboBox<?> priority;

    @FXML
    private TextField rangeCSL;

    @FXML
    private TextField rangeT;

    @FXML
    private VBox recvFactor;

    @FXML
    private StackPane recvFactorBtn;

    @FXML
    private TextField taskNum;

    @FXML
    private TextField utility;

}