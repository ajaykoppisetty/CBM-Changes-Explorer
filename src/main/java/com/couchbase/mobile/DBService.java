package com.couchbase.mobile;

import com.couchbase.lite.Database;
import com.couchbase.lite.JavaContext;
import com.couchbase.lite.Manager;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.replicator.ReplicationState;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class DBService implements Replication.ChangeListener {
  public static final String DATABASE = "db";

  private static final String DB_DIRECTORY = "data";

  private Manager manager;
  private Database database;
  private Replication pushReplication = null;
  private Replication pullReplication = null;
  private boolean replicationActive = false;
  private List<ReplicationStateListener> stateListeners = new ArrayList<>();
  private String username = null;
  private String password = null;

  private DBService() {
    try {
      manager = new Manager(new JavaContext(DB_DIRECTORY), Manager.DEFAULT_OPTIONS);
      database = manager.getDatabase(DATABASE);
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  private static class Holder {
    private static DBService INSTANCE = new DBService();
  }

  public interface ReplicationStateListener {
    void onChange(boolean isActive);
  }

  public static DBService getInstance() {
    return Holder.INSTANCE;
  }

  public Database getDatabase() {
    return database;
  }

  public void setCredentials(String username, String password) {
    this.username = username;
    this.password = password;
  }

  public void toggleReplication(URL gateway, boolean continuous) {
    if (replicationActive) {
      stopReplication();
    } else {
      startReplication(gateway, continuous);
    }
  }

  public void startReplication(URL gateway, boolean continuous) {
    if (replicationActive) {
      stopReplication();
    }

    pushReplication = database.createPushReplication(gateway);
    pullReplication = database.createPullReplication(gateway);
    pushReplication.setContinuous(continuous);
    pullReplication.setContinuous(continuous);

    if (username != null) {
      Authenticator auth = AuthenticatorFactory.createBasicAuthenticator(username, password);
      pushReplication.setAuthenticator(auth);
      pullReplication.setAuthenticator(auth);
    }

    pushReplication.addChangeListener(this);
    pullReplication.addChangeListener(this);

    pushReplication.start();
    pullReplication.start();
  }

  public void stopReplication() {
    if (!replicationActive) return;

    pushReplication.stop();
    pullReplication.stop();

    pushReplication = null;
    pullReplication = null;
  }

  public void addReplicationStateListener(ReplicationStateListener listener) {
    stateListeners.add(listener);
  }

  public void removeReplicationStateListener(ReplicationStateListener listener) {
    stateListeners.remove(listener);
  }

  // Replication.ChangeListener
  @Override
  public void changed(Replication.ChangeEvent changeEvent) {
    if (changeEvent.getError() != null) {
      Throwable lastError = changeEvent.getError();

      Dialog.display(lastError.getMessage());

      return;
    }

    if (changeEvent.getTransition() == null) return;

    ReplicationState dest = changeEvent.getTransition().getDestination();

    replicationActive = ((dest == ReplicationState.STOPPING || dest == ReplicationState.STOPPED) ? false : true);

    stateListeners.forEach(listener -> listener.onChange(replicationActive));
  }
}
