package com.anonymous.datahub.data_hub.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test")
    public String home() {
        return "Demo application is running111111";
    }
}
