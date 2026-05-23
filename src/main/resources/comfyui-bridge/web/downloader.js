// ComfyUI Companion - Bridge Extension
import { app } from "../../scripts/app.js";
import { api } from "../../scripts/api.js";

const initializeExtension = async () => {
    let apiToken = null;
    const loadConfig = async () => {
        try {
            const baseUrl = new URL(".", import.meta.url).href;
            const configUrl = `${baseUrl}config.json?v=${Date.now()}`;
            const response = await fetch(configUrl);
            if (response.ok) { const config = await response.json(); apiToken = config.token; }
        } catch (e) {}
    };
    await loadConfig();

    // Event Listener for UI Refresh
    api.addEventListener("cmfc-refresh-ui", async (event) => {
        const data = event.detail || {};
        const forceReload = data.force_reload === true;

        console.log(`%c🔥 [CMFC] Refresh Signal Received. Force Reload: ${forceReload}`, "background: #ff0000; color: #fff; font-size: 14px;");
        
        if (forceReload) {
            window.location.reload();
            return;
        }

        try {
            // 1. Clear Frontend Caches
            api.nodeDefs = null;

            // 2. Trigger standard ComfyUI refresh
            if (app.refresh) {
                await app.refresh(); 
            }

            // 3. Update existing combo widgets
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
            console.log("[CMFC] UI Sync Complete.");
        } catch (e) {
            console.error("[CMFC] UI Refresh Failed:", e);
        }
    });

    // Event Listener for WebSocket Reconnection (e.g., after server restart)
    api.addEventListener("reconnected", () => {
        console.log("%c🔄 [CMFC] WebSocket reconnected. Reloading ComfyUI to refresh state...", "background: #0077ff; color: #fff; font-size: 14px;");
        window.location.reload();
    });

    const addUI = () => {
        if (document.getElementById("tki-companion-fab")) return;
        const fab = document.createElement("div");
        fab.id = "tki-companion-fab";
        fab.innerHTML = "🚀";
        fab.style = "position:fixed; bottom:30px; right:30px; z-index:10000; cursor:pointer; font-size:30px; background:#ffcc00; border-radius:50%; width:60px; height:60px; display:flex; align-items:center; justify-content:center; box-shadow:0 0 20px rgba(0,0,0,0.5); border: 2px solid white; transition: transform 0.2s;";
        fab.onclick = async () => {
            if (!app?.graph) return;
            try {
                await fetch("http://127.0.0.1:12345/import", {
                    method: "POST", mode: "cors",
                    headers: { "Content-Type": "application/json", "Authorization": `Bearer ${apiToken}` },
                    body: JSON.stringify(app.graph.serialize())
                });
            } catch (e) {}
        };
        document.body.appendChild(fab);
    };
    addUI();
};

app.registerExtension({
    name: "TKI.ComfyUICompanion",
    async setup() {
        await initializeExtension();
    }
});
