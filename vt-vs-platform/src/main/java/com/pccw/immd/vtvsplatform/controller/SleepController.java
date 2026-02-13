package com.pccw.immd.vtvsplatform.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.locks.ReentrantLock;

@RestController
public class SleepController {

    @GetMapping("/block/{seconds}")
    public String block(@PathVariable int seconds) throws InterruptedException {
        Thread.sleep(seconds * 1000L);
        return "Slept for " + seconds + " seconds on" + Thread.currentThread();
    }



    @GetMapping("block-print/{seconds}")
    public String blockPrint(@PathVariable int seconds) throws InterruptedException {
        synchronized  (this) {
            Thread.sleep(seconds * 1000L);
            return "Pinned! " + Thread.currentThread();
        }
    }

    private  final ReentrantLock lock = new ReentrantLock();

    @GetMapping("/block-fixed/{seconds}")
    public String blockFixed(@PathVariable int seconds) throws InterruptedException {
        lock.lock();
        try {
            Thread.sleep(seconds * 1000L);
            return "Fixed  " + seconds + " seconds on" + Thread.currentThread();
        } finally {
            lock.unlock();
        }


    }




}
