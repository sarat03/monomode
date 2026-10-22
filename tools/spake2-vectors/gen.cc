// Generates known-answer vectors for our SPAKE2 pairing code using BoringSSL,
// the implementation adbd itself uses. Run tools/spake2-vectors/gen.sh.
// Reads private keys out of BoringSSL's context struct so our Kotlin code can
// be fed the same "random" scalar.
#include <openssl/curve25519.h>
#include <openssl/digest.h>
#include <openssl/hkdf.h>
#include <cstdio>
#include "crypto/curve25519/internal.h"

static void hex(const char *label, const uint8_t *p, size_t n) {
  printf("%s=", label);
  for (size_t i = 0; i < n; i++) printf("%02x", p[i]);
  printf("\n");
}

int main() {
  // Names as adb sends them: sizeof() of a string literal, so the NUL is included.
  static const uint8_t kClient[] = "adb pair client", kServer[] = "adb pair server";
  const uint8_t pw[] = {0x4f, 0x5a, 0x01, 0x46}, wrong[] = {0x4f, 0x5a, 0x01, 0x47};

  SPAKE2_CTX *a = SPAKE2_CTX_new(spake2_role_alice, kClient, sizeof(kClient), kServer, sizeof(kServer));
  SPAKE2_CTX *b = SPAKE2_CTX_new(spake2_role_bob, kServer, sizeof(kServer), kClient, sizeof(kClient));
  SPAKE2_CTX *w = SPAKE2_CTX_new(spake2_role_bob, kServer, sizeof(kServer), kClient, sizeof(kClient));
  uint8_t am[32], bm[32], wm[32], ak[64], bk[64], wk[64];
  size_t n;
  SPAKE2_generate_msg(a, am, &n, 32, pw, sizeof(pw));
  SPAKE2_generate_msg(b, bm, &n, 32, pw, sizeof(pw));
  SPAKE2_generate_msg(w, wm, &n, 32, wrong, sizeof(wrong));
  SPAKE2_process_msg(a, ak, &n, 64, bm, 32);
  SPAKE2_process_msg(b, bk, &n, 64, am, 32);
  SPAKE2_process_msg(w, wk, &n, 64, am, 32);

  uint8_t aes[16];
  static const uint8_t info[] = "adb pairing_auth aes-128-gcm key";
  HKDF(aes, sizeof(aes), EVP_sha256(), ak, 64, nullptr, 0, info, sizeof(info) - 1);

  hex("password", pw, sizeof(pw));
  hex("alice_private", a->private_key, 32);
  hex("alice_password_hash", a->password_hash, 64);
  hex("alice_password_scalar", a->password_scalar, 32);
  hex("alice_msg", am, 32);
  hex("bob_private", b->private_key, 32);
  hex("bob_msg", bm, 32);
  hex("key", ak, 64);
  hex("bob_key", bk, 64);
  hex("wrong_password", wrong, sizeof(wrong));
  hex("wrong_private", w->private_key, 32);
  hex("wrong_msg", wm, 32);
  hex("wrong_key", wk, 64);
  hex("aes_key", aes, 16);
}
