package com.springqprobackend.springqpro.controller.graphql;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class GraphiQLRedirectController {
    @GetMapping({"/graphiql", "/graphiql/"})
    public String redirect() {
        return "redirect:/graphiql/index.html";
    }
}
