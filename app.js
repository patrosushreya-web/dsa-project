"use strict";

// Campus graph: each adjacency-list entry stores a neighboring vertex and its distance.
const locations = [];
const locationIndex = new Map();
const adjacencyList = [];
const defaultPositions = {
  Gate: { x: 137, y: 453 },
  Admin: { x: 395, y: 219 },
  Library: { x: 606, y: 282 },
  Canteen: { x: 464, y: 414 }
};
const defaultPaths = {
  "gate|admin": { d: "M 137 453 C 215 353, 298 274, 395 219", label: { x: 262, y: 337 } },
  "gate|library": { d: "M 137 453 C 294 432, 414 334, 606 282", label: { x: 373, y: 385 } },
  "admin|library": { d: "M 395 219 C 466 197, 542 222, 606 282", label: { x: 504, y: 224 } },
  "canteen|library": { d: "M 464 414 C 509 373, 565 329, 606 282", label: { x: 553, y: 369 } },
  "admin|canteen": { d: "M 395 219 C 354 307, 408 371, 464 414", label: { x: 390, y: 326 } }
};

const svgNamespace = "http://www.w3.org/2000/svg";
const routeForm = document.querySelector("#route-form");
const fromInput = document.querySelector("#from-location");
const toInput = document.querySelector("#to-location");
const mapSvg = document.querySelector("#campus-map");
let activeMode = "distance";
let activeRoute = null;
let zoom = 1;

function normalize(name) {
  return String(name ?? "").trim().toLocaleLowerCase();
}

function addLocation(name) {
  const displayName = String(name ?? "").trim();
  const key = normalize(displayName);
  if (!key) return { ok: false, message: "Enter a location name." };
  if (locationIndex.has(key)) return { ok: false, message: `${displayName} is already on the map.` };

  const vertex = locations.length;
  locations.push(displayName);
  locationIndex.set(key, vertex);
  adjacencyList.push([]);
  return { ok: true, vertex };
}

function addPath(fromName, toName, distance) {
  const from = locationIndex.get(normalize(fromName));
  const to = locationIndex.get(normalize(toName));
  if (from === undefined) return { ok: false, message: `Location ${String(fromName).trim()} not found.` };
  if (to === undefined) return { ok: false, message: `Location ${String(toName).trim()} not found.` };
  if (from === to) return { ok: false, message: "Choose two different locations for a path." };
  if (!Number.isInteger(distance) || distance < 0) return { ok: false, message: "Distance must be a non-negative whole number." };
  if (adjacencyList[from].some(edge => edge.to === to)) {
    return { ok: false, message: "A path between these places already exists." };
  }

  adjacencyList[from].push({ to, distance });
  adjacencyList[to].push({ to: from, distance });
  return { ok: true };
}

function dijkstra(source, destination) {
  const count = locations.length;
  const distances = Array(count).fill(Infinity);
  const parent = Array(count).fill(-1);
  const settled = Array(count).fill(false);
  distances[source] = 0;

  for (let step = 0; step < count; step += 1) {
    let current = -1;
    for (let vertex = 0; vertex < count; vertex += 1) {
      if (!settled[vertex] && (current === -1 || distances[vertex] < distances[current])) {
        current = vertex;
      }
    }
    if (current === -1 || distances[current] === Infinity) break;
    if (current === destination) break;

    settled[current] = true;
    for (const edge of adjacencyList[current]) {
      const candidate = distances[current] + edge.distance;
      if (candidate < distances[edge.to]) {
        distances[edge.to] = candidate;
        parent[edge.to] = current;
      }
    }
  }

  if (distances[destination] === Infinity) return null;
  const path = reconstructPath(source, destination, parent);
  return { path, distance: distances[destination], stops: Math.max(0, path.length - 1) };
}

