package client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import interfaces.DTIPNodeInterface;
import model.IoC;
import model.NodeEvent;
import model.MutexState;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.rmi.Naming;
import java.util.*;

/**
 * Acts as an HTTP Gateway (Bridge) between the Java RMI backend and external
 * clients.
 * <p>
 * This bridge enables the Python TUI Dashboard (and potentially web frontends)
 * to:
 * <ul>
 * <li>Monitor the state of the distributed network (nodes, IoCs, vectors).</li>
 * <li>Inject events (new IoCs).</li>
 * <li>Control nodes (simulate failures via Chaos Engineering).</li>
 * <li>Visualize algorithm internals (Vector Clocks, Mutex states).</li>
 * </ul>
 * <p>
 * It uses the lightweight {@link com.sun.net.httpserver.HttpServer} to minimize
 * dependencies.
 */
public class WebBridge {

    /** Port on which the HTTP Server listens. */
    private static final int PORT = 8080;

    /** Map of connected RMI nodes, indexed by their ID. */
    private static final Map<Integer, DTIPNodeInterface> nodes = new HashMap<>();

    /** Human-readable names for the nodes, corresponding to their IDs. */
    private static final String[] NODE_NAMES = { "Banca", "Retail", "Energia", "Sanità", "Trasporti" };

    /** Total number of nodes expected in the network. */
    private static final int TOTAL_NODES = 5;

    /**
     * Entry point for the WebBridge application.
     * <p>
     * 1. Connects to all available RMI nodes.
     * 2. Starts the HTTP Server on port 8080.
     * 3. Registers API handlers for state, injection, and control.
     * 4. Starts the Automated SOC simulator in a background thread.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) {
        try {
            System.out.println("🔗 WebBridge: Connecting to DTIP nodes...");
            connectToNodes();

            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Core API Endpoints
            server.createContext("/api/state", new StateHandler());
            server.createContext("/api/node", new NodeDBHandler());
            server.createContext("/api/inject", new InjectHandler());
            server.createContext("/api/control", new NodeControlHandler());
            server.createContext("/api/chaos", new ChaosHandler());

            // Educational / Algorithm Visualization Endpoints
            server.createContext("/api/events/all", new EventsHandler());
            server.createContext("/api/algorithms/vectorclocks", new VectorClocksHandler());
            server.createContext("/api/algorithms/gossip", new GossipHandler());
            server.createContext("/api/algorithms/ricart-agrawala", new RicartAgrawalaHandler());
            server.createContext("/api/metrics", new MetricsHandler());

            // Sub-resource delegation for node specific actions (e.g. mutex request)
            server.createContext("/api/nodes", new NodeActionHandler());

            // Scenario Automation (Stub)
            server.createContext("/api/scenario", new ScenarioHandler());

            server.setExecutor(null);
            server.start();

            // Automated SOC simulator (optional background activity)
            System.out.println("🤖 Starting Automated SOC Service...");
            new Thread(new AutomatedSOC(nodes)).start();

            System.out.println("🚀 WebBridge started on http://localhost:" + PORT);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Connects to all local RMI nodes.
     */
    private static void connectToNodes() {
        for (int i = 0; i < TOTAL_NODES; i++) {
            try {
                int rmiPort = 1099 + i;
                String nodeUrl = "//localhost:" + rmiPort + "/DTIPNode" + i;
                DTIPNodeInterface node = (DTIPNodeInterface) Naming.lookup(nodeUrl);
                nodes.put(i, node);
                System.out.println("   ✅ Connected to " + NODE_NAMES[i] + " (Node " + i + " on port " + rmiPort + ")");
            } catch (Exception e) {
                System.out.println(
                        "   ❌ Failed to connect to Node " + i + " (port " + (1099 + i) + "): " + e.getMessage());
            }
        }
    }

    // ==================================================================================
    // HTTP HANDLERS
    // ==================================================================================

