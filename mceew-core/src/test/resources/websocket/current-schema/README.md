# Wolfx WebSocket parser fixtures

These payloads are exact copies of the Phase 0 current-schema real-time EEW
fixtures in `mceew-bukkit`. They are duplicated here so the core parser tests
remain module-local without introducing a Maven test-JAR dependency. Their
field names follow the fields consumed by the Phase 2B `MCEEW.java` parsers.
