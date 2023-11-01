package com.demo.tool;

import com.demo.tool.resource.ResourcePaneController;
import com.demo.tool.resource.ResourcePartitionController;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;

import static com.demo.tool.Utils.judgeFloat;
import static com.demo.tool.Utils.judgeInteger;

public class Controller {
    @FXML
    private TextField coreNum;
    @FXML
    private TextField taskNum;
    @FXML
    private TextField utility;
    @FXML
    private TextField rangeT1;
    @FXML
    private TextField rangeT2;
    @FXML
    private TextField resourceNum;
    @FXML
    private TextField rsf;
    @FXML
    private TextField maxAccess;
    @FXML
    private TextField rangeCSL1;
    @FXML
    private TextField rangeCSL2;
    @FXML
    private ComboBox<String> allocation;
    @FXML
    private ComboBox<String> RTM;
    @FXML
    private Region loading;
    @FXML
    private Region generating;
    @FXML
    private ComboBox<String> priority;
    @FXML
    private Label scheduleNO;
    @FXML
    private Label scheduleYES;
    @FXML
    private Region startIcon;
    @FXML
    private Label startLabel;
    @FXML
    private Label generateLabel;
    @FXML
    private ComboBox<String> systemMode;
    @FXML
    private TableView<Object[]> table1;
    @FXML
    private TableView<Object[]> table2;
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
    private Tooltip tipsForU;
    @FXML
    private ScrollPane pPartition;
    @FXML
    private ScrollPane pResource;


    private Factors factors;

    private Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> pair;

    private boolean generateDone = false;

    private int taskShowing = -1;
    private boolean taskClickedFactoryAwake = false;
    private int taskClickCount = 1;
    private int currentShowingResource = -1;
    private int resourceClickCount = 0;

