package com.technocreatives.beckon.mesh.data.util;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.Locale;


/**
 * Abstract class for bluetooth mesh addresses
 * copy from: package no.nordicsemi.android.mesh.utils.MeshAddress;
 */

public final class MeshAddress {

    private static final byte[] VTAD = "vtad".getBytes(Charset.forName("US-ASCII"));

    //Unassigned addresses
    public static final int UNASSIGNED_ADDRESS = 0x0000;

    //Unicast addresses
    public static final int START_UNICAST_ADDRESS = 0x0001;
    public static final int END_UNICAST_ADDRESS = 0x7FFF;

    //Group address start and end defines the address range that can be used to create groups
    public static final int START_GROUP_ADDRESS = 0xC000;
    public static final int END_GROUP_ADDRESS = 0xFEFF;

    //Fixed group addresses
    public static final int ALL_PROXIES_ADDRESS = 0xFFFC;
    public static final int ALL_FRIENDS_ADDRESS = 0xFFFD;
    public static final int ALL_RELAYS_ADDRESS = 0xFFFE;
    public static final int ALL_NODES_ADDRESS = 0xFFFF;

    //Virtual addresses
    private static final byte B1_VIRTUAL_ADDRESS = (byte) 0x80;
    public static final int START_VIRTUAL_ADDRESS = 0x8000;
    public static final int END_VIRTUAL_ADDRESS = 0xBFFF;
    public static final int UUID_HASH_BIT_MASK = 0x3FFF;

    public static String formatAddress(final int address, final boolean add0x) {
        return add0x ?
                "0x" + String.format(Locale.US, "%04X", address) :
                String.format(Locale.US, "%04X", address);
    }

    public static boolean isAddressInRange(final byte[] address) {
        return address.length == 2 && isAddressInRange((address[0] & 0xFF) << 8 | address[1] & 0xFF);
    }

    /**
     * Checks if the address is in range
     *
     * @param address address
     * @return true if is in range or false otherwise
     */
    public static boolean isAddressInRange(final int address) {
        return address == (address & 0xFFFF);
    }

    /**
     * Validates a unicast address
     *
     * @param address 16-bit address
     * @return true if the address is a valid unicast address or false otherwise
     */
    public static boolean isValidUnicastAddress(final int address) {
        return isAddressInRange(address) && (address >= START_UNICAST_ADDRESS && address <= END_UNICAST_ADDRESS);
    }

    /**
     * Validates a unicast address
     *
     * @param address 16-bit address
     * @return true if the address is a valid virtual address or false otherwise
     */
    public static boolean isValidVirtualAddress(final int address) {
        if (isAddressInRange(address)) {
            return address >= START_VIRTUAL_ADDRESS && address <= END_VIRTUAL_ADDRESS;
        }
        return false;
    }

    /**
     * Returns true if the its a valid group address
     *
     * @param address 16-bit address
     * @return true if the address is valid and false otherwise
     */
    public static boolean isValidGroupAddress(final int address) {
        if (!isAddressInRange(address))
            return false;
        final int b0 = address >> 8 & 0xFF;
        final int b1 = address & 0xFF;
        final boolean groupRange = b0 >= 0xC0 && b0 <= 0xFF;
        final boolean rfu = b0 == 0xFF && b1 >= 0x00 && b1 <= 0xFB;
        final boolean allNodes = b0 == 0xFF && b1 == 0xFF;
        return groupRange && !rfu && !allNodes;
    }

    public static int unsignedBytesToInt(byte b0, byte b1) {
        return (unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8));
    }

    public static int bytesToInt(byte[] b, final ByteOrder byteOrder) {
        return b.length == 4 ? ByteBuffer.wrap(b).order(byteOrder).getInt() : ByteBuffer.wrap(b).order(byteOrder).getShort();
    }

    public static int unsignedByteToInt(byte b) {
        return b & 0xFF;
    }

}