package com.demo.tool;

import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;

public class ConfigController {
    @FXML
    public Button configButton;
    @FXML
    private TextField text_FIFONP_LOCK;
    @FXML
    private TextField text_FIFONP_UNLOCK;
    @FXML
    private TextField text_FIFOP_CANCEL;
    @FXML
    private TextField text_FIFOP_LOCK;
    @FXML
    private TextField text_FIFOP_UNLOCK;
    @FXML
    private TextField text_Mrsp_LOCK;
    @FXML
    private TextField text_Mrsp_UNLOCK;
    @FXML
    private TextField text_Mrsp_PREEMPTION_AND_MIGRATION;
    @FXML
    private TextField text_FULL_CONTEXT_SWITCH2;

    /**
     * 判断参数是否合法并传递为double，不合法为Double.MIN_VALUE
     *
     * @param textField
     * @return
     * @throws NumberFormatException
     */
    private static double parseDouble(TextField textField) throws NumberFormatException {
        double value = Double.MIN_VALUE;
        try {
            value = Double.parseDouble(textField.getText());
        } catch (NumberFormatException ignored) {
        }
        return value;
    }

    @FXML
    void onConfigButtonClicked() throws IllegalAccessException, NoSuchFieldException {
        boolean successful = true;
        ArrayList<Double> paraList = new ArrayList<>(Arrays.asList(AnalysisUtils.FIFONP_LOCK, AnalysisUtils.FIFONP_UNLOCK, AnalysisUtils.FIFOP_CANCEL, AnalysisUtils.FIFOP_LOCK, AnalysisUtils.FIFOP_UNLOCK, AnalysisUtils.MrsP_LOCK, AnalysisUtils.MrsP_UNLOCK, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, AnalysisUtils.FULL_CONTEXT_SWTICH2));
        ArrayList<TextField> textFieldList = new ArrayList<>(Arrays.asList(text_FIFONP_LOCK, text_FIFONP_UNLOCK, text_FIFOP_CANCEL, text_FIFOP_LOCK, text_FIFOP_UNLOCK, text_Mrsp_LOCK, text_Mrsp_UNLOCK, text_Mrsp_PREEMPTION_AND_MIGRATION, text_FULL_CONTEXT_SWITCH2));

//        Class<?> clazz = ConfigController.class;
//        Field[] fields = clazz.getDeclaredFields();
//        Field removeField = clazz.getDeclaredField("configButton");

//        ArrayList<TextField> textFields = new ArrayList<>(Arrays.asList(text_FIFONP_LOCK, text_FIFONP_UNLOCK, text_FIFOP_CANCEL, text_FIFOP_LOCK, text_FIFOP_UNLOCK, text_Mrsp_LOCK, text_Mrsp_UNLOCK, text_Mrsp_PREEMPTION_AND_MIGRATION, text_FULL_CONTEXT_SWITCH2));
//
//        for (int i = 0; i < paraList.size(); i++) {
//            if (!successful)
//                checkParse(textFields.get(i), paraList.get(i));
//            else
//                successful = checkParse(textFields.get(i), paraList.get(i));
//        }
//
//        if (!successful) {
//            configButton.setStyle("");
//            configButton.setText("Fail");
//            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
//            pauseTransition.setOnFinished(e -> {
//                configButton.setStyle("");
//                configButton.setText("confirm");
//            });
//            pauseTransition.play();
//        } else {
//            configButton.setStyle("-fx-text-fill: red;");
//            configButton.setText("Success");
//            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
//            pauseTransition.setOnFinished(e -> {
//                configButton.setStyle("");
//                configButton.setText("confirm");
//            });
//            pauseTransition.play();
//        }

        boolean flag = true;
        double[] values = new double[paraList.size() + 1];
        for (int i = 0; i < paraList.size(); i++) {
            values[i] = parseDouble(textFieldList.get(i));
            if (values[i] == Double.MIN_VALUE) {
                flag = false;
            }
        }
        if (!flag) {
            configButton.setStyle("-fx-text-fill: red;");
            configButton.setText("Fail");
            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
            pauseTransition.setOnFinished(e -> {
                configButton.setStyle("");
                configButton.setText("confirm");
            });
            pauseTransition.play();
        } else {

            AnalysisUtils.FIFONP_LOCK = values[0];
            AnalysisUtils.FIFONP_UNLOCK = values[1];
            AnalysisUtils.FIFOP_CANCEL = values[2];
            AnalysisUtils.FIFOP_LOCK = values[3];
            AnalysisUtils.FIFOP_UNLOCK = values[4];
            AnalysisUtils.MrsP_LOCK = values[5];
            AnalysisUtils.MrsP_UNLOCK = values[6];
            AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION = values[7];
            AnalysisUtils.FULL_CONTEXT_SWTICH2 = values[8];

            configButton.setStyle("-fx-text-fill: red;");
            configButton.setText("Success");
            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
            pauseTransition.setOnFinished(e -> {
                configButton.setStyle("");
                configButton.setText("confirm");
            });
            pauseTransition.play();
        }

//        AnalysisUtils.FIFONP_LOCK = parseDouble(text_FIFONP_LOCK) == Double.MIN_VALUE ? AnalysisUtils.FIFONP_LOCK : parseDouble(text_FIFONP_LOCK);
//        AnalysisUtils.FIFONP_UNLOCK = parseDouble(text_FIFONP_UNLOCK) == Double.MIN_VALUE ? AnalysisUtils.FIFONP_UNLOCK : parseDouble(text_FIFONP_UNLOCK);
//        AnalysisUtils.FIFOP_CANCEL = parseDouble(text_FIFOP_CANCEL) == Double.MIN_VALUE ? AnalysisUtils.FIFOP_CANCEL : parseDouble(text_FIFOP_CANCEL);
//        AnalysisUtils.FIFOP_LOCK = parseDouble(text_FIFOP_LOCK) == Double.MIN_VALUE ? AnalysisUtils.FIFOP_LOCK : parseDouble(text_FIFOP_LOCK);
//        AnalysisUtils.FIFOP_UNLOCK = parseDouble(text_FIFOP_UNLOCK) == Double.MIN_VALUE ? AnalysisUtils.FIFOP_UNLOCK : parseDouble(text_FIFOP_UNLOCK);
//        AnalysisUtils.MrsP_LOCK = parseDouble(text_Mrsp_LOCK) == Double.MIN_VALUE ? AnalysisUtils.MrsP_LOCK : parseDouble(text_Mrsp_LOCK);
//        AnalysisUtils.MrsP_UNLOCK = parseDouble(text_Mrsp_UNLOCK) == Double.MIN_VALUE ? AnalysisUtils.MrsP_UNLOCK : parseDouble(text_Mrsp_UNLOCK);
//        AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION = parseDouble(text_Mrsp_PREEMPTION_AND_MIGRATION) == Double.MIN_VALUE ? AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION : parseDouble(text_Mrsp_PREEMPTION_AND_MIGRATION);
//        AnalysisUtils.FULL_CONTEXT_SWTICH2 = parseDouble(text_FULL_CONTEXT_SWITCH2) == Double.MIN_VALUE ? AnalysisUtils.FULL_CONTEXT_SWTICH2 : parseDouble(text_FULL_CONTEXT_SWITCH2);

        System.out.println("FIFONP_LOCK: " + AnalysisUtils.FIFONP_LOCK + "\tFIFONP_UNLOCK: " + AnalysisUtils.FIFONP_UNLOCK);
        System.out.println("FIFOP_CANCEL: " + AnalysisUtils.FIFOP_CANCEL + "\tFIFOP_LOCK: " + AnalysisUtils.FIFOP_LOCK + "\tFIFOP_UNLOCK:" + AnalysisUtils.FIFOP_UNLOCK);
        System.out.println("MrsP_LOCK:" + AnalysisUtils.MrsP_LOCK + "\tMrsP_UNLOCK:" + AnalysisUtils.MrsP_UNLOCK + "\tMrsP_PREEMPTION_AND_MIGRATION:" + AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION);
        System.out.println("MrsP_LOCK:" + AnalysisUtils.MrsP_LOCK + "\tMrsP_UNLOCK:" + AnalysisUtils.MrsP_UNLOCK + "\tMrsP_PREEMPTION_AND_MIGRATION:" + AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION);
        System.out.println("FULL_CONTEXT_SWTICH2:" + AnalysisUtils.FULL_CONTEXT_SWTICH2);

    }
}