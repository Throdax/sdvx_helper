package com.sdvxhelper.app.controller.factories;

import javafx.scene.control.TableRow;

import com.sdvxhelper.app.controller.model.RivalScoreRow;

/**
 * Custom TableRow for the rivals score table, which colors rows based on the
 * rival's lamp status.
 * 
 * @author Throdax
 * @since 2.0.0
 */
public class RivalsRowFactory extends TableRow<RivalScoreRow> {

    @Override
    protected void updateItem(RivalScoreRow item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setStyle("");
        } else {
            setStyle(rivalLampStyle(item.getLamp()));
        }
    }

    private static String rivalLampStyle(String lamp) {
        if (lamp == null) {
            return "";
        }
        return switch (lamp.toLowerCase()) {
            case "puc" -> "-fx-background-color: #ffff66; -fx-text-fill: black;";
            case "uc" -> "-fx-background-color: #ffaaaa; -fx-text-fill: black;";
            case "hard" -> "-fx-background-color: #ffccff; -fx-text-fill: black;";
            case "clear" -> "-fx-background-color: #77ff77; -fx-text-fill: black;";
            case "failed" -> "-fx-background-color: #aaaaaa; -fx-text-fill: black;";
            default -> "";
        };
    }

}
