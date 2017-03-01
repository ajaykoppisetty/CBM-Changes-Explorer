package com.couchbase.mobile;

import javafx.application.Platform;
import javafx.scene.control.Alert;

public class Dialog {
  public static void display(Exception ex) {
    display(ex.getMessage());
  }

  public static void display(String message) {
    Platform.runLater(() -> alert(message));
  }

  private static void alert(String message) {
    Alert alert = new Alert(Alert.AlertType.WARNING);
    alert.setTitle("Warning/Error");
    alert.setHeaderText("Exception thrown");
    alert.setContentText(message);

    alert.showAndWait();
  }
}