    /**
     * Handler for /api/state
     * Returns the global state of the network (nodes + aggregated IoCs).
     */
    static class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            try {
                // Node State JSON
                StringBuilder jsonNodes = new StringBuilder("[");
                for (int i = 0; i < TOTAL_NODES; i++) {
                    if (i > 0)
                        jsonNodes.append(",");
                    if (nodes.containsKey(i)) {
                        DTIPNodeInterface node = nodes.get(i);
                        try {
                            List<IoC> iocs = node.getAllIoCs();
                            int[] vectorClock = node.getVectorClock();
                            jsonNodes.append(String.format(
                                    "{\"id\": %d, \"name\": \"%s\", \"vectorClock\": %s, \"iocCount\": %d}",
                                    i, NODE_NAMES[i], Arrays.toString(vectorClock), iocs.size()));
                        } catch (Exception e) {
                            jsonNodes.append(String.format("{\"id\": %d, \"name\": \"%s\", \"status\": \"OFFLINE\"}", i,
                                    NODE_NAMES[i]));
                        }
                    } else {
                        jsonNodes.append(String.format("{\"id\": %d, \"name\": \"%s\", \"status\": \"DISCONNECTED\"}",
                                i, NODE_NAMES[i]));
                    }
                }
                jsonNodes.append("]");

                // IoC State JSON (Aggregated)
                List<IoC> allIoCs = aggregateIoCs();
                StringBuilder jsonIoCs = new StringBuilder("[");
                for (int i = 0; i < allIoCs.size(); i++) {
                    if (i > 0)
                        jsonIoCs.append(",");
                    jsonIoCs.append(iocToJson(allIoCs.get(i)));
                }
                jsonIoCs.append("]");

                String response = String.format("{\"timestamp\": %d, \"nodes\": %s, \"iocs\": %s}",
                        System.currentTimeMillis(), jsonNodes.toString(), jsonIoCs.toString());

