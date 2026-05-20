package com.backend.model;

import lombok.Data;

@Data
public class SendRequest {
    private String scenarioId;
    private String message;
}
