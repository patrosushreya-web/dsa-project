import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Campus navigation using an undirected weighted graph.
 * Compile with:     javac CampusNavigationApp.java
 * Run the console:  java CampusNavigationApp
 * Run the browser:  java CampusNavigationApp --server
 */
public class CampusNavigationApp {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Map<String, Integer> LOCATION_INDEX = new HashMap<>();
    private static final List<String> LOCATION_NAMES = new ArrayList<>();
    private static final List<List<Edge>> ADJACENCY_LIST = new ArrayList<>();

    public static void main(String[] args) {
        loadSampleMap();
        if (args.length > 0 && "--server".equalsIgnoreCase(args[0])) {
            int port = 8080;
            if (args.length > 1) {
                try {
                    port = Integer.parseInt(args[1]);
                } catch (NumberFormatException exception) {
                    System.out.println("Port must be a number between 1 and 65535.");
                    return;
                }
            }
            if (port < 1 || port > 65535) {
                System.out.println("Port must be a number between 1 and 65535.");
                return;
            }
            try {
                startWebServer(port);
            } catch (IOException exception) {
                System.out.println("Could not start the web server: " + exception.getMessage());
            }
            return;
        }
        runMenu();
    }

    private static void loadSampleMap() {
        addLocation("Gate");
        addLocation("Library");
        addLocation("Admin");
        addLocation("Canteen");

        addPath("Gate", "Library", 100);
        addPath("Gate", "Admin", 50);
        addPath("Admin", "Library", 40);
        addPath("Library", "Canteen", 60);
        addPath("Admin", "Canteen", 200);
    }

    private static void runMenu() {
        while (true) {
            System.out.println("\n=== Campus Navigation App ===");
            System.out.println("1. Add location");
            System.out.println("2. Add path");
            System.out.println("3. Find shortest distance");
            System.out.println("4. Find path with fewest stops");
            System.out.println("5. Show campus map");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            Integer choice = readInteger();
            if (choice == null) {
                System.out.println("Please enter a valid menu number.");
                continue;
            }

            switch (choice) {
                case 1 -> addLocationFromConsole();
                case 2 -> addPathFromConsole();
                case 3 -> findShortestDistanceFromConsole();
                case 4 -> findFewestStopsFromConsole();
                case 5 -> showCampusMap();
                case 0 -> {
                    System.out.println("Goodbye.");
                    return;
                }
                default -> System.out.println("Unknown option. Choose 0 through 5.");
            }
        }
    }

    private static void addLocationFromConsole() {
        System.out.print("Location name: ");
        String name = SCANNER.nextLine().trim();
        if (addLocation(name)) {
            System.out.println("Location " + name + " added.");
        }
    }

    private static boolean addLocation(String name) {
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            System.out.println("Location name cannot be empty.");
            return false;
        }

        String key = normalize(cleaned);
        if (LOCATION_INDEX.containsKey(key)) {
            System.out.println("Location " + cleaned + " already exists.");
            return false;
        }

