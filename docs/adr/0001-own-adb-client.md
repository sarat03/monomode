# 1. Write our own ADB client

We implement the ADB protocol, connection handling and Wireless debugging pairing ourselves instead of depending on libadb-android, Shizuku or LADB. TLS uses Android's built-in implementation.

Ed25519 point math for SPAKE2 is also ours: a translation of the RFC 8032 reference arithmetic using BigInteger. BouncyCastle was the first choice, but it only exposes signing, not general point add, multiply and decode. Our math is not constant-time; we accept that because pairing runs once, against a 6-digit code that expires. Pairing is verified against known-answer vectors generated from BoringSSL, the implementation adbd uses (`tools/spake2-vectors/`).
