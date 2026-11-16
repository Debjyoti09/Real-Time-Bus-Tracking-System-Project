"use strict";

/**
 * MapLibre GL JS Local Bundle / WebGL Helper (Audit Point 5.2).
 * - Ensures maplibregl.supported() is always available (including MapLibre GL v4+ where
 *   supported() is checked via WebGLRenderingContext).
 * - If the browser is completely offline and the MapLibre GL JS CDN script could not be loaded,
 *   provides a self-contained DOM/SVG maplibregl implementation supporting [lng, lat] coordinates,
 *   GeoJSON LineString sources/layers (setData), and rotating Markers without any external dependency.
 */
(function () {
    function checkWebGLSupported() {
        try {
            if (typeof window === "undefined" || !window.WebGLRenderingContext) {
                return false;
            }
            const canvas = document.createElement("canvas");
            return Boolean(
                canvas.getContext("webgl2") ||
                canvas.getContext("webgl") ||
                canvas.getContext("experimental-webgl")
            );
        } catch (_) {
            return false;
        }
    }

    if (typeof window.maplibregl !== "undefined" && typeof window.maplibregl.Map === "function") {
        if (typeof window.maplibregl.supported !== "function") {
            window.maplibregl.supported = checkWebGLSupported;
        }
        return;
    }

    class LngLatBounds {
        constructor(sw, ne) {
            this.points = [];
            if (Array.isArray(sw)) this.extend(sw);
            if (Array.isArray(ne)) this.extend(ne);
        }
        extend(coord) {
            if (!coord) return this;
            if (Array.isArray(coord) && coord.length >= 2 && Number.isFinite(coord[0]) && Number.isFinite(coord[1])) {
                this.points.push([Number(coord[0]), Number(coord[1])]);
            } else if (coord instanceof LngLatBounds) {
                coord.points.forEach(p => this.points.push(p));
            }
            return this;
        }
        isEmpty() {
            return this.points.length === 0;
        }
    }

    class Popup {
        constructor(options = {}) {
            this.options = options;
            this.html = "";
        }
        setHTML(html) {
            this.html = html;
            return this;
        }
        setText(text) {
            this.html = String(text ?? "");
            return this;
        }
    }

    class Marker {
        constructor(options = {}) {
            this.element = options.element || document.createElement("div");
            this.rotation = Number(options.rotation) || 0;
            this.lngLat = [0, 0];
            this.popup = null;
            this._map = null;
        }
        setLngLat(lngLat) {
            if (Array.isArray(lngLat) && lngLat.length >= 2) {
                this.lngLat = [Number(lngLat[0]), Number(lngLat[1])];
                if (this._map) this._map._renderViewport();
            }
            return this;
        }
        getLngLat() {
            return { lng: this.lngLat[0], lat: this.lngLat[1] };
        }
        setRotation(deg) {
            this.rotation = Number(deg) || 0;
            const ring = this.element.querySelector(".bus-direction-ring");
            if (ring) {
                ring.style.transform = `rotate(${Math.round(this.rotation)}deg)`;
            } else {
                this.element.style.transform = `translate(-50%, -50%) rotate(${Math.round(this.rotation)}deg)`;
            }
            return this;
        }
        setPopup(popup) {
            this.popup = popup;
            if (popup && popup.html) {
                this.element.title = popup.html.replace(/<[^>]+>/g, " ").trim();
            }
            return this;
        }
        addTo(map) {
            this._map = map;
            if (map && map._markers) {
                if (!map._markers.includes(this)) {
                    map._markers.push(this);
                }
                if (map._markerLayer && this.element.parentNode !== map._markerLayer) {
                    this.element.style.position = "absolute";
                    this.element.style.transform = "translate(-50%, -50%)";
                    map._markerLayer.appendChild(this.element);
                }
                map._renderViewport();
            }
            return this;
        }
        remove() {
            if (this.element && this.element.parentNode) {
                this.element.parentNode.removeChild(this.element);
            }
            if (this._map && this._map._markers) {
                this._map._markers = this._map._markers.filter(m => m !== this);
            }
            this._map = null;
        }
    }

    class NavigationControl {}
    class AttributionControl {
        constructor(options = {}) {
            this.options = options;
        }
    }

    class MapLibreMap {
        constructor(options = {}) {
            this.container = typeof options.container === "string"
                ? document.getElementById(options.container)
                : options.container;
            this.styleUrl = options.style || "https://tiles.openfreemap.org/styles/liberty";
            this.center = Array.isArray(options.center) ? options.center : [86.6948, 24.4851];
            this.zoom = options.zoom ?? 12;
            this._sources = {};
            this._layers = {};
            this._listeners = {};
            this._markers = [];
            this._bbox = null;

            if (this.container) {
                this.container.style.position = "relative";
                this.container.style.overflow = "hidden";
                this.container.style.background = "#e8f1f5";
                this.container.innerHTML = "";

                const svgNS = "http://www.w3.org/2000/svg";
                this._svg = document.createElementNS(svgNS, "svg");
                this._svg.setAttribute("width", "100%");
                this._svg.setAttribute("height", "100%");
                this._svg.style.position = "absolute";
                this._svg.style.inset = "0";
                this.container.appendChild(this._svg);

                this._markerLayer = document.createElement("div");
                this._markerLayer.style.position = "absolute";
                this._markerLayer.style.inset = "0";
                this._markerLayer.style.pointerEvents = "none";
                this.container.appendChild(this._markerLayer);
            }

            setTimeout(() => this._emit("load"), 0);
        }
        addControl() {
            return this;
        }
        on(event, handler) {
            if (!this._listeners[event]) this._listeners[event] = [];
            this._listeners[event].push(handler);
            if (event === "load" && this._loaded) {
                setTimeout(() => handler(), 0);
            }
            return this;
        }
        _emit(event, payload) {
            if (event === "load") this._loaded = true;
            (this._listeners[event] || []).forEach(fn => fn(payload));
        }
        resize() {
            this._renderViewport();
        }
        addSource(id, sourceDef) {
            const self = this;
            this._sources[id] = {
                data: sourceDef.data,
                setData(newData) {
                    this.data = newData;
                    self._renderViewport();
                }
            };
            this._renderViewport();
        }
        getSource(id) {
            return this._sources[id] || null;
        }
        addLayer(layerDef) {
            this._layers[layerDef.id] = layerDef;
            this._renderViewport();
        }
        getLayer(id) {
            return this._layers[id] || null;
        }
        fitBounds(bounds) {
            if (bounds instanceof LngLatBounds && !bounds.isEmpty()) {
                let minLng = Infinity, maxLng = -Infinity, minLat = Infinity, maxLat = -Infinity;
                bounds.points.forEach(([lng, lat]) => {
                    if (lng < minLng) minLng = lng;
                    if (lng > maxLng) maxLng = lng;
                    if (lat < minLat) minLat = lat;
                    if (lat > maxLat) maxLat = lat;
                });
                const padLng = Math.max(0.005, (maxLng - minLng) * 0.18);
                const padLat = Math.max(0.005, (maxLat - minLat) * 0.18);
                this._bbox = { minLng: minLng - padLng, maxLng: maxLng + padLng, minLat: minLat - padLat, maxLat: maxLat + padLat };
                this._renderViewport();
            }
        }
        flyTo(options = {}) {
            if (Array.isArray(options.center)) {
                this.center = options.center;
                this._renderViewport();
            }
        }
        easeTo(options = {}) {
            if (Array.isArray(options.center)) {
                this.center = options.center;
                this._renderViewport();
            }
        }
        _project(lng, lat, width, height) {
            const b = this._bbox || {
                minLng: this.center[0] - 0.05,
                maxLng: this.center[0] + 0.05,
                minLat: this.center[1] - 0.05,
                maxLat: this.center[1] + 0.05
            };
            const x = ((lng - b.minLng) / Math.max(1e-6, b.maxLng - b.minLng)) * width;
            const y = (1 - (lat - b.minLat) / Math.max(1e-6, b.maxLat - b.minLat)) * height;
            return [x, y];
        }
        _renderViewport() {
            if (!this.container || !this._svg) return;
            const width = this.container.clientWidth || 600;
            const height = this.container.clientHeight || 360;
            this._svg.innerHTML = "";
            const svgNS = "http://www.w3.org/2000/svg";

            Object.values(this._layers).forEach(layerDef => {
                const src = this._sources[layerDef.source];
                const coords = src?.data?.geometry?.coordinates || [];
                if (coords.length < 2) return;
                const poly = document.createElementNS(svgNS, "polyline");
                const pts = coords.map(([lng, lat]) => this._project(lng, lat, width, height).join(",")).join(" ");
                poly.setAttribute("points", pts);
                poly.setAttribute("fill", "none");
                poly.setAttribute("stroke", layerDef.paint?.["line-color"] || "#2563eb");
                poly.setAttribute("stroke-width", String(layerDef.paint?.["line-width"] || 5));
                poly.setAttribute("stroke-linecap", "round");
                poly.setAttribute("stroke-linejoin", "round");
                this._svg.appendChild(poly);
            });

            this._markers.forEach(m => {
                const [x, y] = this._project(m.lngLat[0], m.lngLat[1], width, height);
                m.element.style.left = `${x}px`;
                m.element.style.top = `${y}px`;
            });
        }
    }

    window.maplibregl = {
        Map: MapLibreMap,
        Marker,
        Popup,
        LngLatBounds,
        NavigationControl,
        AttributionControl,
        supported: checkWebGLSupported
    };
})();
