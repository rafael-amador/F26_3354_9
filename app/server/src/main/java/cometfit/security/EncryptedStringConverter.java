package cometfit.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** JPA converter that stores a String column encrypted with {@link FieldEncryptor} (NFR Security; A-5). */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : FieldEncryptor.get().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : FieldEncryptor.get().decrypt(dbData);
    }
}
