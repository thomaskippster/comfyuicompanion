# Companion for ComfyUI Bridge
# GitHub Repository: https://github.com/thomaskippster/comfyuicompanion
# Note: There is also a direct Pinokio install and a pre-compiled JAR available – you can find those links on the GitHub repo.

# --- ComfyUI Legacy Custom Nodes Compatibility Layer (ComfyUI 0.42+ sampler_helpers migration) ---
try:
    import comfy.sample
    import comfy.sampler_helpers as _cmfc_sampler_helpers
    for _cmfc_attr in ['prepare_mask', 'get_additional_models', 'convert_cond', 'cleanup_additional_models']:
        if not hasattr(comfy.sample, _cmfc_attr) and hasattr(_cmfc_sampler_helpers, _cmfc_attr):
            setattr(comfy.sample, _cmfc_attr, getattr(_cmfc_sampler_helpers, _cmfc_attr))
except Exception as _cmfc_ex:
    pass
# -------------------------------------------------------------------------------------------------

# --- Silence benign Windows WinError 10054 ConnectionResetError in asyncio Proactor loop ---
try:
    import sys
    if sys.platform == "win32":
        from asyncio.proactor_events import _ProactorBasePipeTransport
        _orig_call_connection_lost = _ProactorBasePipeTransport._call_connection_lost

        def _silent_connection_lost(self, exc=None):
            if isinstance(exc, ConnectionResetError) and getattr(exc, "winerror", None) == 10054:
                exc = None
            try:
                _orig_call_connection_lost(self, exc)
            except ConnectionResetError:
                pass

        _ProactorBasePipeTransport._call_connection_lost = _silent_connection_lost
except Exception:
    pass
# -------------------------------------------------------------------------------------------

import os
import json
import server
import folder_paths
import asyncio
from aiohttp import web

print("\033[95m[CMFC] UI Extension Bridge active\033[0m")
print("\033[96m[CMFC] ComfyUI Companion App: https://github.com/thomaskippster/comfyuicompanion\033[0m")
print("\033[96m[CMFC] Note: Direct Pinokio install & pre-compiled JAR available on GitHub repo.\033[0m")


def _route_registered(method, path):
    """Return True if the given (method, path) is already exposed by PromptServer."""
    instance = getattr(server.PromptServer, "instance", None)
    if instance is None or not hasattr(instance, "routes"):
        return False
    for route in instance.routes:
        if route.method == method and route.path == path:
            return True
    return False


