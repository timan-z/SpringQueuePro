package com.springqprobackend.springqpro.config;

/* TaskProcessingException.java
--------------------------------------------------------------------------------------------------
[HISTORY]:
Introduced when ProcessingService took over all retry + failure management.
Handlers were no longer allowed to modify Task state directly, so the only way
a handler could express “this task failed” was by throwing an exception.

This exception formalizes that communication channel.

[CURRENT ROLE]:
Used by TaskHandlers to signal processing failure. ProcessingService catches it
and performs:
  - FAILED state transition
  - retry scheduling (if applicable)
  - metrics emission
  - logging + observability events

[FUTURE WORK]:
May be expanded into:
  - typed failure codes
  - permanent vs transient failure categories
  - handler-specific metadata
--------------------------------------------------------------------------------------------------
*/

public class TaskProcessingException extends RuntimeException {
    public TaskProcessingException(String message) {
        super(message);
    }
    public TaskProcessingException(String message, Throwable cause) { super(message, cause); }
}
