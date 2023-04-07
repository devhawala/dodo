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

package dev.hawala.vm370.ebcdic;

/**
 * Implementation of a mutable EBCDIC string-like buffer, allowing to
 * append EBCDIC- or (Java-)Unicode-strings as well as conversion to
 * Unicode and some string-functionality.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
 */
public class EbcdicHandler {

	private final int maxLength;
	
	private byte[] buffer;
	private int currLength = 0;
	
	private String isoEquiv = null;
	
	/**
	 * Construct the EBCDIC string for a maximal length of 8192 bytes.
	 */
	public EbcdicHandler() {
		this(8192);
	}
	
	/**
	 * Construct the EBCDIC string for the given maximal length.
	 * @param maxLength the maximal length for the EBCDIC string.
	 */
	public EbcdicHandler(int maxLength) {
		this.maxLength = (maxLength < 16) ? 16 : maxLength;
		this.buffer = new byte[this.maxLength];
	}
	
	/**
	 * Construct the EBCDIC string, initializing it with the given
	 * string and setting the maximal length to the string's length.
	 * @param src the string to use for initialization.
	 */
	public EbcdicHandler(String src) {
		this(src.length());
		this.appendUnicode(src);
	}
	
	/**
	 * Clear the current value of the EBCDIC string.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler reset() {
		this.currLength = 0;
		this.isoEquiv = null;
		return this;
	}
	
	/**
	 * Get the current length of the EBCDIC string.
	 * @return the string length.
	 */
	public int getLength() {
		return this.currLength;
	}
	
	/**
	 * Get the internal byte buffer containing the EBCDIC string, but
	 * not bounded to this string.  
	 * @return the raw internal byte buffer.
	 */
	public byte[] getRawBytes() {
		return this.buffer;
	}
	
	/**
	 * Get a copy of the EBCDIC bytes array.
	 * @return a new byte array containing the EBCDI string.
	 */
	public byte[] getCopy() {
		byte[] copy = new byte[this.currLength];
		System.arraycopy(this.buffer, 0, copy, 0, this.currLength);
		return copy;
	}
	
	/**
	 * Append another EBCDIC string to this instance. 
	 * @param other the other ENCDIC string to append.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler append(EbcdicHandler other) {
		int othersLength = other.getLength();
		byte[] othersBytes = other.getRawBytes();
		this.appendEbcdic(othersBytes, 0, othersLength);
		return this;
	}
	
	/**
	 * Append an EBCDIC chararacter to this instance.
	 * @param b the EBCDIC character to append.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler appendEbcdicChar(byte b) {
		if (this.currLength >= this.maxLength) { return this; }
		this.buffer[this.currLength++] = b;
		this.isoEquiv = null;
		return this;
	}
	
	/**
	 * Append a byte sequence to this instance.
	 * @param fromBytes the byte buffer where to take the EBCDIC characters to append.
	 * @param offset the offset of the first character to append.
	 * @param length the length of the byte sequence to append.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler appendEbcdic(byte[] fromBytes, int offset, int length) {
		if (offset < 0) { offset = 0; }
		if (offset >= fromBytes.length) { return this; }
		length = Math.min(fromBytes.length - offset, length);
		while(this.currLength < this.maxLength && length > 0) {
			this.buffer[this.currLength++] = fromBytes[offset++];
			length--;
		}
		this.isoEquiv = null;
		return this;
	}
	
	/**
	 * Append a Java (unicode) string to this instance.
	 * @param unicodeString the string to append.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler appendUnicode(String unicodeString) {
		byte[] ebcdicChars = Ebcdic.toEbcdic(unicodeString);
		this.appendEbcdic(ebcdicChars, 0, ebcdicChars.length);
		this.isoEquiv = null;
		return this;
	}
	
	/**
	 * Append the EBCDIC string in this instance to a byte buffer.
	 * @param toBuffer the byte buffer to append to.
	 * @param atOffset the offset position in the buffer where start appending.
	 * @return the offset of the first byte in the buffer following the appended EBCDIC string.
	 */
	public int addTo(byte[] toBuffer, int atOffset) {
		int lastAllowed = toBuffer.length - 1;
		if (atOffset < 0) { atOffset = 0; }
		for (int i = 0; i < this.currLength && atOffset < lastAllowed; i++, atOffset++) {
			toBuffer[atOffset] = this.buffer[i];
		}
		return atOffset;
	}
	
