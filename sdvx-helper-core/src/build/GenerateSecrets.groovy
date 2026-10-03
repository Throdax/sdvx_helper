/**
 * GenerateSecrets.groovy
 *
 * Executed by groovy-maven-plugin at the generate-sources phase.
 *
 * Reads secrets.properties (absent on CI / contributor machines → uses empty
 * strings so the build never breaks), XOR-encodes each value with a randomly
 * generated per-key mask, and writes GeneratedSecrets.java into
 * target/generated-sources/secrets/.
 *
 * The random mask is regenerated on every mvn clean, so encoded values differ
 * between builds. Plain-text secret values never appear in the JAR.
 */

import java.nio.charset.StandardCharsets
import java.security.SecureRandom

// ---------------------------------------------------------------------------
// Configuration: property keys (dot-notation) → Java method name suffix
// ---------------------------------------------------------------------------
def keys = [
    'maya2.key'              : 'Maya2Key',
    'maya2.url'              : 'Maya2Url',
    'discord.client.id'      : 'DiscordClientId',
    'webhook.reg.url'        : 'WebhookRegUrl',
    'webhook.unknown.url'    : 'WebhookUnknownUrl',
    'webhook.unknown.exh.url': 'WebhookUnknownExhUrl',
    'webhook.unknown.adv.url': 'WebhookUnknownAdvUrl',
    'webhook.unknown.nov.url': 'WebhookUnknownNovUrl',
]

// ---------------------------------------------------------------------------
// Load secrets.properties (gracefully absent)
// ---------------------------------------------------------------------------
def propsFile = new File(project.basedir, 'src/main/resources/secrets.properties')
def props = new Properties()
if (propsFile.exists()) {
    propsFile.withInputStream { props.load(it) }
    log.info("GenerateSecrets: loaded ${propsFile.absolutePath}")
} else {
    log.info("GenerateSecrets: secrets.properties not found — generating stub class (affected features will be disabled)")
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
def rng = new SecureRandom()

/** XOR-encodes a UTF-8 string with a random mask of the given length. */
def encode = { String value ->
    byte[] plain = value.getBytes(StandardCharsets.UTF_8)
    int maskLen = Math.max(8, plain.length)
    byte[] mask = new byte[maskLen]
    rng.nextBytes(mask)
    byte[] enc = new byte[plain.length]
    for (int i = 0; i < plain.length; i++) {
        enc[i] = (byte) (plain[i] ^ mask[i % maskLen])
    }
    [mask: mask, enc: enc]
}

/** Formats a byte array as a Java byte-literal initialiser. */
def byteArrayLiteral = { byte[] arr ->
    arr.collect { b ->
        String.format('(byte)0x%02X', b & 0xFF)
    }.join(', ')
}

// ---------------------------------------------------------------------------
// Generate source
// ---------------------------------------------------------------------------
def sb = new StringBuilder()
sb << """\
package com.sdvxhelper.config;

/** Auto-generated at build time by GenerateSecrets.groovy. Do not edit. */
final class GeneratedSecrets {
    private GeneratedSecrets() {}

"""

keys.each { propKey, methodSuffix ->
    String value = props.getProperty(propKey, '')
    def result = encode(value)

    sb << """\
    static String get${methodSuffix}() {
        byte[] mask = { ${byteArrayLiteral(result.mask)} };
        byte[] enc  = { ${byteArrayLiteral(result.enc)} };
        return decode(enc, mask);
    }

"""
}

sb << """\
    private static String decode(byte[] enc, byte[] mask) {
        byte[] out = new byte[enc.length];
        for (int i = 0; i < enc.length; i++) {
            out[i] = (byte) (enc[i] ^ mask[i % mask.length]);
        }
        return new String(out, java.nio.charset.StandardCharsets.UTF_8);
    }
}
"""

// ---------------------------------------------------------------------------
// Write output file
// ---------------------------------------------------------------------------
def outDir = new File(project.build.directory, 'generated-sources/secrets/com/sdvxhelper/config')
outDir.mkdirs()
def outFile = new File(outDir, 'GeneratedSecrets.java')
outFile.text = sb.toString()
log.info("GenerateSecrets: wrote ${outFile.absolutePath}")

// Register the generated-sources root so javac picks it up
project.addCompileSourceRoot(
    new File(project.build.directory, 'generated-sources/secrets').absolutePath
)
