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
 * Definition of the extended highlighting of a 3270 terminal supporting
 * extended 3270 data streams.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public enum ExtHighlight3270 {
	
	/** No highlighting (reset). */
	Default((byte)0x00),
	
	/** Blink highlighting. */
	Blink((byte)0xF1),
	
	/** Reverse video highlighting. */ 
	Reverse((byte)0xF2),
	
	/** Underscored highlighting. */
	Underscore((byte)0xF4);
	
	private final byte code;
	
	ExtHighlight3270(byte code) {
		this.code = code;
	}
	
	/**
	 * Get the 3270 byte code for the extended highlighting.
	 * @return the byte code for the enum value.
	 */
	public byte getCode() { return this.code; }
}
