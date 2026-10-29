package com.pasich.encly.core.keystore

/**
 * There is no usable secret store: the operating system has none Encly supports, or (on Linux) no Secret
 * Service is running. Encly never falls back to keeping the secret in a file.
 */
public class SecretStoreUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
