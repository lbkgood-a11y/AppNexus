package com.appnexus.poc.api.partner;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/organizations")
public class PartnerOrganizationController {
    @GetMapping
    Map<String, String> list() { return Map.of("surface", "partner"); }
}
