package com.pccw.immd.vtvsplatform.controller;

import lombok.Getter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@RestController
public class LeakController {

    private static final List< String> CACHE = new ArrayList<>();


    @GetMapping("/leak")
    public String leak(){
        for (int i = 0; i < 10000; i++) {
            CACHE.add(UUID.randomUUID().toString());
        }
        return "Add 10k UUIDs , total size :"+ CACHE.size();

    }

    @GetMapping("/health")
    public String health() {
        return "I'm alive, current cache size: " + CACHE.size();
    }



}
