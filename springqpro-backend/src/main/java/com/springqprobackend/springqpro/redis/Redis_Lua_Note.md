Redis supports server-side scripting using Lua (a lightweight, high-level, multi-paradigm programming language primarily designed
for embedded use within applications. It's known for its simplicity, efficiency, and ease of integration with other languages,
particularly C and C++), whcih allows for extending Redis' functionality and performing complex operations atomically.

- Lua scripts execute as a single, atomic unit. This means that either the entire script completes successfully, or none of its
operations are applied, ensuring data consistency even with multiple commands. This eliminates the need for explicit transactions
or pipelines for atomicity within the script's execution.

- Lua scripts can implement complex business logic, conditional execution, and custom data manipulations that aren't possible
w/ individual Redis commands. They can access and modify Redis data structures and utilize most of the standard Redis commands
through the redis.call() and redis.pcall() functions within the script.

- Lua scripts are executed using the EVAL command, which takes the script itself, the number of keys the script will access,
and then the keys and arguments as separate parameters. For subsequent executions of the same script, EVALSHA can be used with the 
script's SHA1 digest for efficiency, avoiding repeated transmission of the full script.

- Arguments passed to a Lua script are divided into KEYS and ARGV:
- KEYS are used to specify the keys the script will interact with, allowing Redis to properly route commands in a clustered environment.
- ARGV contains additional arguments or data needed by the script. Within the Lua script, these are accessible as tables KEYS[1], KEYS[2], etc.., and ARGV[1], ARGV[2], etc.
- Important **NOTE**: Lua tables are 1-indexed (they start at index 1 instead of 0).

- Error Handling: Lua scripts can manage errors within the script itself, allowing for more robust and self-contained logic.
- Use Cases: Lua scripting is particularly useful for implementing atomic operations like distributed locks, counters with conditional increments, complex data migrations, or custom commands that combine multiple Redis operations.
  (**Obviously in my case, I'm going to be using it for Distributed Locks**).
