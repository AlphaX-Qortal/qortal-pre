package org.qortal.data.account;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AccountBlocksMintedAdjustmentData {

    // Properties
    private String address;
    protected int blocksMintedAdjustment;

    // Constructors

    // necessary for JAXB
    protected AccountBlocksMintedAdjustmentData() {
    }

    public AccountBlocksMintedAdjustmentData(String address, int blocksMintedAdjustment) {
        this.address = address;
        this.blocksMintedAdjustment = blocksMintedAdjustment;
    }

    // Getters/Setters

    public String getAddress() {
        return this.address;
    }

    public int getBlocksMintedAdjustment() {
        return this.blocksMintedAdjustment;
    }

    public String toString() {
        return String.format("%s has blocks minted adjustment %d", this.address, this.blocksMintedAdjustment);
    }

    @Override
    public boolean equals(Object b) {
        if (!(b instanceof AccountBlocksMintedAdjustmentData))
            return false;

        return this.getAddress().equals(((AccountBlocksMintedAdjustmentData) b).getAddress());
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }
}