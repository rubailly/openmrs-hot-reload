package org.openmrs.hotreload.service;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.CloseStatus;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClassReloadService {
    
    private static final Logger log = LoggerFactory.getLogger(ClassReloadService.class);
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final StatusTrackingService statusTrackingService;
    private final Cache<String, byte[]> classCache;
    private final Cache<String, Long> reloadThrottleCache;
    
    private static final long THROTTLE_PERIOD_MS = 1000; // Minimum time between reloads of same class
    private static final String[] RESTART_REQUIRED_PACKAGES = {
        ".api.", ".service.", ".web.", ".rest.", ".controller."
    };
    
    @Autowired
    public ClassReloadService(StatusTrackingService statusTrackingService) {
        this.statusTrackingService = statusTrackingService;
        this.classCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
        this.reloadThrottleCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofSeconds(2))
            .build();
    }

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 5000;

    public void connectToModule(String moduleId, String openmrsUrl) {
        int attempts = 0;
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                StandardWebSocketClient client = new StandardWebSocketClient();
                String wsUrl = openmrsUrl.replace("http", "ws") + "/ws/module/" + moduleId;
                
                WebSocketSession session = client.doHandshake(
                    new BinaryWebSocketHandler() {
                        @Override
                        public void afterConnectionEstablished(WebSocketSession session) {
                            sessions.put(moduleId, session);
                            log.info("Connected to OpenMRS module: {}", moduleId);
                            statusTrackingService.updateStatus("Connected to module: " + moduleId);
                        }
                        
                        @Override
                        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
                            sessions.remove(moduleId);
                            log.info("Disconnected from OpenMRS module: {} with status: {}", moduleId, status);
                            statusTrackingService.updateStatus("Disconnected from module: " + moduleId);
                            
                            // Schedule reconnection attempt using a timer
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    connectToModule(moduleId, openmrsUrl);
                                }
                            }, RETRY_DELAY_MS);
                        }
                        
                        @Override
                        public void handleTransportError(WebSocketSession session, Throwable exception) {
                            log.error("Transport error for module " + moduleId, exception);
                            statusTrackingService.updateStatus("Connection error: " + exception.getMessage());
                        }
                    },
                    wsUrl
                ).get();
                
                sessions.put(moduleId, session);
                log.info("Successfully connected to OpenMRS module {} at {}", moduleId, wsUrl);
                statusTrackingService.updateStatus("Connected and ready for hot reload");
                break;
                
            } catch (Exception e) {
                attempts++;
                log.error("Failed to connect to OpenMRS module: {} (Attempt {}/{})", 
                    moduleId, attempts, MAX_RETRY_ATTEMPTS, e);
                statusTrackingService.updateStatus("Connection failed: " + e.getMessage());
                
                if (attempts < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    public void reloadClass(String className, byte[] classBytes) throws IOException {
        // Check throttle cache to prevent rapid reloads
        Long lastReload = reloadThrottleCache.getIfPresent(className);
        if (lastReload != null && System.currentTimeMillis() - lastReload < THROTTLE_PERIOD_MS) {
            log.debug("Throttling reload of class {}", className);
            return;
        }

        // Check content cache to avoid unnecessary reloads
        byte[] cachedBytes = classCache.getIfPresent(className);
        if (cachedBytes != null && Arrays.equals(cachedBytes, classBytes)) {
            log.debug("Class {} unchanged, skipping reload", className);
            return;
        }

        // Determine if module restart is needed
        boolean requiresRestart = Arrays.stream(RESTART_REQUIRED_PACKAGES)
            .anyMatch(className::contains);

        // Format message for WebSocket transmission
        ByteBuffer message = formatClassMessage(className, classBytes);
        BinaryMessage binaryMessage = new BinaryMessage(message);
        
        // Send to all connected OpenMRS instances using synchronized blocks for thread safety
        CompletableFuture.runAsync(() -> {
            sessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .forEach(session -> {
                    synchronized (session) {
                        try {
                            session.sendMessage(binaryMessage);
                            String status = requiresRestart ? 
                                "Sent class " + className + " to OpenMRS - module restart required" :
                                "Sent class " + className + " to OpenMRS - hot reload only";
                            statusTrackingService.updateStatus(status);
                        } catch (IOException e) {
                            log.error("Failed to send class {} to session", className, e);
                            statusTrackingService.updateStatus("Error reloading " + className + ": " + e.getMessage());
                        }
                    }
                });
        });

        // Update caches
        classCache.put(className, classBytes);
        reloadThrottleCache.put(className, System.currentTimeMillis());
    }

    private ByteBuffer formatClassMessage(String className, byte[] classBytes) throws IOException {
        byte[] classNameBytes = className.getBytes("UTF-8");
        ByteBuffer message = ByteBuffer.allocate(4 + classNameBytes.length + classBytes.length);
        message.putInt(classNameBytes.length);
        message.put(classNameBytes);
        message.put(classBytes);
        message.flip();
        return message;
    }
}
