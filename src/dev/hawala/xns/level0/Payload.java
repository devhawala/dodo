/*
Copyright (c) 2018, Dr. Hans-Walter Latz
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * The name of the author may not be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY EXPRESS
OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package dev.hawala.xns.level0;

/**
 * Implementation of a net packet payload, allowing to get or put
 * values in wire representation and create sub-payloads sharing the
 * common data buffer. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public abstract class Payload {

	// the raw payload buffer, possibly shared among instances of this class
	private final byte[] payload;
	
	// offset of the raw payload in 'payload' (used if this payload represents an internal
	// structure of an encapsulating packet, e.g. an Error packet containing an IDP packet)
	private final int pBase;
	
	// the remaining length in 'payload' available in this payload
	private int maxLength;
	
	// the current length of thie payload: 0..maxLength
	private int currLength;
	
	// the payload we are an wrapper of or null, if we are the 'most inside' payload
	protected final Payload basePayload;
	
	// our base in 'basePayload' if present
	private final int nominalBase;
	
	/**
	 * Construct a new payload.
	 * 
	 * @param size length of the payload buffer
	 */
	public Payload(int size) {
		if (size < 1) {
			throw new IllegalArgumentException("Invalid (negative) payload size");
		}
		this.payload = new byte[size];
		this.pBase = 0;
		this.nominalBase = 0;
		this.maxLength = size;
		this.currLength = size;
		this.basePayload = null;
	}
	
	/**
	 * Construct a sub-payload derived from 'basePayload' (i.e. sharing the payload buffer)
	 * and starting at the given offset and limited to the minimum of the given length or
	 * the original length less this offset. 
	 * 
	 * @param basePayload the existing payload this new payload will be a subset of.
	 * @param base the first byte position in 'basePayload' to be accessible from this new payload.
	 * @param visibleLength the maximum byte count accessible in this sub-payload (possibly
	 *     reduced if less bytes are available in the 'basePayload' starting from 'base').
	 */
	public Payload(Payload basePayload, int base, int visibleLength) {
		if (basePayload == null) {
			throw new IllegalArgumentException("Invalid null base-payload for sub-payload");
		}
		if (base < 0 || base >= (basePayload.payload.length - basePayload.pBase)) {
			throw new IllegalArgumentException("Invalid base for sub-payload");
		}
		if (visibleLength < 1) {
			visibleLength = 0;
		}
		this.payload = basePayload.payload;
		this.nominalBase = base;
		this.pBase = base + basePayload.pBase;
		this.maxLength = Math.min(visibleLength, basePayload.getMaxPayloadLength() - base);
		this.currLength = basePayload.getPayloadLength() - base;
		if (this.currLength < 0) {
			basePayload.setPayloadLength(base);
			this.currLength = 0;
		}
		this.basePayload = basePayload;
	}
	
	/**
	 * Construct a sub-payload derived from 'basePayload' (i.e. sharing the payload buffer)
	 * and starting at the given offset and smaller by this offset.
	 * 
	 * @param basePayload the existing payload this new payload will be a subset of.
	 * @param base the first byte position in 'basePayload' to be accessible from this new payload. 
	 */
	public Payload(Payload basePayload, int base) {
		this(basePayload, base, basePayload.maxLength);
	}
	
	/**
	 * Get the current payload length of this payload.
	 * 
	 * @return the current payload length.
	 */
	public int getPayloadLength() {
		return this.currLength;
	}
	
	/**
	 * Get the accessible length of the payload.
	 * 
	 * @return the length of the payload.
	 */
	public int getMaxPayloadLength() {
		return this.maxLength;
	}
	
	/**
	 * Set the new number of bytes accessing starting from the payload's
	 * base in the underlying buffer; the new accessible length can not
	 * be larger than the size of the underlying buffer less the base
	 * offset of this payload.
	 * 
	 * @param newLength the new maximal accessible length in the payload.
	 */
	public void setPayloadLength(int newLength) {
		this.currLength = Math.max(0,  Math.min(newLength, this.maxLength));
		if (this.basePayload != null) {
			this.basePayload.setPayloadLength(this.currLength + this.nominalBase);
		}
	}
	
	/**
	 * Copy the content of an other Payload, limited to the smallest length of
	 * both payloads.
	 * 
	 * @param other the 
	 * @return
	 */
	public int copy(Payload other, int count) {
		int copyLength = Math.min(this.maxLength, Math.min(count, other.currLength));
		System.arraycopy(other.payload, other.pBase, this.payload, this.pBase, copyLength);
		return copyLength;
	}
	
	/**
	 * Copy the content of an other Payload, limited to the smallest length of
	 * both payloads.
	 * 
	 * @param other the 
	 * @return
	 */
	public int copy(Payload other) {
		return this.copy(other,other.currLength);
	}
	
	/**
	 * Copy data from the payload specifying the source region in the payload and
	 * the region in the target, reducing the amount of data copied by reducing
	 * both regions to valid boundaries inside their respective byte arrays.
	 * 
	 * @param at the offset in the payload where to copy the data to.
	 * @param maxLength the maximum byte count to copy from the payload.
	 * @param target the target data to copy.
	 * @param offset the start of the region in {@code target} to copy to.
	 * @param length the length of the region in {@code target} to copy to.
	 * @return the number of bytes copied from the payload.
	 */
	public int rdBytes(int at, int maxLength, byte[] target, int offset, int length) {
		return this.copyBytes(true, at, maxLength, target, offset, length);
	}
	
	/**
	 * Copy data into the payload specifying the target region in the payload and
	 * the source region in the data, reducing the amount of data copied by reducing
	 * both regions to valid boundaries inside their respective byte arrays.
	 * 
	 * @param at the offset in the payload where to copy the data to.
	 * @param maxLength the maximum byte count to copy to the payload.
	 * @param source the source data to copy.
	 * @param offset the start of the region in {@code source} to copy from.
	 * @param length the length of the region in {@code source} to copy from.
	 * @return the number of bytes copied to the payload.
	 */
	public int wrBytes(int at, int maxLength, byte[] source, int offset, int length) {
		return this.copyBytes(false, at, maxLength, source, offset, length);
	}
	
	private int copyBytes(boolean payloadToBuffer, int at, int maxLength, byte[] buffer, int offset, int length) {
		if (offset < 0) {
			length += offset;
			offset = 0;
		}
		if ((offset + length) > buffer.length) {
			length = buffer.length - offset;
		}
		if (at < 0) {
			maxLength += at;
			at = 0; 
		}
		if ((at + maxLength) > this.maxLength) {
			maxLength = this.maxLength - this.pBase - at;
		}
		if (length < 1 || maxLength < 1) {
			// nothing to copy
			return 0;
		}
		int copyLength = Math.min(length, maxLength);
		if (payloadToBuffer) {
			System.arraycopy(this.payload, this.pBase, buffer, offset, copyLength);
		} else {
			System.arraycopy(buffer, offset, this.payload, this.pBase, copyLength);
		}
		return copyLength;
	}
	
	/**
	 * Get the byte at the given offset from the payload.
	 *  
	 * @param at the offset of the byte in the payload.
	 * @return the byte at the given position or {@code 0} if {@code offset}
	 *   if outside the valid range.
	 */
	public byte rdByte(int at) {
		if (at < 0 || at >= this.maxLength) { return (byte)0; }
		return this.payload[at + this.pBase];
	}
	
	/**
	 * Set the byte at the given offset of the payload. The payload
	 * will stay unchanged if {@code offset} is outside the valid range. 
	 *  
	 * @param at the offset of the byte in the payload.
	 * @param value the byte value to set.
	 */
	public void wrByte(int at, byte value) {
		if (at < 0 || at >= this.maxLength) { return; }
		this.payload[at + this.pBase] = value;
	}
	
	/**
	 * Get the unsigned 16 bit integer value from the payload at the
	 * specified position. Bytes outside the valid payload range will be considered
	 * to have the value {@code 0}. 
	 * 
	 * @param at the offset of the 16 bit quantity.
	 * @return the unsigned 16 bit integer value found at the specified payload position.
	 */
	public int rdCardinal(int at) {
		int result = 0;
		if (at >= 0 && at < this.maxLength) { result = (this.payload[at + this.pBase] & 0xFF) << 8; }
		at++;
		if (at >= 0 && at < this.maxLength) { result |= this.payload[at + this.pBase] & 0xFF; }
		return result;
	}
	
	/**
	 * Put an unsigned 16 bit integer value into the payload at the specified position.
	 * Bytes that would be written outside the valid payload range will be ignored. 
	 * 
	 * @param at the offset of the 16 bit quantity in the payload.
	 * @param value the integer quantity of which the lower 16 bits will be copied to
	 *   the payload at the given position.
	 */
	public void wrCardinal(int at, int value) {
		if (at >= 0 && at < this.maxLength) { this.payload[at + this.pBase] = (byte)((value >> 8) & 0xFF); }
		at++;
		if (at >= 0 && at < this.maxLength) { this.payload[at + this.pBase] = (byte)(value & 0xFF); }
	}
	
	/**
	 * Get the unsigned 32 bit integer value from the payload at the specified position.
	 * Bytes outside the valid payload range will be considered to have the value {@code 0}. 
	 * 
	 * @param at the offset of the 32 bit quantity in the payload.
	 * @return the unsigned 32 bit integer value found at the specified payload position.
	 */
	public long rdLongCardinal(int at) {
		long result = ((long)this.rdCardinal(at) << 16) | (long)this.rdCardinal(at+2);
		return result;
	}
	
	/**
	 * Put an unsigned 32 bit integer value into the payload at the specified position.
	 * Bytes that would be written outside the valid payload range will be ignored. 
	 * 
	 * @param at the offset of the 32 bit quantity in the payload.
	 * @param value the integer quantity of which the lower 32 bits will be copied to
	 *   the payload at the given position.
	 */
	public void wrLongCardinal(int at, long value) {
		this.wrCardinal(at,  (int)((value >> 16) & 0xFFFF));
		this.wrCardinal(at+2,  (int)(value & 0xFFFF));
	}
	
	/**
	 * Get the 48 bit host address value from the payload at the specified position.
	 * Bytes outside the valid payload range will be considered to have the value {@code 0}. 
	 * 
	 * @param at the offset of the 48 bit quantity in the payload.
	 * @return the 48 bit value found at the specified payload position.
	 */
	public long rdAddress(int at) {
		long result = ((long)this.rdCardinal(at) << 32) | ((long)this.rdCardinal(at+2) << 16) | ((long)this.rdCardinal(at+4));
		return result;
	}
	
	/**
	 * Put an 48 bit host address value into the payload at the specified position.
	 * Bytes that would be written outside the valid payload range will be ignored. 
	 * 
	 * @param at the offset of the 48 bit quantity in the payload.
	 * @param value the integer quantity of which the lower 48 bits will be copied to
	 *   the payload at the given position.
	 */
	public void wrAddress(int at, long value) {
		this.wrCardinal(at,  (int)((value >> 32) & 0xFFFF));
		this.wrCardinal(at+2,  (int)((value >> 16) & 0xFFFF));
		this.wrCardinal(at+4,  (int)(value & 0xFFFF));
	}
	
	public String payloadToString() {
		StringBuilder sb = new StringBuilder().append("(").append(this.currLength).append("/").append(this.maxLength).append(")[");
		String sep = "";
		for (int i = 0; i < this.currLength; i++) {
			String hex = String.format("%s%02X", sep, this.payload[this.pBase + i]);
			sb.append(hex);
			sep = " ";
		}
		return sb.append("]").toString();
	}
	
	/**
	 * @return unique local identification of the packet for tracing 
	 */
	public abstract long getPacketId();
	
}
