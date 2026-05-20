package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GreetingReply {
    private String content;
    private long timestamp;
}
