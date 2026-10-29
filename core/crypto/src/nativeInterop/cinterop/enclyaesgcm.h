#ifndef ENCLY_AES_GCM_H
#define ENCLY_AES_GCM_H

#include <stddef.h>
#include <stdint.h>

/* Implemented in Swift over CryptoKit (EnclyAesGcm.swift). 0 on success, -1 on failure. */

/* out receives plaintext_length + 16 bytes: the ciphertext followed by the tag. */
int32_t encly_aes_gcm_seal(const uint8_t *key, ptrdiff_t key_length,
                           const uint8_t *nonce, ptrdiff_t nonce_length,
                           const uint8_t *aad, ptrdiff_t aad_length,
                           const uint8_t *plaintext, ptrdiff_t plaintext_length,
                           uint8_t *out);

/* out receives ciphertext_length - 16 bytes of plaintext. -1 also when the tag does not verify. */
int32_t encly_aes_gcm_open(const uint8_t *key, ptrdiff_t key_length,
                           const uint8_t *nonce, ptrdiff_t nonce_length,
                           const uint8_t *aad, ptrdiff_t aad_length,
                           const uint8_t *ciphertext, ptrdiff_t ciphertext_length,
                           uint8_t *out);

#endif
