package com.appnexus.poc.auth;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/poc/session")
class SessionProbeController {

    @GetMapping
    Map<String, String> session(HttpSession session) {
        return Map.of("sessionId", session.getId());
    }

    @PostMapping("/mutate")
    Map<String, Boolean> mutate() {
        return Map.of("updated", true);
    }
}