function breadthFirstSearch(source, destination) {
  const visited = Array(locations.length).fill(false);
  const parent = Array(locations.length).fill(-1);
  const parentDistance = Array(locations.length).fill(0);
  const queue = [source];
  let head = 0;
  visited[source] = true;

  while (head < queue.length) {
    const current = queue[head++];
    if (current === destination) break;
    for (const edge of adjacencyList[current]) {
      if (!visited[edge.to]) {
        visited[edge.to] = true;
        parent[edge.to] = current;
        parentDistance[edge.to] = edge.distance;
        queue.push(edge.to);
      }
    }
  }

  if (!visited[destination]) return null;
  const path = reconstructPath(source, destination, parent);
  const distance = path.slice(1).reduce((sum, vertex) => sum + parentDistance[vertex], 0);
  return { path, distance, stops: Math.max(0, path.length - 1) };
}

function reconstructPath(source, destination, parent) {
  const reverse = [];
  let current = destination;
  while (current !== -1) {
    reverse.push(current);
    if (current === source) break;
    current = parent[current];
  }
  if (reverse[reverse.length - 1] !== source) return [];
  return reverse.reverse();
}

function resolveRoute() {
  const fromName = fromInput.value.trim();
  const toName = toInput.value.trim();
  const source = locationIndex.get(normalize(fromName));
  const destination = locationIndex.get(normalize(toName));
  if (source === undefined || destination === undefined) {
    const unknown = source === undefined ? fromName : toName;
    showError(`Location ${unknown || "(blank)"} not found.`);
    activeRoute = null;
    renderMap();
    return;
  }

  const result = activeMode === "distance"
    ? dijkstra(source, destination)
    : breadthFirstSearch(source, destination);
  if (!result) {
    showError(`No path found between ${locations[source]} and ${locations[destination]}.`);
    activeRoute = null;
    renderMap();
    return;
  }

  activeRoute = { ...result, source, destination };
  const resultPanel = document.querySelector("#route-result");
  resultPanel.classList.remove("has-error");
  document.querySelector("#result-label").textContent = activeMode === "distance"
    ? "SHORTEST DISTANCE"
    : "FEWEST STOPS";
  document.querySelector("#result-distance").innerHTML = `${result.distance} <small>m</small>`;
  document.querySelector("#result-stops").textContent = `${result.stops} ${result.stops === 1 ? "segment" : "segments"}`;
  document.querySelector("#route-message").textContent = "";
  const routeList = document.querySelector("#route-list");
  routeList.replaceChildren(...result.path.map(vertex => {
    const item = document.createElement("li");
    item.textContent = locations[vertex];
    return item;
  }));
  document.querySelector("#map-status").textContent = activeMode === "distance"
    ? "Showing shortest walking route"
    : "Showing route with fewest stops";
  renderMap();
}

function showError(message) {
  const resultPanel = document.querySelector("#route-result");
  resultPanel.classList.add("has-error");
  document.querySelector("#result-label").textContent = "ROUTE UNAVAILABLE";
  document.querySelector("#route-message").textContent = message;
  document.querySelector("#map-status").textContent = message;
}

