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

import java.util.Hashtable;

/**
 * Definition of the order tokens for a 3270 output data streams.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public enum OrderCode3270 {
	
	/** Start Field order. */
	SF((byte)0x1D),
	
	/** Set Buffer Address order. */
	SBA((byte)0x11),
	
	/** Insert Cursor order. */
	IC((byte)0x13),
	
	/** Program Tab order. */
	PT((byte)0x05),
	
	/** Repeat to Address order. */
	RA((byte)0x3C),	
	
	/** Erase Unprotected to Address order. */
	EUA((byte)0x12),
	
	/** Start Field Extended order. */
	SFE((byte)0x29),
	
	/** Modify Field order. */
	MF((byte)0x2C),	
	
	/** Set Attribute order. */
	SA((byte)0x28),
	
	/** Fallback for map(). */
	Unknown((byte)0x00);
	
	private static Hashtable<Byte,OrderCode3270> codesMap = null;
	
	private final byte code;
	
	OrderCode3270(byte code) {
		this.code = code;
	}
	
	/**
	 * Get the byte code for the order.
	 * @return the byte encoding the command.
	 */
	public byte getCode() { return this.code; }
	
	/**
	 * Get the enum value for the byte.
	 * @param code the byte to check.
	 * @return the enum value for the byte or <code>Unknown</code> if the byte does
	 * not represent a 3270 order.
	 */
	public static OrderCode3270 map(byte code) {
		if (codesMap == null) {
			codesMap = new Hashtable<Byte,OrderCode3270>();
			for(OrderCode3270 zeCode : OrderCode3270.values()) {
				codesMap.put(zeCode.getCode(), zeCode);
			}
		}
		if (codesMap.containsKey(code)) {
			return codesMap.get(code);
		}
		return OrderCode3270.Unknown;
	}
}
