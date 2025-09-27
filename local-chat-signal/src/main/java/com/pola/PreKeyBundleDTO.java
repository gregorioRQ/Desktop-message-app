package com.pola;

public class PreKeyBundleDTO {
    public int registrationId;
    public int deviceId;
    public int preKeyId;
    public byte[] preKeyPublic;
    public int signedPreKeyId;
    public byte[] signedPreKeyPublic;
    public byte[] signedPreKeySignature;
    public byte[] identityKey;

    public PreKeyBundleDTO() {
    };

    public PreKeyBundleDTO(int registrationId, int deviceId,
            int preKeyId, byte[] preKeyPublic,
            int signedPreKeyId, byte[] signedPreKeyPublic,
            byte[] signedPreKeySignature,
            byte[] identityKey) {
        this.registrationId = registrationId;
        this.deviceId = deviceId;
        this.preKeyId = preKeyId;
        this.preKeyPublic = preKeyPublic;
        this.signedPreKeyId = signedPreKeyId;
        this.signedPreKeyPublic = signedPreKeyPublic;
        this.signedPreKeySignature = signedPreKeySignature;
        this.identityKey = identityKey;
    }

    public int getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(int registrationId) {
        this.registrationId = registrationId;
    }

    public int getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(int deviceId) {
        this.deviceId = deviceId;
    }

    public int getPreKeyId() {
        return preKeyId;
    }

    public void setPreKeyId(int preKeyId) {
        this.preKeyId = preKeyId;
    }

    public byte[] getPreKeyPublic() {
        return preKeyPublic;
    }

    public void setPreKeyPublic(byte[] preKeyPublic) {
        this.preKeyPublic = preKeyPublic;
    }

    public int getSignedPreKeyId() {
        return signedPreKeyId;
    }

    public void setSignedPreKeyId(int signedPreKeyId) {
        this.signedPreKeyId = signedPreKeyId;
    }

    public byte[] getSignedPreKeyPublic() {
        return signedPreKeyPublic;
    }

    public void setSignedPreKeyPublic(byte[] signedPreKeyPublic) {
        this.signedPreKeyPublic = signedPreKeyPublic;
    }

    public byte[] getSignedPreKeySignature() {
        return signedPreKeySignature;
    }

    public void setSignedPreKeySignature(byte[] signedPreKeySignature) {
        this.signedPreKeySignature = signedPreKeySignature;
    }

    public byte[] getIdentityKey() {
        return identityKey;
    }

    public void setIdentityKey(byte[] identityKey) {
        this.identityKey = identityKey;
    }

}
