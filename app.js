"use strict";

// The Java server owns the graph and both route algorithms. This file renders its data
// and sends route and map-edit requests to the local Java API.
let locations = [];
let paths = [];
let locationIndex = new Map();
let activeMode = "distance";
let activeRoute = null;
let zoom = 1;
let routeRequestId = 0;

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

function normalize(name) {
  return String(name ?? "").trim().toLocaleLowerCase();
}

async function requestJson(endpoint, payload) {
  let response;
  try {
    response = await fetch(endpoint, payload === undefined ? { cache: "no-store" } : {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
      cache: "no-store"
    });
  } catch {
    throw new Error("Java server is not reachable. Start it with java CampusNavigationApp --server.");
  }

  let data;
  try {
    data = await response.json();
  } catch {
    throw new Error("Java server returned an unreadable response.");
  }
  if (!response.ok || data.ok === false) {
    throw new Error(data.message || `Java server returned HTTP ${response.status}.`);
  }
  return data;
}

function setServerStatus(connected) {
  const indicator = document.querySelector(".offline-indicator");
  indicator.classList.toggle("disconnected", !connected);
  document.querySelector("#server-status").textContent = connected
    ? "Java server connected"
    : "Java server disconnected";
}

async function refreshMap() {
  const map = await requestJson("/api/map");
  locations = map.locations;
  paths = map.paths;
  locationIndex = new Map(locations.map((name, index) => [normalize(name), index]));
  refreshLocationOptions();
  renderMap();
}

async function resolveRoute() {
  const requestId = ++routeRequestId;
  try {
    const result = await requestJson("/api/route", {
      from: fromInput.value.trim(),
      to: toInput.value.trim(),
      mode: activeMode
    });
    if (requestId !== routeRequestId) return;
    activeRoute = result;
    setServerStatus(true);
    const resultPanel = document.querySelector("#route-result");
    resultPanel.classList.remove("has-error");
    document.querySelector("#result-label").textContent = activeMode === "distance"
      ? "SHORTEST DISTANCE"
      : "FEWEST STOPS";
    document.querySelector("#result-distance").innerHTML = `${result.distance} <small>m</small>`;
    document.querySelector("#result-stops").textContent = `${result.stops} ${result.stops === 1 ? "segment" : "segments"}`;
    document.querySelector("#route-message").textContent = "";
    const routeList = document.querySelector("#route-list");
    routeList.replaceChildren(...result.path.map(name => {
      const item = document.createElement("li");
      item.textContent = name;
      return item;
    }));
    document.querySelector("#map-status").textContent = activeMode === "distance"
      ? "Showing shortest walking route"
      : "Showing route with fewest stops";
    renderMap();
  } catch (error) {
    if (requestId !== routeRequestId) return;
    if (error.message.startsWith("Java server is not reachable")) setServerStatus(false);
    showError(error.message);
    activeRoute = null;
    renderMap();
  }
}

function showError(message) {
  const resultPanel = document.querySelector("#route-result");
  resultPanel.classList.add("has-error");
  document.querySelector("#result-label").textContent = "ROUTE UNAVAILABLE";
  document.querySelector("#result-distance").textContent = "—";
  document.querySelector("#result-stops").textContent = "";
  document.querySelector("#route-list").replaceChildren();
  document.querySelector("#route-message").textContent = message;
  document.querySelector("#map-status").textContent = message;
}

function svgElement(tag, attributes = {}) {
  const element = document.createElementNS(svgNamespace, tag);
  for (const [name, value] of Object.entries(attributes)) element.setAttribute(name, String(value));
  return element;
}

function edgeKey(fromName, toName) {
  return [normalize(fromName), normalize(toName)].sort().join("|");
}

function getPosition(name) {
  if (defaultPositions[name]) return defaultPositions[name];
  const vertex = locationIndex.get(normalize(name)) ?? locations.length - 1;
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

function drawEdge(layer, edge, routeKeys) {
  const key = edgeKey(edge.from, edge.to);
  const spec = defaultPaths[key];
  let pathData;
  let labelPosition;
  if (spec) {
    pathData = spec.d;
    labelPosition = spec.label;
  } else {
    const a = getPosition(edge.from);
    const b = getPosition(edge.to);
    const dx = b.x - a.x;
    const dy = b.y - a.y;
    const length = Math.max(1, Math.hypot(dx, dy));
    const bend = 24;
    const controlX = (a.x + b.x) / 2 - (dy / length) * bend;
    const controlY = (a.y + b.y) / 2 + (dx / length) * bend;
    pathData = `M ${a.x} ${a.y} Q ${controlX} ${controlY} ${b.x} ${b.y}`;
    labelPosition = {
      x: Math.round((a.x + 2 * controlX + b.x) / 4),
      y: Math.round((a.y + 2 * controlY + b.y) / 4) - 12
    };
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

function drawNode(layer, name) {
  const point = getPosition(name);
  const isStart = activeRoute && activeRoute.path[0] === name;
  const isEnd = activeRoute && activeRoute.path.at(-1) === name;
  const isOnRoute = activeRoute && activeRoute.path.includes(name);
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

  for (const edge of paths) drawEdge(pathLayer, edge, routeKeys);
  for (const name of locations) drawNode(nodeLayer, name);
  document.querySelector("#map-subtitle").innerHTML = `${locations.length} places <span>·</span> ${paths.length} walking paths`;
  document.querySelector("#place-count").textContent = `${locations.length} ${locations.length === 1 ? "location" : "locations"}`;
  document.querySelector("#path-count").textContent = `${paths.length} two-way ${paths.length === 1 ? "path" : "paths"}`;
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

document.querySelector("#add-location-form").addEventListener("submit", async event => {
  event.preventDefault();
  const input = document.querySelector("#new-location");
  try {
    const result = await requestJson("/api/locations", { name: input.value.trim() });
    input.value = "";
    await refreshMap();
    setServerStatus(true);
    setEditMessage(`${result.name} added to the map.`);
  } catch (error) {
    setEditMessage(error.message, true);
  }
});

document.querySelector("#add-path-form").addEventListener("submit", async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const from = document.querySelector("#path-from").value.trim();
  const to = document.querySelector("#path-to").value.trim();
  const distance = Number(document.querySelector("#path-distance").value);
  try {
    const result = await requestJson("/api/paths", { from, to, distance });
    form.reset();
    await refreshMap();
    await resolveRoute();
    setEditMessage(result.message);
  } catch (error) {
    setEditMessage(error.message, true);
  }
});

document.querySelector("#zoom-in").addEventListener("click", () => {
  zoom = Math.min(1.35, Math.round((zoom + 0.1) * 10) / 10);
  mapSvg.style.transform = `scale(${zoom})`;
});
document.querySelector("#zoom-out").addEventListener("click", () => {
  zoom = Math.max(0.85, Math.round((zoom - 0.1) * 10) / 10);
  mapSvg.style.transform = `scale(${zoom})`;
});

async function startApp() {
  try {
    await refreshMap();
    setServerStatus(true);
    await resolveRoute();
  } catch (error) {
    setServerStatus(false);
    showError(error.message);
  }
}

startApp();
