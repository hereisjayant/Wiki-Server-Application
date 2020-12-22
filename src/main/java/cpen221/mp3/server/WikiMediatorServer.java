package cpen221.mp3.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import cpen221.mp3.wikimediator.WikiMediator;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WikiMediatorServer {


    private final ServerSocket serverSocket;
    private final ExecutorService pool;


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

            //Creates a new executer service to handle n requests:
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

        BufferedReader bufferedReader =
            new BufferedReader(new InputStreamReader(socket.getInputStream()));
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

        JsonObject jsonObjectIn = new Gson().fromJson(bufferedReader, JsonObject.class);
        double timeout = jsonObjectIn.get("timeout").getAsDouble();


        try {
            dataOutputStream.writeUTF(getJsonResult(jsonObjectIn).getAsString());
            dataOutputStream.flush();
        } finally {
            bufferedReader.close();
            dataOutputStream.close();
        }

    }

    private JsonObject getJsonResult(JsonObject jsonObjectIn) {

        JsonObject jsonObjectOut = new JsonObject();
        WikiMediator wikiMediator = new WikiMediator();

        int id = jsonObjectIn.get("id").getAsInt();
        jsonObjectOut.addProperty("id", id);

        String type = jsonObjectIn.get("type").getAsString();

        if (type.compareToIgnoreCase("search") == 0) {
            String query = jsonObjectIn.get("query").getAsString();
            int limit = jsonObjectIn.get("limit").getAsInt();
            List result = wikiMediator.search(query, limit);

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
            }
        } else if (type.compareToIgnoreCase("zeitgeist") == 0) {
            int limit = jsonObjectIn.get("limit").getAsInt();
            List result = wikiMediator.zeitgeist(limit);

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
            }
        } else if (type.compareToIgnoreCase("getPage") == 0) {
            String pageTitle = jsonObjectIn.get("pageTitle").getAsString();
            String result = wikiMediator.getPage(pageTitle);

            jsonObjectOut.addProperty("status", "success");
            jsonObjectOut.addProperty("response", result);
        } else if (type.compareToIgnoreCase("trending") == 0) {
            int limit = jsonObjectIn.get("limit").getAsInt();
            List result = wikiMediator.trending(limit);

            if (!result.isEmpty()) {
                jsonObjectOut.addProperty("status", "success");
                String resultJson = new Gson().toJson(result);
                jsonObjectOut.addProperty("response", resultJson);
            } else {
                jsonObjectOut.addProperty("status", "failed");
            }
        } else if (type.compareToIgnoreCase("peakLoad30s") == 0) {

            int result = wikiMediator.peakLoad30s();

            jsonObjectOut.addProperty("status", "success");
            jsonObjectOut.addProperty("response", result);
        } else {
            jsonObjectOut.addProperty("status", "failed");
        }
        return jsonObjectOut;
    }


}




