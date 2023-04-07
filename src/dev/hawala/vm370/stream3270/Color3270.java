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
 * Definition of the standard colors of a 3279 color terminal.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public enum Color3270 {
	
	/** Default color on a 3279 (green). */
	Default((byte)0x00),
	
	/** Blue. */
	Blue((byte)0xF1),
	
	/** Red. */
	Red((byte)0xF2),
	
	/** Pink. */
	Pink((byte)0xF3),
	
	/** Green. */
	Green((byte)0xF4),
	
	/** Turquoise. */
	Turquoise((byte)0xF5),
	
	/** Yellow. */
	Yellow((byte)0xF6),
	
	/** White. */
	White((byte)0xF7);
	
	private final byte code;
	
	Color3270(byte code) {
		this.code = code;
	}
	
	/**
	 * Get the byte encoding the color in a 3270 output stream.
	 * @return the byte for the color.
	 */
	public byte getCode() { return this.code; }
}
