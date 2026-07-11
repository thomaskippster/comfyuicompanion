import sys
sys.stdout.reconfigure(encoding='utf-8')

path = r'C:/Dev/workspace/comfyuicompanion/src/main/java/de/tki/comfymodels/service/impl/Video4jEditorService.java'
with open(path, 'rb') as fh:
    raw = fh.read()
data = raw.decode('utf-8')
nl = '\r\n' if b'\r\n' in raw else '\n'

# 1. Add a robust resolver method `resolveSceneVideo` and use it in applyTrimming.
# We will insert a helper just before `applyTrimming` and use it from there.

helper = (
    '    /**' + nl
    + '     * Try to find a usable on-disk video for a scene. Looks at the scene' + nl
    + '     * configured videoPath first, then at the workspace root,' + nl
    + '     * the configured ComfyUI output dir, and its "video" subfolder.' + nl
    + '     * Returns the first file found, or null.' + nl
    + '     */' + nl
    + '    private File resolveSceneVideo(Scene scene) {' + nl
    + '        java.util.List<String> candidates = new java.util.ArrayList<>();' + nl
    + '        // 1. The scene configured videoPath (may be stale).' + nl
    + '        if (scene.getVideoPath() != null && !scene.getVideoPath().isEmpty()) {' + nl
    + '            candidates.add(scene.getVideoPath());' + nl
    + '        }' + nl
    + '        String sceneId = scene.getSceneId();' + nl
    + '        // 2. Common well-known locations.' + nl
    + '        String[] workspaceRoots = {' + nl
    + '                System.getProperty("user.dir"),' + nl
    + '                new java.io.File("").getAbsolutePath(),' + nl
    + '                configService.getResolvedOutputDir()' + nl
    + '        };' + nl
    + '        String[] filenamePatterns = {' + nl
    + '                "scene_" + sceneId + "_final.mp4",' + nl
    + '                "scene_" + sceneId + "_simulated.mp4",' + nl
    + '                "videoarchitect_" + sceneId + "_00001_.mp4",' + nl
    + '                "videoarchitect_" + sceneId + ".mp4",' + nl
    + '                sceneId + ".mp4"' + nl
    + '        };' + nl
    + '        for (String root : workspaceRoots) {' + nl
    + '            if (root == null || root.isEmpty()) continue;' + nl
    + '            // direct + video/ subfolder' + nl
    + '            for (String name : filenamePatterns) {' + nl
    + '                candidates.add(new java.io.File(root, name).getAbsolutePath());' + nl
    + '                candidates.add(new java.io.File(new java.io.File(root, "video"), name).getAbsolutePath());' + nl
    + '                candidates.add(new java.io.File(new java.io.File(root, "output"), name).getAbsolutePath());' + nl
    + '            }' + nl
    + '        }' + nl
    + '        for (String pathStr : candidates) {' + nl
    + '            if (pathStr == null || pathStr.isEmpty()) continue;' + nl
    + '            java.io.File f = new java.io.File(pathStr);' + nl
    + '            if (f.exists() && f.length() >= 1024) {' + nl
    + '                System.out.println("   -> Resolved scene " + sceneId + " video to: " + f.getAbsolutePath());' + nl
    + '                return f;' + nl
    + '            }' + nl
    + '        }' + nl
    + '        return null;' + nl
    + '    }' + nl
    + '' + nl
)

# Insert just before applyTrimming.
anchor = '    public File applyTrimming(Scene scene) throws Exception {'
if anchor not in data:
    print('applyTrimming anchor not found', file=sys.stderr)
    sys.exit(1)
data = data.replace(anchor, helper + anchor, 1)
print('Inserted resolveSceneVideo helper')

