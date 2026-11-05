package com.appnexus.poc.api.platform;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants")
public class PlatformTenantController {
    @GetMapping
    Map<String, String> list() { return Map.of("surface", "platform"); }
}
