package com.demo.tool;

import com.demo.tool.resource.ResourcePaneController;
import com.demo.tool.resource.ResourcePartitionController;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import static com.demo.tool.Utils.judgeFloat;
import static com.demo.tool.Utils.judgeInteger;

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

    boolean checkParse(TextField textField, Double para) {
        boolean flag = false;
        if (parseDouble(textField) == Double.MIN_VALUE) {   //不合法字符
            textField.setText("illegal value");
            flag = false;
        } else {
            para = parseDouble(textField);
            flag = true;
        }
        return flag;
    }

    @FXML
    void onConfigButtonClicked() throws IllegalAccessException, NoSuchFieldException {
        boolean successful = true;
        ArrayList<Double> paraList = new ArrayList<>(Arrays.asList(AnalysisUtils.FIFONP_LOCK, AnalysisUtils.FIFONP_UNLOCK, AnalysisUtils.FIFOP_CANCEL, AnalysisUtils.FIFOP_LOCK, AnalysisUtils.FIFOP_UNLOCK, AnalysisUtils.MrsP_LOCK, AnalysisUtils.MrsP_UNLOCK, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, AnalysisUtils.FULL_CONTEXT_SWTICH2));

//        Class<?> clazz = ConfigController.class;
//        Field[] fields = clazz.getDeclaredFields();
//        Field removeField = clazz.getDeclaredField("configButton");

        ArrayList<TextField> textFields = new ArrayList<>(Arrays.asList(text_FIFONP_LOCK,text_FIFONP_UNLOCK,text_FIFOP_CANCEL,text_FIFOP_LOCK,text_FIFOP_UNLOCK,text_Mrsp_LOCK,text_Mrsp_UNLOCK,text_Mrsp_PREEMPTION_AND_MIGRATION,text_FULL_CONTEXT_SWITCH2));

        for (int i=0;i<paraList.size();i++) {
            if(!successful)
                checkParse(textFields.get(i),paraList.get(i));
            else
                successful = checkParse(textFields.get(i),paraList.get(i));
        }

        if(!successful){
            configButton.setText("Fail");
            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
            pauseTransition.setOnFinished(e -> {
                configButton.setText("confirm");
            });
            pauseTransition.play();
        }
        else{
            configButton.setText("Success");
            PauseTransition pauseTransition = new PauseTransition(Duration.millis(1000));
            pauseTransition.setOnFinished(e -> {
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