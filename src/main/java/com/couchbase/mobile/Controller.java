package com.couchbase.mobile;

import com.couchbase.lite.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import static com.couchbase.mobile.Runtime.mapper;

public class Controller implements LiveQuery.ChangeListener, ChangeListener<String>,
    SGMonitor.ChangesFeedListener, DBService.ReplicationStateListener {
  private static final String SYNC_GATEWAY_HOST = "http://localhost";
  private static final String SG_PUBLIC_URL = SYNC_GATEWAY_HOST + ":4984/" + DBService.DATABASE;
  private static final String SG_ADMIN_URL = SYNC_GATEWAY_HOST + ":4985/" + DBService.DATABASE;

  private static final String TOGGLE_INACTIVE = "-fx-background-color: #e6555d;";
  private static final String TOGGLE_ACTIVE = "-fx-background-color: #ade6a6;";
  private static final String TOGGLE_DISABLED = "-fx-background-color: #555555;";

  @FXML private ListView<String> documentList;
  private ObservableList<String > documents = FXCollections.observableArrayList();
  @FXML private TextArea contentsText;
  @FXML private TextArea changesFeed;
  @FXML private TextField usernameText;
  @FXML private TextField passwordText;
  @FXML private ToggleButton applyCredentialsBtn;
  @FXML private ToggleButton syncBtn;

  private DBService service = DBService.getInstance();
  private Database db = service.getDatabase();
  private SGMonitor changesMonitor;
  private LiveQuery liveQuery;

  @FXML private void initialize() {
    documentListInitialize();
    documentList.setItems(documents);

    setState(applyCredentialsBtn, false);
    setState(syncBtn, false);

    service.addReplicationStateListener(this);

    changesMonitor = new SGMonitor(SG_ADMIN_URL, "false", "true", "0", "all_docs", this);
    changesMonitor.start();
  }

  private void documentListInitialize() {
    Query query = db.createAllDocumentsQuery();
    query.setAllDocsMode(Query.AllDocsMode.INCLUDE_DELETED);
    liveQuery = query.toLiveQuery();
    liveQuery.addChangeListener(this);
    liveQuery.start();

    documentList.getSelectionModel().selectedItemProperty().addListener(this);
  }

  // LiveQuery.ChangeListener
  @Override
  public void changed(LiveQuery.ChangeEvent event) {
    if (event.getSource().equals(liveQuery)) {
      Platform.runLater(() -> {
        QueryEnumerator rows = event.getRows();

        documents.clear();

        rows.forEach(queryRow -> documents.add(queryRow.getDocumentId()));
      });
    }
  }

  // ListView ChangeListener<String>
  @Override
  public void changed(ObservableValue<? extends String> observable, String oldId, String newId) {
    if (null == newId) return;

    Map properties = db.getDocument(newId).getProperties();

    try {
      String json = mapper.writeValueAsString(properties);

      contentsText.setText(prettyText(json));
    } catch (JsonProcessingException ex) {
      ex.printStackTrace();
      Dialog.display(ex);
    }
  }

  // SGMonitor.ChangesFeedListener
  @Override
  public void onResponse(String body) {
    changesFeed.appendText(prettyText((String) body));
  }

  @Override
  public void onChange(boolean isActive) {
    setState(syncBtn, isActive);
  }

  @FXML private void applyCredentialsToggled(ActionEvent event) {
    String username = null;
    String password = null;

    if (applyCredentialsBtn.isSelected()) {
      username = usernameText.getText();
      password = passwordText.getText();
    }

    service.setCredentials(username, password);
    applyCredentialsBtn.setStyle(applyCredentialsBtn.isSelected() ? TOGGLE_ACTIVE : TOGGLE_INACTIVE);
  }

  @FXML private void saveContentsClicked(ActionEvent event) {
    Map<String, Object> properties = null;
    Document document;

    try {
      properties = mapper.readValue(contentsText.getText(), Map.class);
    } catch (IOException ex) {
      ex.printStackTrace();
      Dialog.display(ex);
    }

    if (properties.containsKey("_id")) {
      document = db.getDocument((String) properties.get("_id"));
    } else {
      document = db.createDocument();
    }

    try {
      document.putProperties(properties);
    } catch (CouchbaseLiteException ex) {
      ex.printStackTrace();
      Dialog.display(ex);
    }
  }

  @FXML private void syncToggled(ActionEvent event) {
    try {
      syncBtn.setDisable(true);
      syncBtn.setStyle(TOGGLE_DISABLED);
      service.toggleReplication(new URL(SG_PUBLIC_URL), true);
    } catch (Exception ex) {
      ex.printStackTrace();
      Dialog.display(ex);
      syncBtn.setDisable(false);
    }
  }

  @FXML private void exitClicked(ActionEvent event) {
    // Try to shut everything down gracefully
    changesMonitor.stop();
    liveQuery.stop();
    service.stopReplication();
    db.close();
    db.getManager().close();

    Platform.exit();
  }

  private void setState(ToggleButton btn, boolean active) {
    btn.setSelected(active);
    btn.setStyle(active ? TOGGLE_ACTIVE : TOGGLE_INACTIVE);
    btn.setDisable(false);
  }

  private String prettyText(String json) {
    String out = null;

    try {
      Object object = mapper.readValue(json, Object.class);

      out = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    return out;
  }
}
