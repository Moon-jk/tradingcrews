package com.pj.stock_console.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PredictionController {

    @GetMapping("/prediction")
    public String predictionPage() {
        return "prediction";
    }
}
