# 1. Write our own ADB client

We implement the ADB protocol, connection handling and Wireless debugging pairing ourselves instead of depending on libadb-android, Shizuku or LADB. Exceptions: TLS uses Android's built-in implementation, and Ed25519 curve math for SPAKE2 uses BouncyCastle, because hand-written crypto primitives are the likeliest way to ship a security hole. Pairing is verified against AOSP test vectors.
