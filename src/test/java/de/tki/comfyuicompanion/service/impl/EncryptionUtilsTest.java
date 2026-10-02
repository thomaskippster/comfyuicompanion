package de.tki.comfyuicompanion.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EncryptionUtilsTest {

    private final EncryptionUtils encryptionUtils = new EncryptionUtils();

    @Test
    void shouldEncryptAndDecrypt_withValidPassword() throws Exception {
        String secret = "my_super_secret_token";
        String password = "master_password";

        String encrypted = encryptionUtils.encrypt(secret, password);
        assertThat(encrypted).startsWith("$GCM$v1$");

        String decrypted = encryptionUtils.decrypt(encrypted, password);
        assertThat(decrypted).isEqualTo(secret);
    }

    @Test
    void shouldThrowException_whenDecryptingWithWrongPassword() throws Exception {
        String secret = "secret_data";
        String password = "correct_password";
        String wrongPassword = "wrong_password";

        String encrypted = encryptionUtils.encrypt(secret, password);

        assertThatThrownBy(() -> encryptionUtils.decrypt(encrypted, wrongPassword))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldThrowException_whenEncryptingNullString() {
        assertThatThrownBy(() -> encryptionUtils.encrypt(null, "pass"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
