package com.demo.tool;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import responseTimeTool.Analysis;
import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;
import responseTimeTool.utils.Factors;
import responseTimeTool.utils.Pair;

import java.util.ArrayList;

public class Controller {

    @FXML
    private ComboBox<String> RTM;

    @FXML
    private ComboBox<String> allocation;

    @FXML
    private TextField coreNum;

    @FXML
    private StackPane genRandom;

    @FXML
    private Region loading;

    @FXML
    private Region generating;

    @FXML
    private TextField maxAccess;

    @FXML
    private ComboBox<String> priority;

    @FXML
    private TextField rangeCSL1;

    @FXML
    private TextField rangeCSL2;

    @FXML
    private TextField rangeT1;

    @FXML
    private TextField rangeT2;

    @FXML
    private Label scheduleNO;

    @FXML
    private Label scheduleYES;

    @FXML
    private StackPane startAnalysis;

    @FXML
    private Region startIcon;

    @FXML
    private Label startLabel;

    @FXML
    private Label generateLabel;

    @FXML
    private ComboBox<String> systemMode;

    @FXML
    private TextField taskNum;

    @FXML
    private TextField utility;

    @FXML
    private TableView<Object[]> table1;

    @FXML
    private TableView<Object[]> table2;

    private Factors factors;

    private Pair<ArrayList<SporadicTask>, ArrayList<Resource>> pair;

    private boolean generateDone = false;

    @FXML
    void initialize() {
        initComBox();
        initAnalysis();
    }

    @FXML
    void initAnalysis() {
        factors = new Factors();
        pair = new Pair<>();
    }

    @FXML
    void initComBox() {
        allocation.getItems().addAll("WF", "BF", "FF", "NF");
        priority.getItems().addAll("EDF");
        priority.setValue("EDF");
        systemMode.getItems().addAll("LO", "HI", "ModeSwitch");
        RTM.getItems().addAll("MSRP");
        RTM.setValue("MSRP");
    }

    @FXML
    void fillTable1(ArrayList<SporadicTask> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();

        for (SporadicTask task : tasks) {
            // id, partition, priority, critical level, period, deadline, WCET, resource access, utilization
            Object[] row = new Object[]{task.id, task.partition, task.priority, task.critical == 0 ? "LO" : "HI",
                    task.period, task.deadline, task.WCET, task.resource_required_index, task.util};
            data.add(row);
        }
        // 设置数据源到 TableView
        table1.getItems().clear();
        table1.getItems().addAll(data);

        // 将数据源中的数据与每个 TableColumn 进行绑定
        for (int i = 0; i < table1.getColumns().size(); i++) {
            TableColumn<Object[], Object> column = (TableColumn<Object[], Object>) table1.getColumns().get(i);

            int columnIndex = i;
            // 使用 lambda 表达式设置 CellValueFactory
            column.setCellValueFactory(cellData -> {
                Object[] rowData = cellData.getValue();
                if (rowData.length > columnIndex) {
                    Object cellValue = rowData[columnIndex];
                    return cellValue != null ? new SimpleObjectProperty<>(cellValue) : null;
                }
                return null;
            });
        }
    }

    @FXML
    void fillTable2(ArrayList<SporadicTask> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();

        for (SporadicTask task : tasks) {
            // id, partition, priority, response time, deadline, WCET
            // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
            Object[] row = new Object[]{task.id, task.partition, task.priority, task.Ri, task.deadline, task.WCET,
                    task.pure_resource_execution_time, task.interference, task.spin, task.indirectspin, task.mrsp_arrivalblocking_overheads};
            data.add(row);
        }
        // 设置数据源到 TableView
        table2.getItems().clear();
        table2.getItems().addAll(data);

        // 将数据源中的数据与每个 TableColumn 进行绑定
        for (int i = 0; i < table2.getColumns().size(); i++) {
            TableColumn<Object[], Object> column = (TableColumn<Object[], Object>) table2.getColumns().get(i);

            int columnIndex = i;
            // 使用 lambda 表达式设置 CellValueFactory
            column.setCellValueFactory(cellData -> {
                Object[] rowData = cellData.getValue();
                if (rowData.length > columnIndex) {
                    Object cellValue = rowData[columnIndex];
                    return cellValue != null ? new SimpleObjectProperty<>(cellValue) : null;
                }
                return null;
            });
        }
    }

