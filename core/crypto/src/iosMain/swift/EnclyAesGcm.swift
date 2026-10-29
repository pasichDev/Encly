import CryptoKit
import Foundation

// AES-256-GCM for Kotlin/Native. CommonCrypto has no public GCM and CryptoKit is Swift-only, so this file
// is the one Swift seam of the module: two C-callable functions (declared in enclyaesgcm.h) over
// CryptoKit's AES.GCM. Everything else is Kotlin. Keys, nonces and data arrive as raw pointers into the
// caller's arrays and results are written into caller-owned buffers, so the Kotlin side can wipe them.
// Fixed: 32-byte key, 12-byte nonce, 16-byte tag.

private let keyBytes = 32
private let nonceBytes = 12
private let tagBytes = 16

// Kotlin passes no pointer for an empty array. A buffer view with a nil base traps inside Foundation's
// ContiguousBytes on iOS, so an empty input is a zero-length view of this byte instead.
private let emptyInput = UnsafeMutablePointer<UInt8>.allocate(capacity: 1)

private func view(_ pointer: UnsafePointer<UInt8>?, _ count: Int) -> UnsafeRawBufferPointer {
    UnsafeRawBufferPointer(start: pointer ?? UnsafePointer(emptyInput), count: count)
}

/// Writes `ciphertext || tag` (plaintextLength + 16 bytes) into `out`. Returns 0, or -1 when CryptoKit refuses.
@_cdecl("encly_aes_gcm_seal")
public func enclyAesGcmSeal(
    _ key: UnsafePointer<UInt8>?, _ keyLength: Int,
    _ nonce: UnsafePointer<UInt8>?, _ nonceLength: Int,
    _ aad: UnsafePointer<UInt8>?, _ aadLength: Int,
    _ plaintext: UnsafePointer<UInt8>?, _ plaintextLength: Int,
    _ out: UnsafeMutablePointer<UInt8>?
) -> Int32 {
    guard keyLength == keyBytes, nonceLength == nonceBytes, let out = out else { return -1 }  // out holds at least the tag
    do {
        let symmetricKey = SymmetricKey(data: view(key, keyLength))
        let gcmNonce = try AES.GCM.Nonce(data: view(nonce, nonceLength))
        let box = try AES.GCM.seal(
            view(plaintext, plaintextLength), using: symmetricKey, nonce: gcmNonce,
            authenticating: view(aad, aadLength))
        guard box.ciphertext.count == plaintextLength, box.tag.count == tagBytes else { return -1 }
        box.ciphertext.copyBytes(to: out, count: plaintextLength)
        box.tag.copyBytes(to: out + plaintextLength, count: tagBytes)
        return 0
    } catch {
        return -1
    }
}

/// Writes the plaintext (ciphertextLength - 16 bytes) into `out`. Returns 0, or -1 when the data does not
/// authenticate (wrong key, nonce or aad, changed or truncated data).
@_cdecl("encly_aes_gcm_open")
public func enclyAesGcmOpen(
    _ key: UnsafePointer<UInt8>?, _ keyLength: Int,
    _ nonce: UnsafePointer<UInt8>?, _ nonceLength: Int,
    _ aad: UnsafePointer<UInt8>?, _ aadLength: Int,
    _ ciphertext: UnsafePointer<UInt8>?, _ ciphertextLength: Int,
    _ out: UnsafeMutablePointer<UInt8>?
) -> Int32 {
    guard keyLength == keyBytes, nonceLength == nonceBytes, ciphertextLength >= tagBytes else { return -1 }
    let bodyLength = ciphertextLength - tagBytes
    guard bodyLength == 0 || out != nil else { return -1 }
    do {
        let symmetricKey = SymmetricKey(data: view(key, keyLength))
        let gcmNonce = try AES.GCM.Nonce(data: view(nonce, nonceLength))
        let sealed = view(ciphertext, ciphertextLength)
        let box = try AES.GCM.SealedBox(
            nonce: gcmNonce,
            ciphertext: UnsafeRawBufferPointer(rebasing: sealed[0..<bodyLength]),
            tag: UnsafeRawBufferPointer(rebasing: sealed[bodyLength..<ciphertextLength]))
        var plain = try AES.GCM.open(box, using: symmetricKey, authenticating: view(aad, aadLength))
        guard plain.count == bodyLength else { return -1 }
        if let out = out { plain.copyBytes(to: out, count: bodyLength) }
        // The plaintext is the caller's secret: drop the copy CryptoKit made.
        plain.resetBytes(in: 0..<plain.count)
        return 0
    } catch {
        return -1
    }
}