def _load_manager_model_registry():
    """Loads ComfyUI-Manager's model-list.json if available in site-packages or custom_nodes."""
    candidates = []
    try:
        import comfyui_manager
        pkg_dir = os.path.dirname(os.path.abspath(comfyui_manager.__file__))
        candidates.append(os.path.join(pkg_dir, "model-list.json"))
    except Exception:
        pass
    try:
        base_path = getattr(folder_paths, "base_path", "")
        if base_path:
            candidates.append(os.path.join(base_path, "custom_nodes", "ComfyUI-Manager", "model-list.json"))
    except Exception:
        pass
    for path in candidates:
        if os.path.isfile(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                    return data.get("models", [])
            except Exception:
                pass
    return []


prompt_instance = getattr(server.PromptServer, "instance", None)

if prompt_instance is not None and not _route_registered("POST", "/cmfc/refresh-models"):
    @prompt_instance.routes.post("/cmfc/refresh-models")
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
            prompt_instance.object_info = None
        except Exception as e:
            print(f"[CMFC] Error: {e}")

        prompt_instance.send_sync("cmfc-refresh-ui", {
            "status": "ok",
            "force_reload": force_reload,
        })

        return web.json_response({"status": "ok"})
elif prompt_instance is not None:
    print("[CMFC] Route /cmfc/refresh-models already registered, skipping.")


if prompt_instance is not None and not _route_registered("POST", "/cmfc/load-workflow"):
    @prompt_instance.routes.post("/cmfc/load-workflow")
    async def load_workflow(request):
        data = await request.json() if request.has_body else {}
        workflow = data.get("workflow")
        if not workflow:
            return web.json_response(
                {"status": "error", "message": "No workflow data"},
                status=400,
            )

        prompt_instance.send_sync("cmfc-load-workflow", {
            "workflow": workflow,
        })
        return web.json_response({"status": "ok"})
elif prompt_instance is not None:
    print("[CMFC] Route /cmfc/load-workflow already registered, skipping.")


if prompt_instance is not None and not _route_registered("POST", "/cmfc/convert-workflow"):
    @prompt_instance.routes.post("/cmfc/convert-workflow")
    async def convert_workflow(request):
        """
        Accepts a GUI-format workflow JSON from the Java companion app and relays
        it to the browser via WebSocket. The JS bridge will call app.graphToPrompt()
        and POST the resulting API JSON back to http://127.0.0.1:12345/api/workflow-ready.
        """
        try:
            data = await request.json()
        except Exception:
            return web.json_response(
                {"status": "error", "message": "Invalid JSON body"},
                status=400,
            )

        workflow = data.get("workflow")
        callback_url = data.get("callbackUrl", "http://127.0.0.1:12345/api/workflow-ready")

        if not workflow:
            return web.json_response(
                {"status": "error", "message": "No 'workflow' field in request"},
                status=400,
            )

        print("[CMFC] Relaying GUI workflow to browser for app.graphToPrompt() conversion")
        prompt_instance.send_sync("cmfc-convert-workflow", {
            "workflow": workflow,
            "callbackUrl": callback_url,
        })

        return web.json_response({"status": "ok", "message": "Conversion request sent to browser"})
elif prompt_instance is not None:
    print("[CMFC] Route /cmfc/convert-workflow already registered, skipping.")


if prompt_instance is not None and not _route_registered("POST", "/cmfc/inspect-workflow"):
    @prompt_instance.routes.post("/cmfc/inspect-workflow")
    async def inspect_workflow(request):
        """
        Generic workflow inspection service querying ComfyUI's internal runtime registry (nodes.NODE_CLASS_MAPPINGS).
        Identifies missing custom node types directly from the running ComfyUI instance without hardcoded lists.
        """
        try:
            data = await request.json()
        except Exception:
            return web.json_response({"status": "error", "message": "Invalid JSON body"}, status=400)

        workflow = data.get("workflow", {})
        nodes_list = workflow.get("nodes", [])

        try:
            import nodes as comfy_nodes
            registered = set(comfy_nodes.NODE_CLASS_MAPPINGS.keys())
        except Exception:
            registered = set()

        frontend_builtins = {
            "note", "markdownnote", "reroute", "primitivenode",
            "primitiveint", "primitivefloat", "primitivestring",
            "primitivestringmultiline", "primitiveboolean"
        }

        missing_nodes = []
        for n in nodes_list:
            if isinstance(n, dict):
                ntype = n.get("type", "")
                if ntype and ntype.lower() not in frontend_builtins and ntype not in registered:
                    missing_nodes.append({
                        "id": n.get("id"),
                        "type": ntype,
                        "title": n.get("title", ntype)
                    })

        return web.json_response({
            "status": "ok",
            "installed_count": len(registered),
            "missing_count": len(missing_nodes),
            "missing_nodes": missing_nodes,
        })
elif prompt_instance is not None:
    print("[CMFC] Route /cmfc/inspect-workflow already registered, skipping.")


if prompt_instance is not None and not _route_registered("POST", "/cmfc/resolve-models"):
    @prompt_instance.routes.post("/cmfc/resolve-models")
    async def resolve_models(request):
        """
        Generic model resolution service querying ComfyUI's internal folder_paths and ComfyUI-Manager's
        model-list.json directly inside Python to resolve installed paths, target folders, and download URLs.
        """
        try:
            data = await request.json() if request.has_body else {}
        except Exception:
            return web.json_response({"status": "error", "message": "Invalid JSON body"}, status=400)

        requested_models = data.get("models", [])
        folder_map = getattr(folder_paths, "folder_names_and_paths", {})
        manager_models = _load_manager_model_registry()
        manager_by_filename = {
            str(m.get("filename", "")).lower(): m for m in manager_models if isinstance(m, dict) and m.get("filename")
        }

        resolved = []
        for item in requested_models:
            fname = item.get("filename", "") if isinstance(item, dict) else str(item)
            base_name = os.path.basename(fname.replace("\\", "/"))
            base_lower = base_name.lower()

            found_folder = None
            found_rel_path = None
            for folder_key in folder_map.keys():
                try:
                    files = folder_paths.get_filename_list(folder_key)
                    for rel_file in files:
                        rel_norm = rel_file.replace("\\", "/")
                        if rel_norm.lower() == fname.replace("\\", "/").lower() or os.path.basename(rel_norm).lower() == base_lower:
                            found_folder = folder_key
                            found_rel_path = rel_file
                            break
                    if found_rel_path:
                        break
                except Exception:
                    continue

            mgr_entry = manager_by_filename.get(base_lower, {})
            resolved.append({
                "filename": base_name,
                "installed": found_rel_path is not None,
                "folder": found_folder or mgr_entry.get("save_path") or mgr_entry.get("type"),
                "relative_path": found_rel_path,
                "url": mgr_entry.get("url"),
                "size": mgr_entry.get("size"),
            })

        return web.json_response({
            "status": "ok",
            "models": resolved,
        })
elif prompt_instance is not None:
    print("[CMFC] Route /cmfc/resolve-models already registered, skipping.")


WEB_DIRECTORY = "web"
NODE_CLASS_MAPPINGS = {}
__all__ = ["WEB_DIRECTORY", "NODE_CLASS_MAPPINGS"]
