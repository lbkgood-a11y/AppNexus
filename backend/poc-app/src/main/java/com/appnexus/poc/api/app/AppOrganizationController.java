package com.appnexus.poc.api.app;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organizations")
public class AppOrganizationController {
    @GetMapping
    Map<String, String> list() { return Map.of("surface", "app"); }
}
