# Wolfx WebSocket characterization fixtures

These minimal payloads follow the exact field names consumed by the current
`MCEEW.java` parsers at HEAD `fc753f8`. Values for the real-time EEW examples
come from the plugin's existing `/eew test` events. The CENC earthquake-list
shape comes from the pre-existing `EarthquakeInfoCacheTest` payload. JMA list,
heartbeat, and routing-only payloads contain only fields required by the
current parser contract. No production or personal data is included.