    @FXML
    void onStartAnalysis() {
        if (loading.isVisible()) return;
        if (!generateDone) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }
        if (systemMode.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText("System parameters are incomplete.");
            alert.showAndWait();
            return;
        }
        factors.SYSTEM_MODE = systemMode.getValue();

        /* set the start icon inaccessible */
        startIcon.setVisible(false);
        startLabel.setVisible(false);
        loading.setVisible(true);

        Analysis analysis = new Analysis();
        boolean schedulable = analysis.analysis(factors, pair.getFirst(), pair.getSecond());
        fillTable2(pair.getFirst());

        showSchedulable(schedulable);

        /* set the start icon accessible */
        startIcon.setVisible(true);
        startLabel.setVisible(true);
        loading.setVisible(false);
    }

    public String judgeInteger(TextField textField) {
        if (!textField.getText().matches("\\d+")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("WARNING");
            alert.setHeaderText(null);
            alert.setContentText("The input can only be a positive integer");
            alert.showAndWait();
            textField.clear();
            return "-1";
        }
        return textField.getText();
    }

    public String judgeFloat(TextField textField) {
        if (!textField.getText().matches("\\d*\\.?\\d+")) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("WARNING");
            alert.setHeaderText(null);
            alert.setContentText("The input can only be a floating-point number");
            alert.showAndWait();
            textField.clear();
            return "-1";
        }
        return textField.getText();
    }

    @FXML
    void onGenerate() {
        if (generating.isVisible()) return;

        initAnalysis();

        if (rangeT1.getText().isEmpty() || rangeT2.getText().isEmpty() || coreNum.getText().isEmpty() || rangeCSL1.getText().isEmpty() || rangeCSL2.getText().isEmpty()
                || maxAccess.getText().isEmpty() || taskNum.getText().isEmpty() || utility.getText().isEmpty() || allocation.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }

        factors.MIN_PERIOD = Integer.parseInt(judgeInteger(rangeT1));
        factors.MAX_PERIOD = Integer.parseInt(judgeInteger(rangeT2));
        factors.TOTAL_PARTITIONS = Integer.parseInt(judgeInteger(coreNum));

        factors.CL_RANGE_LOW = Integer.parseInt(judgeInteger(rangeCSL1));
        factors.CL_RANGE_HIGH = Integer.parseInt(judgeInteger(rangeCSL2));
        factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = Integer.parseInt(judgeInteger(maxAccess));
        factors.NUMBER_OF_TASKS = Integer.parseInt(judgeInteger(taskNum));
        factors.RESOURCE_SHARING_FACTOR = Double.parseDouble(judgeFloat(utility));
        factors.ALLOCATION = allocation.getValue();

        if (factors.MIN_PERIOD == -1 || factors.MAX_PERIOD == -1 || factors.TOTAL_PARTITIONS == -1 || factors.CL_RANGE_LOW == -1 || factors.CL_RANGE_HIGH == -1 ||
                factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE == -1 || factors.NUMBER_OF_TASKS == -1 || factors.RESOURCE_SHARING_FACTOR == -1 || allocation.getValue() == null)
            return;


        /* set the start icon inaccessible */
        generateLabel.setVisible(false);
        generating.setVisible(true);

        Analysis analysis = new Analysis();
        pair = analysis.generateSystem(factors);


        fillTable1(pair.getFirst());

        generateDone = true;

        /* set the start icon accessible */
        generateLabel.setVisible(true);
        generating.setVisible(false);
    }

    void showSchedulable(boolean schedulable) {
        if (schedulable) {
            scheduleYES.setVisible(true);
            scheduleNO.setVisible(false);
        } else {
            scheduleNO.setVisible(true);
            scheduleYES.setVisible(false);
        }
    }

}
