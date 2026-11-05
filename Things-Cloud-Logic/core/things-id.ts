const BASE58_ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

/**
 * Encodes a buffer/Uint8Array to a Base58 string using Bitcoin's alphabet.
 */
export function base58Encode(bytes: Uint8Array): string {
    const digits: number[] = [0];
    for (let i = 0; i < bytes.length; i++) {
        let carry = bytes[i];
        for (let j = 0; j < digits.length; j++) {
            carry += digits[j] << 8;
            digits[j] = carry % 58;
            carry = (carry / 58) | 0;
        }
        while (carry > 0) {
            digits.push(carry % 58);
            carry = (carry / 58) | 0;
        }
    }

    let result = '';
    // Deal with leading zeros
    for (let i = 0; i < bytes.length && bytes[i] === 0; i++) {
        result += BASE58_ALPHABET[0];
    }
    for (let i = digits.length - 1; i >= 0; i--) {
        result += BASE58_ALPHABET[digits[i]];
    }
    return result;
}

/**
 * Decodes a Base58 string into a Uint8Array.
 */
export function base58Decode(str: string): Uint8Array {
    if (str.length === 0) return new Uint8Array(0);
    const bytes = [0];
    for (let i = 0; i < str.length; i++) {
        const char = str[i];
        const value = BASE58_ALPHABET.indexOf(char);
        if (value === -1) {
            throw new Error(`Invalid Base58 character: ${char}`);
        }
        let carry = value;
        for (let j = 0; j < bytes.length; j++) {
            carry += bytes[j] * 58;
            bytes[j] = carry & 0xff;
            carry >>= 8;
        }
        while (carry > 0) {
            bytes.push(carry & 0xff);
            carry >>= 8;
        }
    }
    // Deal with leading '1's
    for (let i = 0; i < str.length && str[i] === BASE58_ALPHABET[0]; i++) {
        bytes.push(0);
    }
    return new Uint8Array(bytes.reverse());
}

/**
 * Generates a Things 3 canonical Base58 UUID (16 random bytes encoded in Base58).
 */
export function generateThingsId(): string {
    const bytes = new Uint8Array(16);
    if (typeof crypto !== 'undefined' && crypto.getRandomValues) {
        crypto.getRandomValues(bytes);
    } else {
        // Fallback for older environments
        for (let i = 0; i < 16; i++) {
            bytes[i] = Math.floor(Math.random() * 256);
        }
    }
    return base58Encode(bytes);
}

/**
 * Checks if a string looks like a valid Things ID (Base58 string or hyphenated UUID).
 */
export function isValidThingsId(id: string): boolean {
    if (!id || typeof id !== 'string') return false;
    // Things IDs are typically 21-22 chars in base58 or standard UUID (36 chars)
    if (id.length >= 10 && id.length <= 40) return true;
    return false;
}
