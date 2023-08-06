package com.demo.tool;

import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import responseTimeTool.Analysis;
import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;
import responseTimeTool.utils.Factors;
import responseTimeTool.utils.Pair;

import java.text.DecimalFormat;
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

    @FXML
    private TableView<Object[]> table3;

    @FXML
    private VBox page11;

    @FXML
    private VBox page12;


    @FXML
    private VBox page21;

    @FXML
    private VBox page22;

    @FXML
    private StackPane page1btn;

    @FXML
    private StackPane page2btn;

    @FXML
    private TextField resourceNum;

    @FXML
    private TextField rsf;

    @FXML
    private Tooltip tipsForU;


    private Factors factors;

    private Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> pair;

    private boolean generateDone = false;

    @FXML
    void initialize() {
        initComBox();
        initAnalysis();
        initPageBtn();
        tipsForU.setShowDelay(javafx.util.Duration.millis(100));
        tipsForU.setHideDelay(javafx.util.Duration.millis(100));

        utility.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                Bounds screenBounds = page11.localToScreen(page11.getBoundsInLocal());
                tipsForU.show(utility, screenBounds.getCenterX() + utility.getWidth()*0.4, screenBounds.getCenterY() -utility.getParent().getBoundsInLocal().getHeight()*2.75 );
            }else {
                tipsForU.hide();
            }
        });
    }

    @FXML
    void initPageBtn() {
        page1btn.getStyleClass().add("round-background");
        page1btn.getStyleClass().add("basic-hover");
        page2btn.getStyleClass().add("basic-hover");
    }

    @FXML
    void initAnalysis() {
        factors = new Factors();
        pair = new Pair<>();
    }

    @FXML
    void initComBox() {
        allocation.getItems().addAll("WF", "BF", "FF", "NF");
        priority.getItems().addAll("DMPO");
        priority.setValue("DMPO");
        systemMode.getItems().addAll("LO", "HI", "ModeSwitch");
        RTM.getItems().addAll("MSRP", "Mrsp");
    }

    @FXML
    void fillTable1(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);

                DecimalFormat df = new DecimalFormat("#.###");
                String formattedValue = df.format(task.util);

                // id, partition, priority, critical level, period, deadline, WCET, resource access, utilization
                Object[] row = new Object[]{task.id, task.partition, task.priority, task.critical == 0 ? "LO" : "HI",
                        task.period, task.deadline, task.C_LOW, task.C_HIGH, task.resource_required_index, formattedValue};
                data.add(row);
            }
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
    void fillTable3(ArrayList<Resource> resources) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();

        for (int i = 0; i < resources.size(); i++) {
                Resource resource = resources.get(i);
                ArrayList<Integer> tasksID = new ArrayList<>();
                for (int j =0; j< resources.get(i).requested_tasks.size();j++){
                    tasksID.add(resources.get(i).requested_tasks.get(j).id);
                }
                // id, csl, requested_tasks, partitions
                Object[] row = new Object[]{resource.id, resource.csl, tasksID, resource.partitions};
                data.add(row);
        }
        // 设置数据源到 TableView
        table3.getItems().clear();
        table3.getItems().addAll(data);

        // 将数据源中的数据与每个 TableColumn 进行绑定
        for (int i = 0; i < table3.getColumns().size(); i++) {
            TableColumn<Object[], Object> column = (TableColumn<Object[], Object>) table3.getColumns().get(i);

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
    void fillTable2(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();

        if (systemMode.getValue() == "ModeSwitch") {
            for (int i = 0; i < tasks.size(); i++) {
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    SporadicTask task = tasks.get(i).get(j);
                    // id, partition, priority, response time, deadline, WCET
                    // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                    Object[] row = new Object[]{task.id, task.partition, task.priority, task.Ri_Switch, task.deadline, task.WCET,
                            task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local};
                    data.add(row);
                }
            }
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    SporadicTask task = tasks.get(i).get(j);
                    // id, partition, priority, response time, deadline, WCET
                    // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                    Object[] row = new Object[]{task.id, task.partition, task.priority, task.Ri, task.deadline, task.WCET,
                            task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local};
                    data.add(row);
                }
            }
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
            alert.setHeaderText(null);
            alert.setContentText("Parameter settings are incomplete");
            alert.showAndWait();
            return;
        }
        if (systemMode.getValue() == null || RTM.getValue() == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setHeaderText(null);
            alert.setContentText("System parameters are incomplete.");
            alert.showAndWait();
            return;
        }
        factors.SYSTEM_MODE = systemMode.getValue();
        factors.ANALYSIS_MODE = RTM.getValue();

        /* set the start icon inaccessible */
        startIcon.setVisible(false);
        startLabel.setVisible(false);
        loading.setVisible(true);

        Analysis analysis = new Analysis();
        analysis.analysis(factors, pair.getFirst(), pair.getSecond());
        fillTable2(pair.getFirst());

        showSchedulable(factors.schedulable);

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
                || maxAccess.getText().isEmpty() || taskNum.getText().isEmpty() || utility.getText().isEmpty() || resourceNum.getText().isEmpty()
                || rsf.getText().isEmpty() || allocation.getValue() == null) {
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
        factors.UTILISATION = Double.parseDouble(judgeFloat(utility)) < factors.TOTAL_PARTITIONS ? Double.parseDouble(judgeFloat(utility)) : factors.TOTAL_PARTITIONS;
        factors.ALLOCATION = allocation.getValue();

        factors.RESOURCE_SHARING_FACTOR = Double.parseDouble(judgeFloat(rsf)) < 1.0 ? Double.parseDouble(judgeFloat(rsf)) : 1.0;
        factors.TOTAL_RESOURCES = Integer.parseInt(judgeInteger(resourceNum));


        if (factors.MIN_PERIOD == -1 || factors.MAX_PERIOD == -1 || factors.TOTAL_PARTITIONS == -1 || factors.CL_RANGE_LOW == -1 || factors.CL_RANGE_HIGH == -1 ||
                factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE == -1 || factors.NUMBER_OF_TASKS == -1 || factors.UTILISATION == -1 || factors.TOTAL_RESOURCES == -1 ||
                factors.RESOURCE_SHARING_FACTOR == -1 || allocation.getValue() == null)
            return;


        /* set the start icon inaccessible */
        generateLabel.setVisible(false);
        generating.setVisible(true);

        Analysis analysis = new Analysis();
        pair = analysis.generateSystem(factors);


        fillTable1(pair.getFirst());
        fillTable3(pair.getSecond());

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

    @FXML
    void onChangeToPage1() {
        page1btn.getStyleClass().add("round-background");
        page2btn.getStyleClass().remove("round-background");

        page11.setVisible(true);
        page12.setVisible(true);
        page21.setVisible(false);
        page22.setVisible(false);
    }

    @FXML
    void onChangeToPage2() {
        page1btn.getStyleClass().remove("round-background");
        page2btn.getStyleClass().add("round-background");

        page11.setVisible(false);
        page12.setVisible(false);
        page21.setVisible(true);
        page22.setVisible(true);
    }

}
