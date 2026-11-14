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

/**
 * Console-based campus navigation using an undirected weighted graph.
 * Compile with: javac CampusNavigationApp.java
 * Run with:     java CampusNavigationApp
 */
public class CampusNavigationApp {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Map<String, Integer> LOCATION_INDEX = new HashMap<>();
    private static final List<String> LOCATION_NAMES = new ArrayList<>();
    private static final List<List<Edge>> ADJACENCY_LIST = new ArrayList<>();

    public static void main(String[] args) {
        loadSampleMap();
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

