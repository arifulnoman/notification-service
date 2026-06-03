package com.notification_service.controller;

import java.util.Date;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private static final long APP_START_TIME = System.currentTimeMillis();

    @GetMapping("/")
    public String health() {
        return "Notification Service is running since: " + new Date(APP_START_TIME);
    }
}
