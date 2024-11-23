package org.openmrs.hotreload.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatusTrackingService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private String currentStatus = "Idle";

    @Autowired
    public StatusTrackingService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void updateStatus(String status) {
        this.currentStatus = status;
        messagingTemplate.convertAndSend("/topic/status", status);
    }

    public String getCurrentStatus() {
        return currentStatus;
    }
}
