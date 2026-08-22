// Companion for ComfyUI - Bridge Extension
import { app } from "../../scripts/app.js";
import { api } from "../../scripts/api.js";

const BRIDGE_URL = "http://127.0.0.1:12345/import";

const log = (msg, level = "log") => {
    const tag = "[CMFC]";
    if (level === "error") {
        console.error(tag, msg);
    } else if (level === "warn") {
        console.warn(tag, msg);
    } else {
        console.log(tag, msg);
    }
};

async function loadApiToken() {
    try {
        const baseUrl = new URL(".", import.meta.url).href;
        const response = await fetch(`${baseUrl}config.json?v=${Date.now()}`);
        if (!response.ok) {
            log(`config.json responded ${response.status}; bridge will not send auth header.`, "warn");
            return null;
        }
        const config = await response.json();
        return typeof config.token === "string" && config.token.length > 0 ? config.token : null;
    } catch (e) {
        log(`Could not load config.json: ${e.message}`, "warn");
        return null;
    }
}

const initializeExtension = async () => {
    const apiToken = await loadApiToken();

    api.addEventListener("cmfc-refresh-ui", async (event) => {
        const data = event.detail || {};
        const forceReload = data.force_reload === true;

        log(`Refresh signal received (force_reload=${forceReload})`);

        if (forceReload) {
            window.location.reload();
            return;
        }

        try {
            api.nodeDefs = null;
            if (app.refresh) {
                await app.refresh();
            }

            const nodeDefs = await api.getNodeDefs();
            if (app.graph && app.graph._nodes) {
                for (const node of app.graph._nodes) {
                    const def = nodeDefs[node.type];
                    if (!def || !node.widgets) continue;
                    for (const widget of node.widgets) {
                        if (widget.type === "combo") {
                            const inputDef = def.input?.required?.[widget.name] || def.input?.optional?.[widget.name];
                            if (inputDef && Array.isArray(inputDef[0])) {
                                widget.options.values = inputDef[0];
                            }
                        }
                    }
                    node.setDirtyCanvas(true, true);
                }
            }
            log("UI sync complete.");
        } catch (e) {
            log(`UI refresh failed: ${e.message}`, "error");
        }
    });

    api.addEventListener("cmfc-load-workflow", (event) => {
        const data = event.detail || {};
        if (!data.workflow) {
            log("load-workflow event arrived without a workflow payload.", "warn");
            return;
        }
        log("Load workflow signal received");
        try {
            app.loadGraphData(data.workflow);
        } catch (e) {
            log(`Failed to load workflow: ${e.message}`, "error");
        }
    });

    /**
     * Triggered by /cmfc/convert-workflow (Python).
     * Loads the GUI workflow, converts it to API format via ComfyUI's own
     * app.graphToPrompt(), and POSTs the result to the Java callback endpoint.
     * The previous canvas is restored afterwards so the user doesn't lose their work.
     */
    /**
     * Triggered by /cmfc/convert-workflow (Python).
     * Loads the GUI workflow, converts it to API format via ComfyUI's own
     * app.graphToPrompt(), and POSTs the result to the Java callback endpoint.
     * The previous canvas is restored afterwards so the user doesn't lose their work.
     */
    api.addEventListener("cmfc-convert-workflow", async (event) => {
        const data = event.detail || {};
        const callbackUrl = data.callbackUrl || "http://127.0.0.1:12345/api/workflow-ready";

        if (!data.workflow) {
            log("convert-workflow event arrived without a workflow payload.", "warn");
            try {
                await fetch(callbackUrl, {
                    method: "POST",
                    mode: "cors",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ error: "No workflow payload received" }),
                });
            } catch (_) {}
            return;
        }

        log("Starting GUI→API conversion via app.graphToPrompt()");

        // Save the current canvas so we can restore it after conversion
        let previousGraph = null;
        try {
            previousGraph = app.graph ? app.graph.serialize() : null;
        } catch (_) {}

        try {
            // Load the blueprint GUI JSON into the canvas
            app.loadGraphData(data.workflow);

            // Give the graph a tick to settle before converting
            await new Promise(resolve => setTimeout(resolve, 150));

            // Use ComfyUI's own conversion — the same path as Queue Prompt
            const apiPromptResult = await app.graphToPrompt();

            // Restore the previous canvas
            if (previousGraph) {
                try { app.loadGraphData(previousGraph); } catch (_) {}
            }

            // Extract the actual prompt JSON object
            let payloadToSend = apiPromptResult;
            if (apiPromptResult && typeof apiPromptResult === "object") {
                if (apiPromptResult.output) {
                    payloadToSend = apiPromptResult.output;
                } else if (apiPromptResult.prompt) {
                    payloadToSend = apiPromptResult.prompt;
                }
            }

            // POST the API JSON back to the Java companion
            const response = await fetch(callbackUrl, {
                method: "POST",
                mode: "cors",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payloadToSend),
            });

            if (!response.ok) {
                log(`Callback to ${callbackUrl} failed: HTTP ${response.status}`, "warn");
            } else {
                log("Conversion complete — API prompt sent to companion.");
            }
        } catch (e) {
            log(`Conversion failed: ${e.message}`, "error");
            // Restore canvas even on error
            if (previousGraph) {
                try { app.loadGraphData(previousGraph); } catch (_) {}
            }
            try {
                await fetch(callbackUrl, {
                    method: "POST",
                    mode: "cors",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ error: e.message || "Workflow conversion failed in browser" }),
                });
            } catch (fetchErr) {
                log(`Failed to send conversion error to callback: ${fetchErr.message}`, "error");
            }
        }
    });

    api.addEventListener("reconnected", () => {
        log("WebSocket reconnected; reloading ComfyUI to refresh state.");
        window.location.reload();
    });

    const addUI = () => {
        if (document.getElementById("tki-companion-fab")) return;
        const fab = document.createElement("div");
        fab.id = "tki-companion-fab";
        fab.innerHTML = "&#128640;";
        fab.title = "Send workflow to Companion for ComfyUI";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onmouseover = () => { fab.style.transform = "scale(1.1)"; };
        fab.onmouseout = () => { fab.style.transform = "scale(1.0)"; };
        fab.onclick = async () => {
            if (!app?.graph) {
                log("Cannot send workflow: ComfyUI app/graph is not ready yet.", "warn");
                return;
            }
            const headers = { "Content-Type": "application/json" };
            if (apiToken) {
                headers["Authorization"] = `Bearer ${apiToken}`;
            }
            try {
                const response = await fetch(BRIDGE_URL, {
                    method: "POST",
                    mode: "cors",
                    headers,
                    body: JSON.stringify(app.graph.serialize()),
                });
                if (!response.ok) {
                    log(`Companion rejected workflow (HTTP ${response.status}).`, "warn");
                }
            } catch (e) {
                log(`Failed to reach Companion at ${BRIDGE_URL}: ${e.message}`, "error");
            }
        };
        document.body.appendChild(fab);
    };
    addUI();
    log("Bridge extension ready.");
};

app.registerExtension({
    name: "TKI.ComfyUICompanion",
    async setup() {
        await initializeExtension();
    }
});
