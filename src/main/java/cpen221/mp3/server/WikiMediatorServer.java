package cpen221.mp3.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cpen221.mp3.wikimediator.WikiMediator;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLOutput;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WikiMediatorServer {

    public static final int WIKI_PORT = 4949;
    private final ServerSocket serverSocket;
    private final ExecutorService pool;
    boolean shutdown;

    /**
     * Start a server at a given port number, with the ability to process
     * upto n requests concurrently.
     *
     * @param port the port number to bind the server to
     * @param n    the number of concurrent requests the server can handle
     */
    public WikiMediatorServer(int port, int n) throws IOException {
        serverSocket = new ServerSocket(port);
        pool = Executors.newFixedThreadPool(n);
        shutdown = false;
    }

    /**
     * Run the server, listening for connections and handling them.
     *
     * @throws IOException if the main server socket is broken
     */
    public void serve() throws IOException {
        while (true) {
            // block until a client connects
            final Socket socket = serverSocket.accept();

            //Creates a new executor service to handle n requests:
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        try {
                            handle(socket);
                        } finally {
                            socket.close();
                        }
                    } catch (IOException ioe) {
                        // this exception wouldn't terminate serve(),
                        // since we're now on a different thread, but
                        // we still need to handle it
                        ioe.printStackTrace();
                    }
                }
            });
        }
    }

    /**
     * Handle one client connection. Returns when client disconnects.
     *
     * @param socket socket where client is connected
     * @throws IOException if connection encounters an error
     */
    private void handle(Socket socket) throws IOException {
        System.err.println("client connected");

        BufferedReader in =
            new BufferedReader(new InputStreamReader(socket.getInputStream()));

        PrintWriter out = new PrintWriter(new OutputStreamWriter(
            socket.getOutputStream()), true);

        try {
            // each request is a single line containing a number
            for (String line = in.readLine(); line != null; line = in
                .readLine()) {
                System.err.println("request: " + line);
                try {
                    JsonObject jsonObjectIn = new Gson().fromJson(line, JsonObject.class);
                    System.out.println("Request as obj:"+jsonObjectIn.toString());

                    // compute answer and send back to client
                    JsonObject jsonObjectOut = getJsonResult(jsonObjectIn);
                    System.err.println("reply: " + jsonObjectOut.toString());
                    out.println(jsonObjectOut.toString());
                    if(shutdown){
                        System.out.println("Turning the server off...");
                        out.close();
                        in.close();
                        socket.close();
                        pool.shutdown();
                        serverSocket.close();
                    }
                } catch (NumberFormatException e) {
                    // complain about ill-formatted request
                    System.err.println("reply: err");
                    out.print("err\n");
                }
                // important! our PrintWriter is auto-flushing, but if it were
                // not:
                // out.flush();
            }
        } finally {
            out.close();
            in.close();
        }
    }



    private JsonObject getJsonResult(JsonObject jsonObjectIn) {

        JsonObject jsonObjectOut = new JsonObject();
        WikiMediator wikiMediator = new WikiMediator();

        jsonObjectOut.add("id", jsonObjectIn.get("id"));

        String type = jsonObjectIn.get("type").getAsString();

        // This is for the Search operation
        if (type.compareToIgnoreCase("search") == 0) {

            String query = jsonObjectIn.get("query").getAsString();
            int limit = jsonObjectIn.get("limit").getAsInt();
            List<String> result = new ArrayList<>();

            //this is executed to manage timeouts
            if (jsonObjectIn.has("timeout")) {
                //gets timeout from the json obj
                long timeoutDuration = jsonObjectIn.get("timeout").getAsLong();
                final Duration timeout = Duration.ofSeconds(timeoutDuration);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<List<String>> future =
                    executor.submit(() -> wikiMediator.search(query, limit));
                try {
                    result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    future.cancel(true);
                    jsonObjectOut.addProperty("status", "failed");
                    jsonObjectOut.addProperty("response", "Operation timed out");
                    return jsonObjectOut;
                }
            } else {
                result = wikiMediator.search(query, limit);
            }

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
                jsonObjectOut.addProperty("response", query + " returned no results");
            }
        //This is for the zeitgeist operation
        } else if (type.compareToIgnoreCase("zeitgeist") == 0) {

            int limit = jsonObjectIn.get("limit").getAsInt();
            List result = wikiMediator.zeitgeist(limit);

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
                jsonObjectOut.addProperty("response", "returned no results");
            }
        //This is for the getPage Operation
        } else if (type.compareToIgnoreCase("getPage") == 0) {
            String pageTitle = jsonObjectIn.get("pageTitle").getAsString();
            String result;
            //this is executed to manage timeouts
            if (jsonObjectIn.has("timeout")) {
                //gets timeout from the json obj
                long timeoutDuration = jsonObjectIn.get("timeout").getAsLong();
                final Duration timeout = Duration.ofSeconds(timeoutDuration);

                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<String> future =
                    executor.submit(() -> wikiMediator.getPage(pageTitle));
                try {
                    result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (TimeoutException | InterruptedException | ExecutionException e) {
                    future.cancel(true);
                    jsonObjectOut.addProperty("status", "failed");
                    jsonObjectOut.addProperty("response", "Operation timed out");
                    return jsonObjectOut;
                }
            } else {
                result = wikiMediator.getPage(pageTitle);
            }
            jsonObjectOut.addProperty("status", "success");
            jsonObjectOut.addProperty("response", result);
        }
        //This is for the trending operation
        else if (type.compareToIgnoreCase("trending") == 0) {

            int limit = jsonObjectIn.get("limit").getAsInt();
            List result = wikiMediator.trending(limit);

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
                jsonObjectOut.addProperty("response", "returned no results");

            }

        } else if (type.compareToIgnoreCase("peakLoad30s") == 0) {

            int result = wikiMediator.peakLoad30s();

            jsonObjectOut.addProperty("status", "success");
            jsonObjectOut.addProperty("response", result);

        } else if(type.compareToIgnoreCase("stop") == 0){
            jsonObjectOut.addProperty("response", "bye");
            shutdown = true;
        }
        else {

            jsonObjectOut.addProperty("status", "failed");
            jsonObjectOut.addProperty("response", "Operation type not found");

        }
        return jsonObjectOut;
    }


    /**
     * Start a WikiMediatorServer running on the default port.
     */
    public static void main(String[] args) {
        try {
            WikiMediatorServer server = new WikiMediatorServer(
                WIKI_PORT, 1);
            server.serve();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}




