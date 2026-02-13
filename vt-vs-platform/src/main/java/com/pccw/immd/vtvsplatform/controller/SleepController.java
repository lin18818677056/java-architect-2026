package com.pccw.immd.vtvsplatform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SleepController {

    @GetMapping("/block/{seconds}")
    public String block(@PathVariable int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
        return "Slept for " + seconds + " seconds on" + Thread.currentThread();
    }

}