    void test0() {
        page1btn.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                coreNum.setText("16");
                taskNum.setText("80");
                utility.setText("8");
                rangeT1.setText("1");
                rangeT2.setText("100");
                resourceNum.setText("16");
                rsf.setText("0.3");
                maxAccess.setText("40");
                rangeCSL1.setText("1");
                rangeCSL2.setText("100");
                allocation.setValue("WF");
            }
        });
    }

    @FXML
    void initialize() {
        initComBox();
        initAnalysis();
        initPageBtnStyle();
        tipsForU.setShowDelay(javafx.util.Duration.millis(100));
        tipsForU.setHideDelay(javafx.util.Duration.millis(100));

        // 监听排序事件，当排序发生变化时更新行样式
        table2.getSortOrder().addListener((ListChangeListener<? super TableColumn<Object[], ?>>) change -> {
            table2.refresh(); // 重新渲染表格以更新行样式
        });
        resultTableChangeColorWithSort();

        utility.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                Bounds screenBounds = page11.localToScreen(page11.getBoundsInLocal());
                tipsForU.show(utility, screenBounds.getCenterX() + utility.getWidth() * 0.4, screenBounds.getCenterY() - utility.getParent().getBoundsInLocal().getHeight() * 2.75);
            } else {
                tipsForU.hide();
            }
        });
        //test
        test0();
    }

    void initPageBtnStyle() {
        page1btn.getStyleClass().add("round-background");
        page1btn.getStyleClass().add("basic-hover");
        page2btn.getStyleClass().add("basic-hover");
    }

    void initAnalysis() {
        factors = new Factors();
        pair = new Pair<>();
    }

    void initComBox() {
        allocation.getItems().addAll("WF", "BF", "FF", "NF");
        priority.getItems().addAll("DMPO");
        priority.setValue("DMPO");
        systemMode.getItems().addAll("LO", "HI", "ModeSwitch");
        RTM.getItems().addAll("MSRP", "MSRPNew", "Mrsp","MrspNew", "PWLP", "Dynamic");
    }

    void resultTableChangeColorWithSort() {
        ObservableList<Object[]> data = table2.getItems();
        table2.setRowFactory(tv -> new TableRow<Object[]>() {
            @Override
            protected void updateItem(Object[] item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle(""); // 处理空行的情况
                } else {
                    int rowIndex = getIndex();
                    if (rowIndex >= 0 && rowIndex < data.size()) {
                        Object[] currentRow = data.get(rowIndex);
                        if (currentRow != null && currentRow.length > 0) {
                            String value = (String) currentRow[currentRow.length - 1];
//                            System.out.println(currentRow[0] + " " + value);
                            if (value.equals("Unschedulable")) {
                                setStyle("-fx-background-color: rgb(240, 211, 211);");
                            } else {
                                setStyle("");
                            }
                        }
                    }
                }
            }
        });

        TableColumn<Object[], String> targetColumn = (TableColumn<Object[], String>) table2.getColumns().get(table2.getColumns().size() - 1);

        targetColumn.setCellFactory(column -> new TableCell<Object[], String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty); // 确保单元格内容正常显示
                if (empty || item == null) {
                    setStyle(""); // 处理空单元格的情况
                } else {
                    if (item.equals("Accept")) {
                        // 在原有样式的基础上追加背景颜色样式
                        setStyle("-fx-text-fill: rgb(22, 202, 173);");
                    } else {
                        if (item.equals("Unschedulable")) setStyle("-fx-text-fill: rgb(173, 67, 62);");
                        else setStyle("");
                    }
                }
                setText(item);
            }
        });
    }

    /**
     * bind the data with the table column
     */
    void fillTable(TableView table, ObservableList<Object[]> data) {
        // 设置数据源到 TableView
        table.getItems().clear();
        table.getItems().addAll(data);

        // 将数据源中的数据与每个 TableColumn 进行绑定
        for (int i = 0; i < table.getColumns().size(); i++) {
            TableColumn<Object[], Object> column = (TableColumn<Object[], Object>) table.getColumns().get(i);

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

    void generateTasksTable(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();
        for (ArrayList<SporadicTask> sporadicTasks : tasks) {
            for (SporadicTask task : sporadicTasks) {
                DecimalFormat df = new DecimalFormat("#.###");
                String formattedValue = df.format(task.util);
                // id, partition, priority, critical level, period, deadline, WCET, resource access, utilization
                Object[] row = new Object[]{task.id, task.partition, task.priority, task.critical == 0 ? "LO" : "HI",
                        task.period, task.deadline, task.C_LOW, task.C_HIGH, task.resource_required_index, formattedValue};
                data.add(row);
            }
        }
        fillTable(table1, data);
    }

    void generateResourceAccessButton() {
        TableColumn<Object[], ArrayList> resourceAccess = (TableColumn<Object[], ArrayList>) table1.getColumns().get(table1.getColumns().size() - 2);
        resourceAccess.setCellFactory(createButtonCellFactory());
        resourceAccess.setText("Resource Access");
    }

    private Callback<TableColumn<Object[], ArrayList>, TableCell<Object[], ArrayList>> createButtonCellFactory() {
        return new Callback<TableColumn<Object[], ArrayList>, TableCell<Object[], ArrayList>>() {
            @Override
            public TableCell<Object[], ArrayList> call(final TableColumn<Object[], ArrayList> param) {
                return new TableCell<Object[], ArrayList>() {
                    private final Button button = new Button("View");
                    {
//                        button.setStyle("-fx-background-color: rgb(22, 202, 173);-fx-text-fill: white;-fx-focus-color: transparent;-fx-faint-focus-color: transparent;");
                        button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                "-fx-background-insets: 0, 1; -fx-faint-focus-color: transparent;" +
                                "-fx-background-radius: 5px; -fx-text-fill: white;" +
                                "-fx-font-weight: bold;-fx-font-size: 14px;");
                        button.setOnAction(event -> {
                            Object[] item = getTableView().getItems().get(getIndex());
                            taskClicked(item);
                        });
                        // 鼠标进入时的样式
                        button.setOnMouseEntered(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(17, 183, 168), rgb(17, 152, 130));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });
                        // 鼠标退出时的样式
                        button.setOnMouseExited(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                "-fx-background-insets: 0, 1; -fx-faint-focus-color: transparent;" +
                                "-fx-background-radius: 5px; -fx-text-fill: white;" +
                                "-fx-font-weight: bold;-fx-font-size: 14px;");
                        });

                        // 按下时的样式
                        button.setOnMousePressed(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(11, 101, 86), rgb(65, 122, 112));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });

                        // 恢复正常样式
                        button.setOnMouseReleased(e -> {
                            button.setStyle("-fx-background-color: linear-gradient(to top, rgb(22, 202, 173), rgb(130, 244, 224));" +
                                    "-fx-background-insets: 0, 1; -fx-background-radius: 5px; -fx-text-fill: white;" +
                                    "-fx-font-weight: bold; -fx-font-size: 14px;");
                        });
                    }

                    @Override
                    protected void updateItem(ArrayList item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty) {
                            setGraphic(null);
                        } else {
                            setGraphic(button);
                        }
                    }
                };
            }
        };
    }

    void generateResource(ArrayList<Resource> resources) {
        HBox contentHBox = new HBox();
        for (Resource resource : resources) {
            ResourcePaneController rpc = new ResourcePaneController();
            rpc.getResourceId().setText("Resource " + (resource.id - 1));
            rpc.setID(resource.id);
            rpc.getCsl().setText("csl " + resource.csl);

            contentHBox.getChildren().add(rpc);
        }
        pResource.setContent(contentHBox);
    }

    void generatePartition() {
        HBox contentHBox = new HBox();
        for (int i = 0; i < factors.TOTAL_PARTITIONS; i++) {
            ResourcePartitionController rpc = new ResourcePartitionController();
            rpc.getPartitionId().setText("Partition " + i);

            contentHBox.getChildren().add(rpc);
        }

        pPartition.setContent(contentHBox);
    }

    void cleanStyleBeforeChange(String styleToBeCleaned) {
        // clean first
        HBox partitionPane = (HBox) pPartition.getContent();
        for (int i = 0; i < partitionPane.getChildren().size(); i++) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(i);
            rpc.backToNormal(styleToBeCleaned);
        }
        HBox resourcePane = (HBox) pResource.getContent();
        for (int i = 0; i < resourcePane.getChildren().size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(i);
            rpc.backToNormal(styleToBeCleaned);
        }
    }

    void onWakeUpTaskClicked() {
        table1.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !taskClickedFactoryAwake) {
                // clean first
                cleanStyleBeforeChange("resource-clicked-change-all");

//                taskClicked();
                table1.refresh();
            }
        });
    }