        int vertex = LOCATION_NAMES.size();
        LOCATION_INDEX.put(key, vertex);
        LOCATION_NAMES.add(cleaned);
        ADJACENCY_LIST.add(new ArrayList<>());
        return true;
    }

    private static void addPathFromConsole() {
        System.out.print("From: ");
        String from = SCANNER.nextLine().trim();
        System.out.print("To: ");
        String to = SCANNER.nextLine().trim();
        System.out.print("Distance in metres: ");
        Integer distance = readInteger();
        if (distance == null) {
            System.out.println("Invalid distance. Enter a whole number in metres.");
            return;
        }
        if (addPath(from, to, distance)) {
            System.out.println("Path added between " + displayName(from) + " and "
                    + displayName(to) + " (" + distance + " m, two-way).");
        }
    }

    private static boolean addPath(String from, String to, int distance) {
        if (distance < 0) {
            System.out.println("Distance cannot be negative.");
            return false;
        }

        Integer fromIndex = findLocation(from);
        if (fromIndex == null) {
            printUnknownLocation(from);
            return false;
        }
        Integer toIndex = findLocation(to);
        if (toIndex == null) {
            printUnknownLocation(to);
            return false;
        }

        ADJACENCY_LIST.get(fromIndex).add(new Edge(toIndex, distance));
        ADJACENCY_LIST.get(toIndex).add(new Edge(fromIndex, distance));
        return true;
    }

    private static void findShortestDistanceFromConsole() {
        System.out.print("From: ");
        String from = SCANNER.nextLine().trim();
        System.out.print("To: ");
        String to = SCANNER.nextLine().trim();
        Integer source = resolveLocation(from);
        Integer destination = resolveLocation(to);
        if (source == null || destination == null) {
            return;
        }

        Route route = dijkstra(source, destination);
        if (route == null) {
            System.out.println("No path found between " + displayName(from)
                    + " and " + displayName(to) + ".");
            return;
        }
        System.out.println("Path: " + String.join(" -> ", route.locationNames));
        System.out.println("Distance: " + route.distanceMetres + " m");
    }

    private static void findFewestStopsFromConsole() {
        System.out.print("From: ");
        String from = SCANNER.nextLine().trim();
        System.out.print("To: ");
        String to = SCANNER.nextLine().trim();
        Integer source = resolveLocation(from);
        Integer destination = resolveLocation(to);
        if (source == null || destination == null) {
            return;
        }

        Route route = breadthFirstSearch(source, destination);
        if (route == null) {
            System.out.println("No path found between " + displayName(from)
                    + " and " + displayName(to) + ".");
            return;
        }
        System.out.println("Path: " + String.join(" -> ", route.locationNames)
                + " | Stops: " + route.stops + " | Distance: "
                + route.distanceMetres + " m");
    }

    /** Dijkstra's algorithm: minimizes total distance in metres. */
    private static Route dijkstra(int source, int destination) {
        int vertexCount = LOCATION_NAMES.size();
        long[] distances = new long[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(distances, Long.MAX_VALUE);
        Arrays.fill(parent, -1);
        distances[source] = 0;

        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingLong(entry -> entry.distance));
        queue.add(new QueueEntry(source, 0));

        while (!queue.isEmpty()) {
            QueueEntry current = queue.remove();
            if (current.distance != distances[current.vertex]) {
                continue;
            }
            if (current.vertex == destination) {
                break;
            }

            for (Edge edge : ADJACENCY_LIST.get(current.vertex)) {
                long candidate = current.distance + edge.distanceMetres;
                if (candidate < distances[edge.destination]) {
                    distances[edge.destination] = candidate;
                    parent[edge.destination] = current.vertex;
                    queue.add(new QueueEntry(edge.destination, candidate));
                }
            }
        }

        if (distances[destination] == Long.MAX_VALUE) {
            return null;
        }
        return makeRoute(source, destination, parent, distances[destination]);
    }

    /** BFS: minimizes the number of path segments, regardless of their lengths. */
    private static Route breadthFirstSearch(int source, int destination) {
        int vertexCount = LOCATION_NAMES.size();
        boolean[] visited = new boolean[vertexCount];
        int[] parent = new int[vertexCount];
        int[] parentEdgeDistance = new int[vertexCount];
        Arrays.fill(parent, -1);

        Queue<Integer> queue = new java.util.ArrayDeque<>();
        visited[source] = true;
        queue.add(source);

        while (!queue.isEmpty()) {
            int current = queue.remove();
            if (current == destination) {
                break;
            }
            for (Edge edge : ADJACENCY_LIST.get(current)) {
                if (!visited[edge.destination]) {
                    visited[edge.destination] = true;
                    parent[edge.destination] = current;
                    parentEdgeDistance[edge.destination] = edge.distanceMetres;
                    queue.add(edge.destination);
                }
            }
        }

        if (!visited[destination]) {
            return null;
        }
        List<Integer> vertexPath = reconstructPath(source, destination, parent);
        long totalDistance = 0;
        for (int i = 1; i < vertexPath.size(); i++) {
            totalDistance += parentEdgeDistance[vertexPath.get(i)];
        }
        return makeRoute(source, destination, parent, totalDistance);
    }

    private static Route makeRoute(int source, int destination, int[] parent,
                                   long totalDistance) {
        List<Integer> vertexPath = reconstructPath(source, destination, parent);
        if (vertexPath.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (int vertex : vertexPath) {
            names.add(LOCATION_NAMES.get(vertex));
        }
        return new Route(names, totalDistance, Math.max(0, vertexPath.size() - 1));
    }

    /** Reconstructs the source-to-destination path by following parent links. */
    private static List<Integer> reconstructPath(int source, int destination,
                                                 int[] parent) {
        List<Integer> reversePath = new ArrayList<>();
        int current = destination;
        while (current != -1) {
            reversePath.add(current);
            if (current == source) {
                break;
            }
            current = parent[current];
        }
        if (reversePath.get(reversePath.size() - 1) != source) {
            return new ArrayList<>();
        }
        List<Integer> path = new ArrayList<>();
        for (int i = reversePath.size() - 1; i >= 0; i--) {
            path.add(reversePath.get(i));
        }
        return path;
    }

    private static void showCampusMap() {
        System.out.println("Campus map (two-way paths):");
        for (int vertex = 0; vertex < LOCATION_NAMES.size(); vertex++) {
            System.out.print(LOCATION_NAMES.get(vertex) + ": ");
            List<String> neighbors = new ArrayList<>();
            for (Edge edge : ADJACENCY_LIST.get(vertex)) {
                neighbors.add(LOCATION_NAMES.get(edge.destination) + " ("
                        + edge.distanceMetres + " m)");
            }
            System.out.println(neighbors.isEmpty() ? "(no paths)"
                    : String.join(", ", neighbors));
        }
    }

    /** Starts a loopback-only web server that exposes the same graph to the browser UI. */
    private static void startWebServer(int port) throws IOException {
        Path webRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.createContext("/api/", CampusNavigationApp::handleApiRequest);
        server.createContext("/", exchange -> serveStaticFile(exchange, webRoot));
        server.start();
        System.out.println("Campus Navigation web app is running.");
        System.out.println("Open http://127.0.0.1:" + port + " in your browser.");
        System.out.println("Press Ctrl+C in this window to stop the server.");
    }

    private static void handleApiRequest(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("GET".equals(method) && "/api/map".equals(requestPath)) {
            sendJson(exchange, 200, mapAsJson());
            return;
        }

        if (!"POST".equals(method)) {
            sendJson(exchange, 405, errorJson("Use GET or POST for this API endpoint."));
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (body.length() > 100_000) {
            sendJson(exchange, 413, errorJson("Request is too large."));
            return;
        }

        switch (requestPath) {
            case "/api/route" -> handleRouteRequest(exchange, body);
            case "/api/locations" -> handleAddLocationRequest(exchange, body);
            case "/api/paths" -> handleAddPathRequest(exchange, body);
            default -> sendJson(exchange, 404, errorJson("API endpoint not found."));
        }
    }

    private static void handleRouteRequest(HttpExchange exchange, String body) throws IOException {
        String from = readJsonString(body, "from");
        String to = readJsonString(body, "to");
        String mode = readJsonString(body, "mode");
        if (from == null || to == null || mode == null) {
            sendJson(exchange, 400, errorJson("Enter a start, destination and route mode."));
            return;
        }

        Integer source = findLocation(from);
        if (source == null) {
            sendJson(exchange, 400, errorJson("Location " + from.trim() + " not found."));
            return;
        }
        Integer destination = findLocation(to);
        if (destination == null) {
            sendJson(exchange, 400, errorJson("Location " + to.trim() + " not found."));
            return;
        }

        Route route;
        if ("distance".equals(mode)) {
            route = dijkstra(source, destination);
        } else if ("stops".equals(mode)) {
            route = breadthFirstSearch(source, destination);
        } else {
            sendJson(exchange, 400, errorJson("Unknown route mode."));
            return;
        }

        if (route == null) {
            sendJson(exchange, 404,
                    errorJson("No path found between " + LOCATION_NAMES.get(source)
                            + " and " + LOCATION_NAMES.get(destination) + "."));
            return;
        }

        StringBuilder json = new StringBuilder("{\"ok\":true,\"path\":[");
        for (int i = 0; i < route.locationNames.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(route.locationNames.get(i)));
        }
        json.append("],\"distance\":").append(route.distanceMetres)
                .append(",\"stops\":").append(route.stops).append('}');
        sendJson(exchange, 200, json.toString());
    }

    private static void handleAddLocationRequest(HttpExchange exchange, String body) throws IOException {
        String name = readJsonString(body, "name");
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.isEmpty()) {
            sendJson(exchange, 400, errorJson("Location name cannot be empty."));
            return;
        }
        String key = normalize(cleaned);
        if (LOCATION_INDEX.containsKey(key)) {
            sendJson(exchange, 409, errorJson("Location " + cleaned + " already exists."));
            return;
        }

        int vertex = LOCATION_NAMES.size();
        LOCATION_INDEX.put(key, vertex);
        LOCATION_NAMES.add(cleaned);
        ADJACENCY_LIST.add(new ArrayList<>());
        sendJson(exchange, 201, "{\"ok\":true,\"name\":" + jsonString(cleaned) + "}");
    }

    private static void handleAddPathRequest(HttpExchange exchange, String body) throws IOException {
        String from = readJsonString(body, "from");
        String to = readJsonString(body, "to");
        Integer distance = readJsonInteger(body, "distance");
        if (from == null || to == null || distance == null) {
            sendJson(exchange, 400,
                    errorJson("Enter two locations and a whole-number distance in metres."));
            return;
        }
        if (distance < 0) {
            sendJson(exchange, 400, errorJson("Distance cannot be negative."));
            return;
        }

        Integer fromIndex = findLocation(from);
        if (fromIndex == null) {
            sendJson(exchange, 400, errorJson("Location " + from.trim() + " not found."));
            return;
        }
        Integer toIndex = findLocation(to);
        if (toIndex == null) {
            sendJson(exchange, 400, errorJson("Location " + to.trim() + " not found."));
            return;
        }
        if (fromIndex.equals(toIndex)) {
            sendJson(exchange, 400, errorJson("Choose two different locations for a path."));
            return;
        }
        boolean alreadyConnected = ADJACENCY_LIST.get(fromIndex).stream()
                .anyMatch(edge -> edge.destination == toIndex);
        if (alreadyConnected) {
            sendJson(exchange, 409, errorJson("A path between these places already exists."));
            return;
        }

        ADJACENCY_LIST.get(fromIndex).add(new Edge(toIndex, distance));
        ADJACENCY_LIST.get(toIndex).add(new Edge(fromIndex, distance));
        String message = "Path added between " + LOCATION_NAMES.get(fromIndex) + " and "
                + LOCATION_NAMES.get(toIndex) + " (" + distance + " m, two-way).";
        sendJson(exchange, 201, "{\"ok\":true,\"message\":" + jsonString(message) + "}");
    }

    private static String mapAsJson() {
        StringBuilder json = new StringBuilder("{\"locations\":[");
        for (int i = 0; i < LOCATION_NAMES.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(LOCATION_NAMES.get(i)));
        }
        json.append("],\"paths\":[");
        boolean first = true;
        for (int from = 0; from < ADJACENCY_LIST.size(); from++) {
            for (Edge edge : ADJACENCY_LIST.get(from)) {
                if (from >= edge.destination) continue;
                if (!first) json.append(',');
                first = false;
                json.append("{\"from\":").append(jsonString(LOCATION_NAMES.get(from)))
                        .append(",\"to\":").append(jsonString(LOCATION_NAMES.get(edge.destination)))
                        .append(",\"distance\":").append(edge.distanceMetres).append('}');
            }
        }
        return json.append("]}").toString();
    }

    private static void serveStaticFile(HttpExchange exchange, Path webRoot) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed.", "text/plain; charset=utf-8");
            return;
        }
        String requestPath = exchange.getRequestURI().getPath();
        String fileName = switch (requestPath) {
            case "/", "/index.html" -> "index.html";
            case "/styles.css" -> "styles.css";
            case "/app.js" -> "app.js";
            default -> null;
        };
        if (fileName == null) {
            sendText(exchange, 404, "File not found.", "text/plain; charset=utf-8");
            return;
        }

        Path file = webRoot.resolve(fileName).normalize();
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "Web files not found. Run the server from the project folder.",
                    "text/plain; charset=utf-8");
            return;
        }
        String contentType = fileName.endsWith(".css") ? "text/css; charset=utf-8"
                : fileName.endsWith(".js") ? "text/javascript; charset=utf-8"
                : "text/html; charset=utf-8";
        byte[] content = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, content.length);
        try (var output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String contentType)
            throws IOException {
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, content.length);
        try (var output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static String errorJson(String message) {
        return "{\"ok\":false,\"message\":" + jsonString(message) + "}";
    }

    private static String readJsonString(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? unescapeJson(matcher.group(1)) : null;
    }

    private static Integer readJsonInteger(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(field)
                + "\"\\s*:\\s*(-?\\d+)(?=\\s*[,}])");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) return null;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String unescapeJson(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                decoded.append(current);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"' -> decoded.append('"');
                case '\\' -> decoded.append('\\');
                case '/' -> decoded.append('/');
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        try {
                            decoded.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException exception) {
                            decoded.append('u');
                        }
                    } else {
                        decoded.append('u');
                    }
                }
                default -> decoded.append(escaped);
            }
        }
        return decoded.toString();
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static Integer resolveLocation(String name) {
        Integer index = findLocation(name);
        if (index == null) {
            printUnknownLocation(name);
        }
        return index;
    }

    private static Integer findLocation(String name) {
        return LOCATION_INDEX.get(normalize(name));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    private static void printUnknownLocation(String name) {
        String shown = name == null ? "" : name.trim();
        System.out.println("Location " + shown + " not found.");
    }

    private static String displayName(String name) {
        Integer index = findLocation(name);
        return index == null ? (name == null ? "" : name.trim())
                : LOCATION_NAMES.get(index);
    }

    private static Integer readInteger() {
        String input = SCANNER.nextLine().trim();
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static final class Edge {
        private final int destination;
        private final int distanceMetres;

        private Edge(int destination, int distanceMetres) {
            this.destination = destination;
            this.distanceMetres = distanceMetres;
        }
    }

    private static final class QueueEntry {
        private final int vertex;
        private final long distance;

        private QueueEntry(int vertex, long distance) {
            this.vertex = vertex;
            this.distance = distance;
        }
    }

    private static final class Route {
        private final List<String> locationNames;
        private final long distanceMetres;
        private final int stops;

        private Route(List<String> locationNames, long distanceMetres, int stops) {
            this.locationNames = locationNames;
            this.distanceMetres = distanceMetres;
            this.stops = stops;
        }
    }
}
