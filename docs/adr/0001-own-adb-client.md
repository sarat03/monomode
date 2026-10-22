# 1. Write our own ADB client

We implement the ADB protocol, connection handling and Wireless debugging pairing ourselves instead of depending on libadb-android, Shizuku or LADB.

TLS comes from Conscrypt (Apache-2.0), bundled in the app. Pairing needs the TLS key exporter, and Android only exposes it publicly from API 31 (`SSLSockets.exportKeyingMaterial`), while our minimum is API 30. Conscrypt is the same TLS stack Android itself ships, so we bundle it instead of requiring Android 12. Our TLS identity is an RSA-2048 key in AndroidKeyStore; it needs DECRYPT purpose with no padding because Conscrypt performs the raw RSA operation for TLS 1.3 RSA-PSS signatures.

Ed25519 point math for SPAKE2 is also ours: a translation of the RFC 8032 reference arithmetic using BigInteger. BouncyCastle was the first choice, but it only exposes signing, not general point add, multiply and decode. Our math is not constant-time; we accept that because pairing runs once, against a 6-digit code that expires. Pairing is verified against known-answer vectors generated from BoringSSL, the implementation adbd uses (`tools/spake2-vectors/`).
