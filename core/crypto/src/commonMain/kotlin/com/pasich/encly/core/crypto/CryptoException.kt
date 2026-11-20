package com.pasich.encly.core.crypto

/** The provider could not seal: callers fail closed (nothing is written) instead of crashing. */
public class CryptoException(cause: Throwable) : RuntimeException(cause)