# 2. Patch applyTrimming to use resolveSceneVideo when scene.getVideoPath() points
# to a missing or too-small file. The current code throws FileNotFoundException;
# we replace that with an attempt to resolve and only then throw.
old = (
    '    public File applyTrimming(Scene scene) throws Exception {' + nl
    + '        validateFfmpeg();' + nl
    + '        String videoPath = scene.getVideoPath();' + nl
    + '        System.out.println("ðŸ” [Video4jEditorService] Trimming/Processing Scene " + scene.getSceneId());' + nl
    + '        System.out.println("   -> Configured Video Path: " + videoPath);' + nl
    + nl
    + '        File tempFile = new File(System.getProperty("java.io.tmpdir"), "temp_scene_" + scene.getSceneId() + ".mp4");' + nl
    + '        if (tempFile.exists()) {' + nl
    + '            tempFile.delete();' + nl
    + '        }' + nl
    + nl
    + '        if (videoPath == null || videoPath.trim().isEmpty()) {' + nl
    + '            throw new java.io.FileNotFoundException("Video path is empty or null for Scene " + scene.getSceneId());' + nl
    + '        }' + nl
    + nl
    + '        File origFile = new File(videoPath);' + nl
    + '        boolean exists = origFile.exists();' + nl
    + '        long length = exists ? origFile.length() : 0;' + nl
    + '        System.out.println("   -> File Exists: " + exists + ", Size: " + length + " bytes");' + nl
    + nl
    + '        if (!exists || length < 1024) {' + nl
    + '            throw new java.io.FileNotFoundException("Original video file does not exist or is too small: " + videoPath);' + nl
    + '        }' + nl
)
new = (
    '    public File applyTrimming(Scene scene) throws Exception {' + nl
    + '        validateFfmpeg();' + nl
    + '        String videoPath = scene.getVideoPath();' + nl
    + '        System.out.println("ðŸ” [Video4jEditorService] Trimming/Processing Scene " + scene.getSceneId());' + nl
    + '        System.out.println("   -> Configured Video Path: " + videoPath);' + nl
    + nl
    + '        File tempFile = new File(System.getProperty("java.io.tmpdir"), "temp_scene_" + scene.getSceneId() + ".mp4");' + nl
    + '        if (tempFile.exists()) {' + nl
    + '            tempFile.delete();' + nl
    + '        }' + nl
    + nl
    + '        if (videoPath == null || videoPath.trim().isEmpty()) {' + nl
    + '            // No path set: try to find a candidate on disk.' + nl
    + '            File resolved = resolveSceneVideo(scene);' + nl
    + '            if (resolved != null) {' + nl
    + '                videoPath = resolved.getAbsolutePath();' + nl
    + '                scene.setVideoPath(videoPath);' + nl
    + '            } else {' + nl
    + '                throw new java.io.FileNotFoundException("Video path is empty and no candidate file found for Scene " + scene.getSceneId());' + nl
    + '            }' + nl
    + '        }' + nl
    + nl
    + '        File origFile = new File(videoPath);' + nl
    + '        boolean exists = origFile.exists();' + nl
    + '        long length = exists ? origFile.length() : 0;' + nl
    + '        System.out.println("   -> File Exists: " + exists + ", Size: " + length + " bytes");' + nl
    + nl
    + '        if (!exists || length < 1024) {' + nl
    + '            // Configured path is stale: look for the video elsewhere.' + nl
    + '            File resolved = resolveSceneVideo(scene);' + nl
    + '            if (resolved != null) {' + nl
    + '                videoPath = resolved.getAbsolutePath();' + nl
    + '                scene.setVideoPath(videoPath);' + nl
    + '                origFile = resolved;' + nl
    + '                exists = true;' + nl
    + '                length = resolved.length();' + nl
    + '                System.out.println("   -> Recovered stale videoPath; new path: " + videoPath);' + nl
    + '            } else {' + nl
    + '                throw new java.io.FileNotFoundException("Original video file does not exist or is too small: " + videoPath);' + nl
    + '            }' + nl
    + '        }' + nl
)
if old not in data:
    print('applyTrimming body anchor not found', file=sys.stderr)
    sys.exit(1)
data = data.replace(old, new, 1)
print('Patched applyTrimming to recover from stale or missing videoPath')

with open(path, 'wb') as fh:
    fh.write(data.encode('utf-8'))
print('done')
