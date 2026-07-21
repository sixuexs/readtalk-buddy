package com.backend.event;

import com.backend.entity.ContactEntity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContactSavedEvent {

    private ContactEntity contact;
}
