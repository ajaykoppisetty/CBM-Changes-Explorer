package com.couchbase.mobile;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.*;

import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import static com.couchbase.mobile.Runtime.mapper;

public class SGMonitor {
  private static final OkHttpClient client = new OkHttpClient.Builder()
      .readTimeout(1, TimeUnit.DAYS)
      .build();

  private ChangesFeedListener listener;
  private HttpUrl.Builder urlBuilder;
  private Thread monitorThread;
  private String since = "0";
  private Call call;

  SGMonitor(String url, String activeOnly, String includeDocs, String since, String style,
            ChangesFeedListener listener) {
    this.since = since;

    urlBuilder = HttpUrl.parse(url).newBuilder()
        .addPathSegment("_changes")
        .addQueryParameter("active_only", activeOnly)
        .addQueryParameter("include_docs", includeDocs)
        .addQueryParameter("style", style)
        .addQueryParameter("since", since)
        .addQueryParameter("feed", "longpoll")
        .addQueryParameter("timeout", "0");

    this.listener = listener;
  }

  public interface ChangesFeedListener {
    void onResponse(String body);
  }

  public void start() {
    monitorThread = new Thread(() -> {
      while (!Thread.interrupted()) {
        Request request = new Request.Builder()
            .url(urlBuilder.build())
            .build();

        call = client.newCall(request);

        try (Response response = call.execute()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          String body = response.body().string();

          JsonNode tree = mapper.readTree(body);

          since = tree.get("last_seq").asText();
          urlBuilder.setQueryParameter("since", since);

          listener.onResponse(body);
        } catch (SocketException ex) {
          return;
        } catch (IOException ex) {
          ex.printStackTrace();
          Dialog.display(ex);
        }
      }
    });

    monitorThread.setDaemon(true);
    monitorThread.start();
  }

  public void stop() {
    monitorThread.interrupt();
    call.cancel();
  }

  public String getSince() {
    return since;
  }
}