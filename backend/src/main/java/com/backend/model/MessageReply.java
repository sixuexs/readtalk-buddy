package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MessageReply {
    private String content;
    private long timestamp;
}
