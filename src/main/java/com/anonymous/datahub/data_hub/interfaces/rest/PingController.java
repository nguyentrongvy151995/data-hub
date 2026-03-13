package com.anonymous.datahub.data_hub.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
    @GetMapping("/ping")
    public String home() {
        return "Ping application is running";
    }
}
