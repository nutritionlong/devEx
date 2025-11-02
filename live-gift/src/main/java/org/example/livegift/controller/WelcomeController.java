package org.example.livegift.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {

  @GetMapping("/hello")
  public String sayHello() {
    return "hello live-gift";
  }
}
