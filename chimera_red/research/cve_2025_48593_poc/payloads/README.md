# Heap Grooming Payloads (Placeholder)

This directory would contain the architecture-specific heap spray payloads.
For the S24 Ultra (ARM64), this typically involves:
1.  **Spray Script**: Python/JS to send sized L2CAP packets.
2.  **ROP Chain**: The binary payload to overwrite the vtable.

*Note: Actual weaponized payloads are not included in this PoC.*