	/**
	 * Get the EBCDIC string in this instance converted to a Java (unicode) string.
	 * @return the equivalent Java string for the EBCDIC string.
	 */
	public String getString() {
		if (this.currLength == 0) { return ""; }
		if (this.isoEquiv != null) { return this.isoEquiv; }
		this.isoEquiv = Ebcdic.toAscii(this.buffer, 0, this.currLength);
		return this.isoEquiv;
	}
	
	@Override
	public String toString() {
		return this.getString();
	}

	/**
	 * Case-sensitive compare of this EBCDIC string with a EBCDIC byte sequence.   
	 * @param othersBytes the byte buffer containing the other EBCDIC string.
	 * @param othersOffset the start offset of the other string in the buffer.
	 * @param othersLength the length of the other string in the buffer.
	 * @return <code>true</code> if the 2 strings are (case-sensitive) same.
	 */
	public boolean eq(byte[] othersBytes, int othersOffset, int othersLength) {
		if (this.currLength == 0 && othersLength == 0) { return true; }
		if (this.currLength != othersLength) { return false; }
		for (int i = 0; i < othersLength; i++) {
		  if (othersBytes[i + othersOffset] != this.buffer[i]) { return false; }
		}
		return true;
	}
	
	/**
	 * Case-sensitive compare of this EBCDIC string with another EBCDIC string.   
	 * @param other the other string to compare with.
	 * @return <code>true</code> if the 2 strings are (case-sensitive) same.
	 */
	public boolean eq(EbcdicHandler other) {
		int othersLength = other.getLength();
		byte[] othersBytes = other.getRawBytes();
		return this.eq(othersBytes, 0, othersLength);
	}
	
	/**
	 * Check if the other EBCDIC byte sequence starts with the string of this instance. 
	 * @param othersBytes the byte buffer containing the EBDDIC string to check.
	 * @param othersOffset the byte position where to start the comparison 
	 * @param othersLength the length of the other string.
	 * @return <code>true</code> if the other string starts with the EBCDIC string of this instance.
	 */
	public boolean startsWith(byte[] othersBytes, int othersOffset, int othersLength) {
		if (this.currLength == 0 && othersLength == 0) { return true; }
		if (this.currLength < othersLength) { return false; }
		for (int i = 0; i < othersLength; i++) {
		  if (othersBytes[i + othersOffset] != this.buffer[i]) { return false; }
		}
		return true;
	}
	
	/**
	 * Check if the other EBCDIC string starts with the string of this instance. 
	 * @param other the other string to check.
	 * @return <code>true</code> if the other string starts with the EBCDIC string of this instance.
	 */
	public boolean startsWith(EbcdicHandler other) {
		int othersLength = other.getLength();
		byte[] othersBytes = other.getRawBytes();
		return this.startsWith(othersBytes, 0, othersLength);
	}
	
	/**
	 * Remove trailing blanks from this EBCDIC string.
	 * @return this instance for function call chaining.
	 */
	public EbcdicHandler strip() {
		if (this.currLength == 0) { return this; }
		int pos = this.currLength - 1;
		while(pos >= 0 && this.buffer[pos] == Ebcdic._Blank) {
			pos--;
			this.currLength--;
			this.isoEquiv = null;
		}
		return this;
	}
	
	/**
	 * Case-insensitive compare of this EBCDIC string with another EBCDIC string.   
	 * @param other the other string to compare with.
	 * @return <code>true</code> if the 2 strings are (case-insensitive) same.
	 */
	public boolean ncaseEq(EbcdicHandler other) {
		int othersLength = other.getLength();
		if (this.currLength == 0 && othersLength == 0) { return true; }
		if (this.currLength != othersLength) { return false; }
		byte[] othersBytes = other.getRawBytes();
		for (int i = 0; i < othersLength; i++) {
		  if (Ebcdic.uppercase(othersBytes[i]) != Ebcdic.uppercase(this.buffer[i])) { return false; }
		}
		return true;
	}
}
