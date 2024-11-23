package org.openmrs.hotreload.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class FileWatchService {
    
    private static final Logger log = LoggerFactory.getLogger(FileWatchService.class);
    private final Map<WatchKey, Path> watchKeys = new HashMap<>();
    private WatchService watchService;
    
    private final ClassReloadService classReloadService;
    private final StatusTrackingService statusTrackingService;

    @Autowired
    public FileWatchService(ClassReloadService classReloadService,
                          StatusTrackingService statusTrackingService) {
        this.classReloadService = classReloadService;
        this.statusTrackingService = statusTrackingService;
        
        // Auto-start watching configured directory
        String moduleDir = System.getProperty("hotreload.moduleDir");
        if (moduleDir != null) {
            try {
                this.watchService = FileSystems.getDefault().newWatchService();
                Path path = Paths.get(moduleDir, "target", "classes");
                if (Files.exists(path)) {
                    WatchKey key = path.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    watchKeys.put(key, path);
                    statusTrackingService.updateStatus("Watching classes in: " + path);
                } else {
                    log.warn("Classes directory not found: {}", path);
                }
            } catch (IOException e) {
                log.error("Failed to initialize watch service", e);
            }
        }
    }

    @Scheduled(fixedDelay = 500)
    public void processEvents() {
        try {
            WatchKey key = watchService.poll();
            if (key != null) {
                Path dir = watchKeys.get(key);
                if (dir != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            log.warn("WatchService overflow occurred");
                            continue;
                        }
                        
                        Path changed = dir.resolve((Path) event.context());
                        String path = changed.toString();
                        
                        if (path.endsWith(".class")) {
                            log.debug("Detected change in class file: {}", path);
                            handleClassChange(changed);
                        }
                    }
                }
                
                // Reset key and remove from set if directory no longer accessible
                boolean valid = key.reset();
                if (!valid) {
                    watchKeys.remove(key);
                    log.warn("Directory no longer accessible: {}", dir);
                    
                    // Try to re-register if possible
                    if (Files.exists(dir)) {
                        try {
                            WatchKey newKey = dir.register(watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                            watchKeys.put(newKey, dir);
                            log.info("Re-registered directory: {}", dir);
                        } catch (IOException e) {
                            log.error("Failed to re-register directory: " + dir, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing file watch events", e);
            statusTrackingService.updateStatus("Error watching files: " + e.getMessage());
        }
    }

    private void handleClassChange(Path classFile) {
        try {
            byte[] classBytes = Files.readAllBytes(classFile);
            String className = getClassName(classFile);
            
            statusTrackingService.updateStatus("Reloading class: " + className);
            classReloadService.reloadClass(className, classBytes);
            
        } catch (Exception e) {
            log.error("Failed to reload class: " + classFile, e);
            statusTrackingService.updateStatus("Error: " + e.getMessage());
        }
    }

    private String getClassName(Path classFile) {
        // Convert path/to/class.class to package.path.to.class
        String relativePath = watchKeys.get(classFile).relativize(classFile).toString();
        return relativePath.substring(0, relativePath.length() - 6).replace('/', '.');
    }

    public void stopWatching() {
        try {
            watchService.close();
            statusTrackingService.updateStatus("File watching stopped");
        } catch (IOException e) {
            log.error("Error closing watch service", e);
        }
    }
}
