package org.example.livebill.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {

  @GetMapping("/hello")
  public String hello() {
    return "hello live-bill";
  }
}
