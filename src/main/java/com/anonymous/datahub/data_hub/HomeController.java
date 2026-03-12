package com.anonymous.datahub.data_hub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping("/abc")
    public String home() {
        return "Demo application is running";
    }
}
