package com.springqprobackend.springqpro.events;

// DEBUG:+NOTE:+TO-DO: Maybe merge this and the other records file I have for the GraphQL stuff.

// 2025-11-13-EDIT: This record will be "published" by Task after saving a new task.
public record TaskCreatedEvent(Object source, String taskId) { }
