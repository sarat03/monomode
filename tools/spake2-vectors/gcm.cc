// Deterministic AES-128-GCM vectors for adb's pairing cipher, using BoringSSL like adbd.
// Nonce = 12 bytes: the 64-bit sequence number (little-endian) then zeros. No AAD, 16-byte tag.
// Build like gen.cc: clang++ -std=c++20 -I$B -I$B/include gcm.cc $B/build/libcrypto.a
#include <openssl/aead.h>
#include <cstdio>
#include <cstring>

static void hex(const char *label, const uint8_t *p, size_t n) {
  printf("%s=", label);
  for (size_t i = 0; i < n; i++) printf("%02x", p[i]);
  printf("\n");
}

int main() {
  // aes_key from vectors.txt
  const uint8_t key[16] = {0xd7, 0xfb, 0xf9, 0x8a, 0x9d, 0xa9, 0xe4, 0x15,
                           0x3e, 0x08, 0x2b, 0x87, 0xcf, 0x85, 0x72, 0xbe};
  const uint8_t msg[] = {0x2a, 0x2b, 0x2c, 0xff, 0x45, 0x12, 0x33};
  EVP_AEAD_CTX ctx;
  EVP_AEAD_CTX_init(&ctx, EVP_aead_aes_128_gcm(), key, 16, EVP_AEAD_DEFAULT_TAG_LENGTH, nullptr);
  for (uint64_t seq = 0; seq < 2; seq++) {
    uint8_t nonce[12] = {0}, out[64];
    size_t n;
    memcpy(nonce, &seq, sizeof(seq));
    EVP_AEAD_CTX_seal(&ctx, out, &n, sizeof(out), nonce, 12, msg, sizeof(msg), nullptr, 0);
    hex(seq == 0 ? "seal_seq0" : "seal_seq1", out, n);
  }
  hex("plaintext", msg, sizeof(msg));
}
