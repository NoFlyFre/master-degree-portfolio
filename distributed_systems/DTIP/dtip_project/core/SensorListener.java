package core;

import interfaces.DTIPNodeInterface;
import model.IoC;
import util.ConsoleColors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple TCP server that listens for raw IoC data injection.
 * <p>
 * This allows external tools (like a SIEM or a simple netcat script) to push
 * Indicators of Compromise into the DTIP network without using the specific REST API.
 * <p>
 * <b>Protocol Format:</b>
 * <pre>TYPE:VALUE:CONFIDENCE:TAG1,TAG2</pre>
 * Example: {@code IP:192.168.1.1:80:botnet,critical}
 */
public class SensorListener implements Runnable {

    private int port;
    private DTIPNodeInterface node;
    private int myNodeId;
    private String myNodeName;
    private boolean running;
    private ServerSocket serverSocket;

    /**
     * Initializes the sensor listener.
     *
     * @param port TCP port to bind (typically 9000 + nodeId).
     * @param node The parent DTIPNode to publish received IoCs to.
     * @param myNodeId Node ID for logging.
     * @param myNodeName Node Name for logging.
     */
    public SensorListener(int port, DTIPNodeInterface node, int myNodeId, String myNodeName) {
        this.port = port;
        this.node = node;
        this.myNodeId = myNodeId;
        this.myNodeName = myNodeName;
        this.running = true;
    }

    /**
     * Main loop. Accepts incoming TCP connections and spawns a handler thread for each.
     */
    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            ConsoleColors.printInfo("SensorListener active on port " + port);

            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleConnection(client)).start();
            }
        } catch (IOException e) {
            if (running) {
                ConsoleColors.printError("SensorListener error: " + e.getMessage());
            }
        }
    }

    /**
     * Reads a single line from the socket and attempts to parse it as an IoC.
     */
    private void handleConnection(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()))) {
            String line = reader.readLine();
            if (line != null && !line.isEmpty()) {
                parseAndPublish(line);
            }
        } catch (IOException e) {
            // Ignore connection errors
        } finally {
            try { client.close(); } catch (Exception e) {}
        }
    }

    /**
     * Parses the raw string and invokes {@link DTIPNodeInterface#publishIoC(IoC)}.
     *
     * @param raw The raw input string (e.g., "IP:1.2.3.4:90").
     */
    private void parseAndPublish(String raw) {
        try {
            String[] parts = raw.split(":");
            if (parts.length < 3) {
                System.out.println(ConsoleColors.YELLOW + "[Sensor] Invalid format: " + raw + ConsoleColors.RESET);
                return;
            }

            IoC.IoCType type = IoC.IoCType.valueOf(parts[0].toUpperCase());
            String value = parts[1];
            int confidence = Integer.parseInt(parts[2]);

            List<String> tags = new ArrayList<>();
            if (parts.length > 3 && parts[3] != null && !parts[3].isEmpty()) {
                String[] tagParts = parts[3].split(",");
                for (String t : tagParts) tags.add(t.trim());
            }

            IoC ioc = new IoC(type, value, confidence, tags, myNodeId, myNodeName + "-Sensor");
            node.publishIoC(ioc);

        } catch (Exception e) {
            System.out.println(ConsoleColors.YELLOW + "[Sensor] Failed to parse: " + raw + " (" + e.getMessage() + ")" + ConsoleColors.RESET);
        }
    }

    /**
     * Stops the listener server.
     */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException e) {}
    }
}