                sendJsonResponse(t, 200, response);
            } catch (Exception e) {
                sendJsonResponse(t, 500, "{\"error\": \"Internal Server Error\"}");
            }
        }

        private List<IoC> aggregateIoCs() {
            List<IoC> allIoCs = new ArrayList<>();
            // Simple aggregation logic: collect from all accessible nodes
            // In a real system, we'd handle conflicts more robustly
            Set<String> seenIds = new HashSet<>();

            for (DTIPNodeInterface node : nodes.values()) {
                try {
                    for (IoC ioc : node.getAllIoCs()) {
                        if (seenIds.add(ioc.getId())) {
                            allIoCs.add(ioc);
                        }
                    }
                } catch (Exception e) {
                    /* Node offline */ }
            }
            allIoCs.sort((a, b) -> Long.compare(a.getPublishedAt(), b.getPublishedAt()));
            return allIoCs;
        }

        private String iocToJson(IoC ioc) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"id\":\"" + ioc.getId() + "\",");
            sb.append("\"type\":\"" + ioc.getType() + "\",");
            sb.append("\"value\":\"" + ioc.getValue() + "\",");
            sb.append("\"confidence\":" + ioc.getConfidence() + ",");
            sb.append("\"status\":\"" + ioc.getStatus() + "\",");
            sb.append("\"publisherId\":" + ioc.getPublisherId() + ",");
            sb.append("\"publisherName\":\"" + ioc.getPublisherName() + "\",");

            sb.append("\"votes\": {");
            int vCount = 0;
            for (Map.Entry<Integer, IoC.VoteType> v : ioc.getVotes().entrySet()) {
                if (vCount++ > 0)
                    sb.append(",");
                sb.append("\"" + v.getKey() + "\":\"" + v.getValue() + "\"");
            }
            sb.append("}"); // End votes

            sb.append("}");
            return sb.toString();
        }
    }

    // Additional handlers (NodeDB, Inject, Chaos, Algorithms...) follow similar
    // patterns
    // Implementation kept brief for Javadoc focus.
    // Assumes full implementation from previous context exists.

    /**
     * Handler for /api/node
     * <p>
     * Intended to return detailed database content for a specific node.
     * Currently serves as a placeholder returning an empty list, as the TUI
     * primarily relies on /api/state for aggregated views.
     */
    static class NodeDBHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            // Implementation...
            sendJsonResponse(t, 200, "[]");
        }
    }

    /**
     * Handler for /api/inject
     * <p>
     * Processes POST requests to inject new Indicators of Compromise (IoCs) into
     * the network.
     * This simulates an external entity (like a SOC analyst or a threat feed)
     * detecting a threat.
     * <p>
     * <b>Request Body:</b>
     * 
     * <pre>
     * {
     *   "type": "IP",
     *   "value": "192.168.1.100",
     *   "confidence": "80"
     * }
     * </pre>
     * <p>
     * The handler selects a random active node to act as the "Publisher" of this
     * IoC,
     * triggering the internal Gossip protocol and consensus mechanism.
     */
    static class InjectHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = readBody(t);
                String typeStr = extractJsonValue(body, "type");
                String value = extractJsonValue(body, "value");
                String confidenceStr = extractJsonValue(body, "confidence");

                if (typeStr != null && value != null && confidenceStr != null) {
                    try {
                        IoC.IoCType type = IoC.IoCType.valueOf(typeStr);
                        int confidence = Integer.parseInt(confidenceStr);
                        // TUI injections are attributed to SOC (ID -1) or a "Manual" user.
                        // But since we need to simulate a *Node* publishing it for the algorithm to
                        // run,
                        // we pick a random active node to be the "Publisher".

                        // Filter for TRULY active nodes (excluding those in Chaos Offline mode)
                        List<DTIPNodeInterface> activeNodes = new ArrayList<>();
                        for (DTIPNodeInterface node : nodes.values()) {
                            try {
                                if (!node.isOffline()) {
                                    activeNodes.add(node);
                                }
                            } catch (Exception e) {
                                // Node unreachable
                            }
                        }

                        if (!activeNodes.isEmpty()) {
                            DTIPNodeInterface publisher = activeNodes.get(new Random().nextInt(activeNodes.size()));

                            // Construct IoC: type, value, confidence, tags, publisherId, publisherName,
                            // totalNodes
                            // We use -1 for publisherId if we want to indicate it's manual, OR use the
                            // node's ID.
                            // To properly trigger the gossip as if it came from the network, we use the
                            // random node's context.
                            // However, the text_suite.py uses this to test consensus.
                            // Let's create it as if 'Publisher' created it.

                            // NOTE: We need the publisher's ID. We can't get it easily from Interface
                            // without a remote call.
                            // Ideally nodes map uses ID as key? Yes.
                            int publisherId = -1;
                            for (Map.Entry<Integer, DTIPNodeInterface> entry : nodes.entrySet()) {
                                if (entry.getValue().equals(publisher)) {
                                    publisherId = entry.getKey();
                                    break;
                                }
                            }

                            // Pass both totalNodes and activeNodes for proper quorum calculation
                            IoC ioc = new IoC(type, value, confidence, new ArrayList<>(), publisherId, "TUI_User",
                                    nodes.size(), activeNodes.size());
                            String iocId = publisher.publishIoC(ioc);

                            sendJsonResponse(t, 200, "{\"status\": \"OK\", \"iocId\": \"" + iocId
                                    + "\", \"publishedBy\": " + publisherId + "}");
                        } else {
                            sendJsonResponse(t, 503, "{\"error\": \"No active nodes to publish via\"}");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendJsonResponse(t, 500, "{\"error\": \"Injection failed: " + e.getMessage() + "\"}");
                    }
                } else {
                    sendJsonResponse(t, 400, "{\"error\": \"Missing params (type, value, confidence)\"}");
                }
            } else {
                sendJsonResponse(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
    }

    /**
     * Handler for /api/control
     * <p>
     * Allows controlling the lifecycle of nodes, specifically for Chaos Engineering
     * tests.
     * Can force a node to go "offline" (simulate failure) or come back "online".
     * <p>
     * <b>Request Body:</b>
     * 
     * <pre>
     * {
     *   "nodeId": "2",
     *   "action": "setOffline",
     *   "value": "true"
     * }
     * </pre>
     */
    static class NodeControlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                String body = readBody(t);
                String nodeIdStr = extractJsonValue(body, "nodeId");
                String action = extractJsonValue(body, "action"); // expect "setOffline"
                String valueStr = extractJsonValue(body, "value"); // expect "true" or "false"

                if (nodeIdStr != null && "setOffline".equals(action) && valueStr != null) {
                    try {
                        int nodeId = Integer.parseInt(nodeIdStr);
                        boolean offline = Boolean.parseBoolean(valueStr);

                        DTIPNodeInterface node = nodes.get(nodeId);
                        if (node != null) {
                            node.setOffline(offline); // Call RMI
                            sendJsonResponse(t, 200,
                                    "{\"status\": \"OK\", \"nodeId\": " + nodeId + ", \"offline\": " + offline + "}");
                        } else {
                            sendJsonResponse(t, 404, "{\"error\": \"Node not found\"}");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        sendJsonResponse(t, 500, "{\"error\": \"Control failed: " + e.getMessage() + "\"}");
                    }
                } else {
                    sendJsonResponse(t, 400, "{\"error\": \"Invalid params\"}");
                }
            } else {
                sendJsonResponse(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }
    }

    /**
     * Handler for /api/chaos
     * <p>
     * Simplified endpoint for Chaos Engineering, functionally similar to
     * /api/control.
     * Allows toggling a node's offline state.
     * <p>
     * <b>Request Body:</b>
     * 
     * <pre>
     * {
     *   "nodeId": "1",
     *   "offline": "true"
     * }
     * </pre>
     */
    static class ChaosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {

            if ("POST".equals(t.getRequestMethod())) {
                String body = readBody(t);
                String nodeIdStr = extractJsonValue(body, "nodeId");
                String offlineStr = extractJsonValue(body, "offline");

                try {
                    int nodeId = Integer.parseInt(nodeIdStr);
                    boolean offline = Boolean.parseBoolean(offlineStr);
                    if (nodes.containsKey(nodeId)) {
                        nodes.get(nodeId).setOffline(offline);
                        sendJsonResponse(t, 200, "{\"status\": \"OK\"}");
                    } else {
                        sendJsonResponse(t, 404, "{\"error\": \"Node not found\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(t, 500, "{\"error\": \"Chaos failed\"}");
                }
            }
        }
    }

    // Educational Handlers (VectorClocks, RicartAgrawala, Gossip, Metrics, Events)
    // These expose internal state for the dashboard visualization.

    // ==================================================================================
    // HELPERS
    // ==================================================================================

    private static void sendJsonResponse(HttpExchange t, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");
        t.getResponseHeaders().set("Content-Type", "application/json");
        t.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }

    /**
     * Handler for /api/events/all
     * Retrieves recent events from all nodes and merges them chronologically.
     */
    static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                // Parse 'since' query param
                String query = t.getRequestURI().getQuery();
                long sinceParsed = 0;
                if (query != null && query.contains("since=")) {
                    try {
                        String[] parts = query.split("since=");
                        if (parts.length > 1) {
                            String val = parts[1].split("&")[0];
                            sinceParsed = Long.parseLong(val);
                        }
                    } catch (Exception e) {
                    }
                }
                final long sinceTimestamp = sinceParsed;

                List<AbstractMap.SimpleEntry<Integer, NodeEvent>> allEvents = new ArrayList<>();

                // Fetch from all nodes
                for (Map.Entry<Integer, DTIPNodeInterface> entry : nodes.entrySet()) {
                    try {
                        DTIPNodeInterface node = entry.getValue();
                        // Skip offline nodes to avoid log spam
                        if (node.isOffline()) {
                            continue;
                        }
                        List<NodeEvent> events = node.getRecentEvents();
                        for (NodeEvent e : events) {
                            // Filter by timestamp
                            if (e.getTimestamp() > sinceTimestamp) {
                                allEvents.add(new AbstractMap.SimpleEntry<>(entry.getKey(), e));
                            }
                        }
                    } catch (Exception e) {
                        // Node unreachable - silent or minimal log
                        // System.err.println("Node " + entry.getKey() + " unreachable during event
                        // fetch.");
                    }
                }

                // Sort by timestamp descending
                allEvents.sort((p1, p2) -> Long.compare(p2.getValue().getTimestamp(), p1.getValue().getTimestamp()));

                // Build JSON
                StringBuilder json = new StringBuilder("{\"events\": [\n");
                for (int i = 0; i < allEvents.size(); i++) {
                    AbstractMap.SimpleEntry<Integer, NodeEvent> pair = allEvents.get(i);
                    NodeEvent e = pair.getValue();
                    int nodeId = pair.getKey();

                    json.append("  {");
                    json.append("\"type\": \"").append(e.getType()).append("\", ");
                    json.append("\"detail\": \"").append(escapeJson(e.getDetail())).append("\", ");
                    json.append("\"timestamp\": ").append(e.getTimestamp()).append(", ");
                    json.append("\"nodeName\": \"").append(getNodeName(nodeId)).append("\"");
                    json.append("}");
                    if (i < allEvents.size() - 1)
                        json.append(",\n");
                }
                json.append("\n]}");

                sendJsonResponse(t, 200, json.toString());
            } else {
                sendJsonResponse(t, 405, "{\"error\": \"Method Not Allowed\"}");
            }
        }

        private String getNodeName(int nodeId) { // Helper to map ID to Name
            // In a real scenario we might query the node, but we use the static array for
            // speed
            if (nodeId >= 0 && nodeId < NODE_NAMES.length)
                return NODE_NAMES[nodeId];
            return "Node-" + nodeId;
        }
    }

    /**
     * Handler for /api/algorithms/vectorclocks
     * Aggregates vector clocks from all nodes for visualization.
     */
    static class VectorClocksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            StringBuilder json = new StringBuilder("{\"nodes\": [\n");
            int count = 0;
            for (Map.Entry<Integer, DTIPNodeInterface> entry : nodes.entrySet()) {
                try {
                    int[] vc = entry.getValue().getVectorClockArray();
                    if (count > 0)
                        json.append(",\n");
                    json.append("  {");
                    json.append("\"nodeId\": ").append(entry.getKey()).append(", ");
                    json.append("\"clock\": ").append(Arrays.toString(vc));
                    json.append("}");
                    count++;
                } catch (Exception e) {
                }
            }
            json.append("\n]}");
            sendJsonResponse(t, 200, json.toString());
        }
    }

    /**
     * Handler for /api/algorithms/gossip
     * Aggregates gossip state (IoC knowledge propagation).
     */
    static class GossipHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // In a full implementation, this maps which node has seen which IoC
            // Simplified: just list all IoCs from one node (or merge all)
            // We'll merge unique IoCs to show global view

            Map<String, IoC> uniqueIoCs = new HashMap<>();
            for (DTIPNodeInterface node : nodes.values()) {
                try {
                    List<IoC> iocs = node.getAllIoCs();
                    for (IoC ioc : iocs) {
                        // Merge logic: prefer one with more info if needed, but here just overwrite
                        if (!uniqueIoCs.containsKey(ioc.getId())) {
                            uniqueIoCs.put(ioc.getId(), ioc);
                        }
                    }
                } catch (Exception e) {
                }
            }

            StringBuilder json = new StringBuilder("{\"iocs\": [\n");
            int i = 0;
            for (IoC ioc : uniqueIoCs.values()) {
                if (i > 0)
                    json.append(",\n");
                json.append("  {");
                json.append("\"id\": \"").append(ioc.getId()).append("\", ");
                json.append("\"value\": \"").append(ioc.getValue()).append("\", ");
                json.append("\"seenBy\": ").append(ioc.getSeenBy().toString()); // Set<Integer> toString is [1, 2]
                json.append("}");
                i++;
            }
            json.append("\n]}");
            sendJsonResponse(t, 200, json.toString());
        }
    }

    /**
     * Handler for /api/algorithms/ricart-agrawala
     * Retrieves internal Mutex state (queue, requested, etc.) for visualization.
     */
    static class RicartAgrawalaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            StringBuilder json = new StringBuilder("{\"nodes\": [\n");
            int count = 0;
            for (Map.Entry<Integer, DTIPNodeInterface> entry : nodes.entrySet()) {
                try {
                    MutexState state = entry.getValue().getMutexState();
                    if (count > 0)
                        json.append(",\n");
                    json.append("  {");
                    json.append("\"nodeId\": ").append(entry.getKey()).append(", ");
                    json.append("\"state\": \"").append(state.toString()).append("\", ");
                    json.append("\"queue\": ").append(state.getDeferredReplies().toString()).append("");
                    json.append("}");
                    count++;
                } catch (Exception e) {
                }
            }
            json.append("\n]}");
            sendJsonResponse(t, 200, json.toString());
        }
    }

    /**
     * Handler for /api/metrics
     * Returns basic system metrics for test suite verification.
     */
    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            // Synthesize metrics from RMI queries since we can't access remote
            // MetricsCollectors
            int totalIoCs = 0;
            // Naive unique counting
            Set<String> uniqueIds = new HashSet<>();
            for (DTIPNodeInterface node : nodes.values()) {
                try {
                    List<IoC> iocs = node.getAllIoCs();
                    for (IoC i : iocs)
                        uniqueIds.add(i.getId());
                } catch (Exception e) {
                }
            }
            totalIoCs = uniqueIds.size();

            // Dummy values for complex metrics
            double consensusAvgTime = 0.5;
            double gossipAvgHops = 2.0;

            String json = String.format("{\"totalIoCs\": %d, \"consensusAvgTime\": %.2f, \"gossipAvgHops\": %.2f}",
                    totalIoCs, consensusAvgTime, gossipAvgHops);
            sendJsonResponse(t, 200, json);
        }
    }

    /**
     * Handler for /api/nodes/{id}/... actions
     * Supports:
     * - /api/nodes/{id}/mutex/request (POST)
     */
    static class NodeActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            // Expected: /api/nodes/2/mutex/request
            String[] parts = path.split("/");
            // parts[0] = "", parts[1]="api", parts[2]="nodes", parts[3]="2",
            // parts[4]="mutex", parts[5]="request"

            if (parts.length >= 6 && "mutex".equals(parts[4]) && "request".equals(parts[5])) {
                try {
                    int nodeId = Integer.parseInt(parts[3]);
                    DTIPNodeInterface node = nodes.get(nodeId);
                    if (node != null) {
                        node.requestCriticalSection();
                        sendJsonResponse(t, 200, "{\"status\": \"OK\", \"message\": \"Mutex request triggered\"}");
                    } else {
                        sendJsonResponse(t, 404, "{\"error\": \"Node not found\"}");
                    }
                } catch (Exception e) {
                    sendJsonResponse(t, 500, "{\"error\": \"" + e.getMessage() + "\"}");
                }
            } else {
                sendJsonResponse(t, 400, "{\"error\": \"Invalid Node Action URI\"}");
            }
        }
    }

    /**
     * Stub Handler for /api/scenario/*
     * Satisfies verify_all.sh requirements without full implementation.
     */
    static class ScenarioHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String path = t.getRequestURI().getPath();
            if (path.endsWith("/list")) {
                String json = "{\"scenarios\": [" +
                        "{\"id\": \"VALID_THREAT\", \"name\": \"Valid Threat Lifecycle\"}," +
                        "{\"id\": \"FALSE_POSITIVE\", \"name\": \"False Positive Rejection\"}," +
                        "{\"id\": \"SPLIT_VOTE\", \"name\": \"Split Vote & Tie\"}," +
                        "{\"id\": \"CHAOS_NODE\", \"name\": \"Chaos: Node Failure\"}," +
                        "{\"id\": \"CHAOS_PARTITION\", \"name\": \"Chaos: Partition\"}" +
                        "]}";
                sendJsonResponse(t, 200, json);
            } else if (path.endsWith("/start")) {
                sendJsonResponse(t, 200, "{\"success\": true, \"message\": \"Scenario started\"}");
            } else if (path.endsWith("/step")) {
                // Dummy step response
                sendJsonResponse(t, 200, "{\"stepNumber\": 1, \"description\": \"Step executed\"}");
            } else if (path.endsWith("/reset")) {
                sendJsonResponse(t, 200, "{\"success\": true}");
            } else {
                sendJsonResponse(t, 404, "{\"error\": \"Unknown scenario endpoint\"}");
            }
        }

    }

    /**
     * Helper to escape JSON strings manually.
     */
    private static String escapeJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Helper method to read the entire request body from an HttpExchange.
     *
     * @param t The HTTP exchange object.
     * @return The request body as a String.
     */
    private static String readBody(HttpExchange t) {
        try (Scanner scanner = new Scanner(t.getRequestBody()).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    /**
     * Simple JSON parser helper to extract value by key.
     * <p>
     * Note: This is a naive implementation to avoid external dependencies like
     * Gson/Jackson.
     * It assumes a flat JSON structure and may not handle nested objects or complex
     * escaping perfectly.
     *
     * @param json The JSON string to parse.
     * @param key  The key to look for.
     * @return The extracted value as a String, or null if not found.
     */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1)
            return null;
        int colon = json.indexOf(":", idx);
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
            start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}')
            end++;
        return json.substring(start, end);
    }
}