package com.demo.tool;

import javafx.scene.control.Alert;
import javafx.scene.control.TextField;

import java.util.ArrayList;
import java.util.Random;

/**
 * Utils used in front end
 */
public class Utils {
    public static String judgeInteger(TextField textField) {
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

    public static String judgeFloat(TextField textField) {
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

    public static ArrayList<Integer> generate() {
        int numberOfRandomNumbers = 11;
        int minValue = 0;
        int maxValue = 4;

        ArrayList<Integer> res = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < numberOfRandomNumbers; i++) {
            int randomNumber = random.nextInt(maxValue - minValue + 1) + minValue;
            res.add(randomNumber);
            System.out.print(randomNumber + " ");
        }
        return res;
    }
}