//     双击控件弃用，换成按钮
//    /**
//     * link task table to resource and partition block by define a new row factory
//     * -> click table row twice to light the task
//     * and call demonstration of resource with access time and partition
//     **/
//    void taskClicked() {
//        for (Object[] item : table1.getItems()) {
//            if (item != null && item.length > 0) {
//                table1.setRowFactory(tv -> {
//                    TableRow<Object[]> row = new TableRow<>() {
//                        @Override
//                        protected void updateItem(Object[] item, boolean empty) {
//                            super.updateItem(item, empty);
//                            if (item == null || empty) setStyle("");
//                            else {
//                                if (taskShowing == (int) item[0]) setStyle("-fx-background-color: rgb(22, 202, 173);");
//                                else setStyle("");
//                            }
//                        }
//                    };
//                    row.setOnMouseClicked(event -> {
//                        if (event.getClickCount() == 2 && (!row.isEmpty())) {
//                            Object[] selectedItem = table1.getSelectionModel().getSelectedItem();
//                            System.out.println("Select No.   " + selectedItem[0]);
//                            if (taskShowing == (int) selectedItem[0]) {
//                                taskClickCount++;
//                            } else {
//                                taskClickCount = 1;
//                                // 恢复上一个双击行的背景颜色
//                                if (taskShowing != -1) {
//                                    table1.refresh();
//                                    taskClickedShowResourceAndPartition(taskShowing, true);
//                                }
//                            }
//                            if (taskClickCount % 2 == 0) {
//                                // 双击同一行两次，恢复原样
//                                taskClickedShowResourceAndPartition(taskShowing, true);
//                                table1.refresh();
//                                taskShowing = -1;
//                            } else {
//                                // 更改当前行的背景颜色为绿色
//                                taskShowing = (int) selectedItem[0];
//                                table1.refresh();
//                                taskClickedShowResourceAndPartition(taskShowing, false);
//                            }
//                        }
//                        taskClickedFactoryAwake = true;
//                        System.out.println("task click awake!!！");
//                    });
//                    return row;
//                });
//            }
//        }
//    }

    /**
     * click resource access button to show
     **/
    void taskClicked(Object[] rowItem) {
        if (taskShowing == (int) rowItem[0]) {
            taskClickCount = 0;
        } else {
            taskClickCount = 1;
            // 恢复上一个双击行的背景颜色
            if (taskShowing != -1) {
                table1.refresh();
                taskClickedShowResourceAndPartition(taskShowing, true);
            }
        }
        if (taskClickCount == 0) {
            taskClickedShowResourceAndPartition(taskShowing, true);
            table1.refresh();
            taskShowing = -1;
        } else {
            // 更改当前行的背景颜色为绿色
            taskShowing = (int) rowItem[0];
            table1.refresh();
            taskClickedShowResourceAndPartition(taskShowing, false);
        }
        table1.setRowFactory(tv -> {
            TableRow<Object[]> row = new TableRow<>() {
                @Override
                protected void updateItem(Object[] item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item == null || empty) setStyle("");
                    else {
                        if (taskShowing == (int) item[0]) setStyle("-fx-background-color: rgb(22, 202, 173);");
                        else setStyle("");
                    }
                }
            };

            return row;
        });
    }

    /**
     * link task table to resource and partition block
     * -> show the resource with access time and partition
     * -> as well as restore resource and partition back to normal
     **/
    void taskClickedShowResourceAndPartition(int taskId, boolean restore) {
        ArrayList<ArrayList<SporadicTask>> tasks = pair.getFirst();
        ArrayList<Integer> resourceBlock = new ArrayList<>();
        ArrayList<Integer> numberOfAccessInOneRelease = new ArrayList<>();
        int partitionBlock = -1;
        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                if (tasks.get(i).get(j).id == taskId) {
                    SporadicTask task = tasks.get(i).get(j);
                    resourceBlock = task.resource_required_index;
                    numberOfAccessInOneRelease = task.number_of_access_in_one_release;
                    partitionBlock = i;
                }
            }
        }
        // show resource
        HBox resourcePane = (HBox) pResource.getContent();
        if (resourceBlock.size() == 0 && restore) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(0);
            rpc.backToNormal("resource-change-when-task-clicked");
        }
        for (int i = 0; i < resourceBlock.size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(resourceBlock.get(i));
            if (restore) rpc.backToNormal("resource-change-when-task-clicked");
            else {
                rpc.getTaskAccessTime().setText("Number of access " + numberOfAccessInOneRelease.get(i));
                rpc.onRespondTask();
            }
        }
        // show partition
        HBox partitionPane = (HBox) pPartition.getContent();
        ResourcePartitionController rac = (ResourcePartitionController) partitionPane.getChildren().get(partitionBlock);
        if (restore) rac.backToNormal("resource-change-when-task-clicked");
        else rac.onRespond("resource-change-when-task-clicked");
    }

    /**
     * link resource panel to task table and partition block
     * -> light the corresponding tasks and partition
     * -> as well as task table and partition back to normal
     **/
    void resourceClicked() {
        HBox resourcePane = (HBox) pResource.getContent();
        for (int i = 0; i < resourcePane.getChildren().size(); i++) {
            ResourcePaneController rpc = (ResourcePaneController) resourcePane.getChildren().get(i);
            rpc.setOnMouseClicked(event -> {
                // clean first
                cleanStyleBeforeChange("resource-change-when-task-clicked");

                int lastShowingResource = currentShowingResource;
                resourceClickedShowPartition(true);
                resourceClickedShowTask(true);

                currentShowingResource = rpc.onPaneClicked();

                if (lastShowingResource == currentShowingResource) resourceClickCount++;
                else resourceClickCount = 1;

                if (resourceClickCount % 2 == 0) {
                    resourceClickedShowPartition(true);
                    resourceClickedShowTask(true);
                } else {
                    resourceClickedShowPartition(false);
                    resourceClickedShowTask(false);
                }
                taskClickedFactoryAwake = false;
                System.out.println("task click sleep...");
            });
        }
    }

    void resourceClickedShowPartition(boolean restore) {
        // show partition
        if (currentShowingResource == -1 && restore) return;
        ArrayList<Integer> partitions = pair.getSecond().get(currentShowingResource - 1).partitions;
        HBox partitionPane = (HBox) pPartition.getContent();
        System.out.println(partitions);
        if (partitions.size() == 0) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(0);
            rpc.backToNormal("resource-clicked-change-all");
        }

        for (Integer partition : partitions) {
            ResourcePartitionController rpc = (ResourcePartitionController) partitionPane.getChildren().get(partition);
            if (restore) rpc.backToNormal("resource-clicked-change-all");
            else rpc.onRespond("resource-clicked-change-all");
        }
    }

    void resourceClickedShowTask(boolean restore) {
        //show task
        if (currentShowingResource == -1 && restore) return;
        HashSet<Integer> tasksId = new HashSet<>();
        ArrayList<SporadicTask> tasks = pair.getSecond().get(currentShowingResource - 1).requested_tasks;
        for (SporadicTask task : tasks) tasksId.add(task.id);
        System.out.println(tasksId);
        if (restore) table1.setRowFactory(null);
        else {
            for (Object[] item : table1.getItems()) {
                if (item != null && item.length > 0) {
                    table1.setRowFactory(tv -> new TableRow<>() {
                        @Override
                        protected void updateItem(Object[] item1, boolean empty) {
                            super.updateItem(item1, empty);
                            if (item1 == null || empty) {
                                setStyle("");
                            } else {
                                if (tasksId.contains((int) item1[0])) {
                                    setStyle("-fx-background-color: lightsalmon;");
                                } else {
                                    setStyle("");
                                }
                            }
                        }
                    });
                }
            }
        }
        table1.refresh();
    }

    void generateResponseTimeTable(ArrayList<ArrayList<SporadicTask>> tasks) {
        ObservableList<Object[]> data = FXCollections.observableArrayList();
        for (ArrayList<SporadicTask> sporadicTasks : tasks) {
            for (SporadicTask task : sporadicTasks) {
                String schedulable = "Unresolved";
                if (task.schedulable == 1) schedulable = "Accept";
                else if (task.schedulable == 0)schedulable = "Unschedulable";
                if (systemMode.getValue() == "ModeSwitch" || systemMode.getValue() == "HI") {
                    long Ri = task.Ri_Switch;
                    if (systemMode.getValue() == "HI") Ri = task.Ri_HI;
                    if (task.critical == 1) {     //只显示HI任务
                        // id, partition, priority, response time, deadline, WCET
                        // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                        Object[] row = new Object[]{task.id, task.partition, "HI", task.priority, Ri, task.deadline, task.WCET,
                                task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local, task.PWLP_S, schedulable};
                        data.add(row);
                    }
                } else {
                    // id, partition, priority, response time, deadline, WCET
                    // resource execution time, interference time, spin blocking, indirect spin blocking, arrival blocking
                    Object[] row = new Object[]{task.id, task.partition, task.critical == 0 ? "LO" : "HI", task.priority, task.Ri_LO, task.deadline, task.WCET,
                            task.pure_resource_execution_time, task.interference, task.spin, task.indirect_spin, task.local, task.PWLP_S, schedulable};
                    data.add(row);
                }
            }
        }
        fillTable(table2, data);
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
        generateResponseTimeTable(pair.getFirst());

        showSchedulable(factors.schedulable);

        /* set the start icon accessible */
        startIcon.setVisible(true);
        startLabel.setVisible(true);
        loading.setVisible(false);
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

        factors.RESOURCE_SHARING_FACTOR = Math.min(Double.parseDouble(judgeFloat(rsf)), 1.0);
        factors.TOTAL_RESOURCES = Integer.parseInt(judgeInteger(resourceNum));
        factors.PRIORITY = priority.getValue();

        if (factors.MIN_PERIOD == -1 || factors.MAX_PERIOD == -1 || factors.TOTAL_PARTITIONS == -1 || factors.CL_RANGE_LOW == -1 || factors.CL_RANGE_HIGH == -1 ||
                factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE == -1 || factors.NUMBER_OF_TASKS == -1 || factors.UTILISATION == -1 || factors.TOTAL_RESOURCES == -1 ||
                factors.RESOURCE_SHARING_FACTOR == -1 || allocation.getValue() == null)
            return;


        /* set the start icon inaccessible */
        generateLabel.setVisible(false);
        generating.setVisible(true);

        Analysis analysis = new Analysis();
        pair = analysis.generateSystem(factors);


        generateTasksTable(pair.getFirst());
//        fillTable3(pair.getSecond());
        generateResource(pair.getSecond());
        generatePartition();

        generateDone = true;

        /* set the start icon accessible */
        generateLabel.setVisible(true);
        generating.setVisible(false);

        // show data on the screen
//        onWakeUpTaskClicked();
//        taskClicked();
        generateResourceAccessButton();
        resourceClicked();
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
