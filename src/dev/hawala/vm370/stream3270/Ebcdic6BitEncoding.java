/*
** This file is part of the external MECAFF process implementation.
** (MECAFF :: Multiline External Console And Fullscreen Facility 
**            for VM/370 R6 SixPack 1.2)
**
** This software is provided "as is" in the hope that it will be useful, with
** no promise, commitment or even warranty (explicit or implicit) to be
** suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
** Released to the public domain.
*/

package dev.hawala.vm370.stream3270;

/**
 * Mapping of 6-bit values into the 3270 data stream representation as
 * displayable character.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public class Ebcdic6BitEncoding {

	protected static final byte[] codes3270 = {
		  (byte)0x40, (byte)0xC1, (byte)0xC2, (byte)0xC3, 
		  (byte)0xC4, (byte)0xC5, (byte)0xC6, (byte)0xC7, 
		  (byte)0xC8, (byte)0xC9, (byte)0x4A, (byte)0x4B, 
		  (byte)0x4C, (byte)0x4D, (byte)0x4E, (byte)0x4F,
		  (byte)0x50, (byte)0xD1, (byte)0xD2, (byte)0xD3,
		  (byte)0xD4, (byte)0xD5, (byte)0xD6, (byte)0xD7,
		  (byte)0xD8, (byte)0xD9, (byte)0x5A, (byte)0x5B,
		  (byte)0x5C, (byte)0x5D, (byte)0x5E, (byte)0x5F,
		  (byte)0x60, (byte)0x61, (byte)0xE2, (byte)0xE3,
		  (byte)0xE4, (byte)0xE5, (byte)0xE6, (byte)0xE7,
		  (byte)0xE8, (byte)0xE9, (byte)0x6A, (byte)0x6B,
		  (byte)0x6C, (byte)0x6D, (byte)0x6E, (byte)0x6F,
		  (byte)0xF0, (byte)0xF1, (byte)0xF2, (byte)0xF3,
		  (byte)0xF4, (byte)0xF5, (byte)0xF6, (byte)0xF7,
		  (byte)0xF8, (byte)0xF9, (byte)0x7A, (byte)0x7B,
		  (byte)0x7C, (byte)0x7D, (byte)0x7E, (byte)0x7F
		};
	
	/**
	 * Get the byte encoding the lower 6 bits of the passed value.
	 * @param value the value to encode.
	 * @return the equivalent byte encoding the 6-bit value. 
	 */
	public static byte encode6BitValue(byte value) {
		value &= 0x3F;
		return codes3270[value];
	}
	
	/**
	 * Get the 6-bit value encoded by the byte passed byte.
	 * @param b the byte to decode.
	 * @return the value encoded by the byte or 0 if the byte is not a
	 * value encoding.
	 */
	public static int valueOf(byte b) {
		for (int i = 0; i < 64; i++) {
			if (codes3270[i] == b) { return i; }
		}
		return 0; // avoid any strange exceptions if 3270 stream is misinterpreted
	}
	
}
