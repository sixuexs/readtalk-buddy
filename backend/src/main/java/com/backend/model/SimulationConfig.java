package com.backend.model;

import lombok.Data;

import java.util.List;

@Data
public class SimulationConfig {
    private List<String> themes;
    private List<String> personalities;
}
