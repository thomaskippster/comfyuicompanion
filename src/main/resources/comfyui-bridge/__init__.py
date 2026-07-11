# ComfyUI Companion Bridge
import server
import folder_paths
import asyncio
from aiohttp import web

print("\033[95m[CMFC] UI Extension Bridge active\033[0m")


def _route_registered(method, path):
    """Return True if the given (method, path) is already exposed by PromptServer."""
    for route in server.PromptServer.instance.routes:
        if route.method == method and route.path == path:
            return True
    return False


if not _route_registered("POST", "/cmfc/refresh-models"):
    @server.PromptServer.instance.routes.post("/cmfc/refresh-models")
    async def refresh_models(request):
        data = await request.json() if request.has_body else {}
        force_reload = bool(data.get("force_reload", False))

        print(f"\033[92m[CMFC] Refresh signal received (force_reload={force_reload})\033[0m")

        # Give the client a moment to settle before invalidating caches.
        await asyncio.sleep(1)

        try:
            if hasattr(folder_paths, "invalidate_all_cached_folders"):
                folder_paths.invalidate_all_cached_folders()

            if hasattr(folder_paths, "filename_list_cache"):
                folder_paths.filename_list_cache.clear()

            if hasattr(folder_paths, "cache_helper"):
                folder_paths.cache_helper.clear()

            # Force ComfyUI to rebuild the node metadata.
            server.PromptServer.instance.object_info = None
        except Exception as e:
            print(f"[CMFC] Error: {e}")

        server.PromptServer.instance.send_sync("cmfc-refresh-ui", {
            "status": "ok",
            "force_reload": force_reload,
        })

        return web.json_response({"status": "ok"})
else:
    print("[CMFC] Route /cmfc/refresh-models already registered, skipping.")


if not _route_registered("POST", "/cmfc/load-workflow"):
    @server.PromptServer.instance.routes.post("/cmfc/load-workflow")
    async def load_workflow(request):
        data = await request.json() if request.has_body else {}
        workflow = data.get("workflow")
        if not workflow:
            return web.json_response(
                {"status": "error", "message": "No workflow data"},
                status=400,
            )

        server.PromptServer.instance.send_sync("cmfc-load-workflow", {
            "workflow": workflow,
        })
        return web.json_response({"status": "ok"})
else:
    print("[CMFC] Route /cmfc/load-workflow already registered, skipping.")


WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
