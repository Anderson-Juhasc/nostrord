import * as secp256k1 from '@noble/secp256k1';
import { sha256 as sha256Hash } from '@noble/hashes/sha256';

export function randomPrivateKey() {
    return secp256k1.utils.randomPrivateKey();
}

export function getPublicKey(privateKey, compressed) {
    return secp256k1.getPublicKey(privateKey, compressed);
}

export function schnorrSign(message, privateKey) {
    return secp256k1.schnorr.signSync(message, privateKey);
}

export function schnorrVerify(signature, message, publicKey) {
    return secp256k1.schnorr.verifySync(signature, message, publicKey);
}

export function sha256(data) {
    return sha256Hash(data);
}