function svgElement(tag, attributes = {}) {
  const element = document.createElementNS(svgNamespace, tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
  return element;
}

function edgeKey(from, to) {
  return [normalize(locations[from]), normalize(locations[to])].sort().join("|");
}

function getPosition(vertex) {
  const name = locations[vertex];
  if (defaultPositions[name]) return defaultPositions[name];
  const extraIndex = vertex - 4;
  const angle = (-Math.PI / 2) + (extraIndex * 2.4);
  return {
    x: Math.round(462 + Math.cos(angle) * 280),
    y: Math.round(305 + Math.sin(angle) * 198)
  };
}

function addDistancePill(layer, x, y, text, isOnRoute) {
  const group = svgElement("g", { class: `distance-pill${isOnRoute ? " route-pill" : ""}`, transform: `translate(${x} ${y})` });
  const width = Math.max(47, text.length * 6.2 + 16);
  group.append(svgElement("rect", { x: -width / 2, y: -10, width, height: 20, rx: 7 }));
  const label = svgElement("text", { x: 0, y: 1 });
  label.textContent = text;
  group.append(label);
  layer.append(group);
}

function drawEdge(layer, from, edge, routeKeys) {
  const to = edge.to;
  const key = edgeKey(from, to);
  const spec = defaultPaths[key];
  let pathData;
  let labelPosition;
  if (spec) {
    pathData = spec.d;
    labelPosition = spec.label;
  } else {
    const a = getPosition(from);
    const b = getPosition(to);
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const length = Math.max(1, Math.hypot(dx, dy));
    const bend = 24;
    const controlX = (a.x + b.x) / 2 - (dy / length) * bend;
    const controlY = (a.y + b.y) / 2 + (dx / length) * bend;
    pathData = `M ${a.x} ${a.y} Q ${controlX} ${controlY} ${b.x} ${b.y}`;
    labelPosition = { x: Math.round((a.x + 2 * controlX + b.x) / 4), y: Math.round((a.y + 2 * controlY + b.y) / 4) - 12 };
  }

  const isRoute = routeKeys.has(key);
  if (isRoute) layer.append(svgElement("path", { d: pathData, class: "path-route-glow" }));
  layer.append(svgElement("path", { d: pathData, class: "path-underlay" }));
  layer.append(svgElement("path", { d: pathData, class: isRoute ? "path-route" : "path-surface" }));
  addDistancePill(layer, labelPosition.x, labelPosition.y, `${edge.distance} m`, isRoute);
}

function facilityKind(name) {
  const kind = {
    Gate: "MAIN ENTRANCE",
    Admin: "ADMINISTRATION",
    Library: "LEARNING CENTRE",
    Canteen: "DINING HALL"
  };
  return kind[name] || "CAMPUS LOCATION";
}

function drawNode(layer, vertex) {
  const name = locations[vertex];
  const point = getPosition(vertex);
  const isStart = activeRoute && vertex === activeRoute.source;
  const isEnd = activeRoute && vertex === activeRoute.destination;
  const isOnRoute = activeRoute && activeRoute.path.includes(vertex);
  const className = ["node-group", isStart && "start-node", isEnd && "end-node", isOnRoute && "route-node"].filter(Boolean).join(" ");
  const group = svgElement("g", { class: className, transform: `translate(${point.x} ${point.y})` });
  const building = svgElement("g", { class: "node-building" });
  building.append(svgElement("rect", { class: "building-shadow", x: -40, y: -49, width: 80, height: 49, rx: 4 }));
  building.append(svgElement("rect", { class: "building-footprint", x: -39, y: -54, width: 78, height: 48, rx: 4 }));
  building.append(svgElement("rect", { class: "building-roof", x: -36, y: -60, width: 72, height: 44, rx: 3 }));
  building.append(svgElement("rect", { class: "building-roof-light", x: -36, y: -60, width: 72, height: 8, rx: 3 }));
  for (const windowX of [-27, -8, 11]) {
    building.append(svgElement("rect", { class: "building-window", x: windowX, y: -42, width: 10, height: 9, rx: 1 }));
    building.append(svgElement("rect", { class: "building-window", x: windowX, y: -25, width: 10, height: 7, rx: 1 }));
  }
  group.append(building);

  const labelWidth = Math.max(57, Math.min(128, name.length * 7 + 21));
  group.append(svgElement("rect", { class: "building-label-bg", x: -labelWidth / 2, y: 15, width: labelWidth, height: 21, rx: 7 }));
  const label = svgElement("text", { class: "building-label", x: 0, y: 26 });
  label.textContent = name;
  group.append(label);
  const caption = svgElement("text", { class: "building-caption", x: 0, y: 49 });
  caption.textContent = facilityKind(name);
  group.append(caption);
  group.append(svgElement("circle", { class: "node-shell", cx: 0, cy: 0, r: 12 }));
  group.append(svgElement("circle", { class: "node-dot", cx: 0, cy: 0, r: 7 }));
  layer.append(group);
}

function renderMap() {
  const pathLayer = document.querySelector("#path-layer");
  const nodeLayer = document.querySelector("#node-layer");
  pathLayer.replaceChildren();
  nodeLayer.replaceChildren();
  const routeKeys = new Set();
  if (activeRoute) {
    for (let index = 1; index < activeRoute.path.length; index += 1) {
      routeKeys.add(edgeKey(activeRoute.path[index - 1], activeRoute.path[index]));
    }
  }

  for (let from = 0; from < adjacencyList.length; from += 1) {
    for (const edge of adjacencyList[from]) {
      if (from < edge.to) drawEdge(pathLayer, from, edge, routeKeys);
    }
  }
  for (let vertex = 0; vertex < locations.length; vertex += 1) drawNode(nodeLayer, vertex);
  document.querySelector("#map-subtitle").innerHTML = `${locations.length} places <span>·</span> ${countPaths()} walking paths`;
  document.querySelector("#place-count").textContent = `${locations.length} ${locations.length === 1 ? "location" : "locations"}`;
  document.querySelector("#path-count").textContent = `${countPaths()} two-way ${countPaths() === 1 ? "path" : "paths"}`;
}

function countPaths() {
  return adjacencyList.reduce((sum, edges) => sum + edges.length, 0) / 2;
}

function refreshLocationOptions() {
  const options = document.querySelector("#location-options");
  options.replaceChildren(...locations.map(name => {
    const option = document.createElement("option");
    option.value = name;
    return option;
  }));
}

function setEditMessage(message, isError = false) {
  const element = document.querySelector("#edit-message");
  element.textContent = message;
  element.classList.toggle("error", isError);
}

function loadSampleMap() {
  for (const name of ["Gate", "Library", "Admin", "Canteen"]) addLocation(name);
  addPath("Gate", "Library", 100);
  addPath("Gate", "Admin", 50);
  addPath("Admin", "Library", 40);
  addPath("Library", "Canteen", 60);
  addPath("Admin", "Canteen", 200);
}

document.querySelectorAll(".mode-button").forEach(button => {
  button.addEventListener("click", () => {
    activeMode = button.dataset.mode;
    document.querySelectorAll(".mode-button").forEach(item => {
      const selected = item === button;
      item.classList.toggle("active", selected);
      item.setAttribute("aria-pressed", String(selected));
    });
    resolveRoute();
  });
});

routeForm.addEventListener("submit", event => {
  event.preventDefault();
  resolveRoute();
});

document.querySelector("#add-location-form").addEventListener("submit", event => {
  event.preventDefault();
  const input = document.querySelector("#new-location");
  const result = addLocation(input.value);
  if (!result.ok) {
    setEditMessage(result.message, true);
    return;
  }
  input.value = "";
  refreshLocationOptions();
  setEditMessage(`${locations[result.vertex]} added to the map.`);
  renderMap();
});

document.querySelector("#add-path-form").addEventListener("submit", event => {
  event.preventDefault();
  const from = document.querySelector("#path-from").value;
  const to = document.querySelector("#path-to").value;
  const distanceInput = document.querySelector("#path-distance");
  const distance = Number(distanceInput.value);
  const result = addPath(from, to, distance);
  if (!result.ok) {
    setEditMessage(result.message, true);
    return;
  }
  event.currentTarget.reset();
  setEditMessage(`Two-way path added: ${locations[locationIndex.get(normalize(from))]} ↔ ${locations[locationIndex.get(normalize(to))]} (${distance} m).`);
  resolveRoute();
});

document.querySelector("#zoom-in").addEventListener("click", () => {
  zoom = Math.min(1.35, Math.round((zoom + 0.1) * 10) / 10);
  mapSvg.style.transform = `scale(${zoom})`;
});
document.querySelector("#zoom-out").addEventListener("click", () => {
  zoom = Math.max(0.85, Math.round((zoom - 0.1) * 10) / 10);
  mapSvg.style.transform = `scale(${zoom})`;
});

loadSampleMap();
refreshLocationOptions();
resolveRoute();
