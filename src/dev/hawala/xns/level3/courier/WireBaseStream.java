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

package dev.hawala.xns.level3.courier;

/**
 * Base implementation of the wire functionality used by Courier (de)serialization.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class WireBaseStream implements iWireStream {
	
	/*
	 * to be implemented by subclasses
	 */
	
	/*
	 * read an int [0..255]
	 */
	protected abstract int getByte() throws EndOfMessageException;
	
	/*
	 * write an int [0..255]
	 */
	protected abstract void putByte(int b) throws NoMoreWriteSpaceException;
	
	/*
	 * internal handling of byte vs. word boundaries 
	 */
	
	private boolean wrPadByte = false;
	protected void writePad() throws NoMoreWriteSpaceException {
		if (this.wrPadByte) {
			this.putByte((byte)0);
			this.wrPadByte = false;
		}
	}
	
	@Override
	public void resetWritingToWordBoundary() {
		this.wrPadByte = false;
	}
	
	private boolean rdPadByte = false;
	protected void readPad() throws EndOfMessageException {
		if (this.rdPadByte) {
			this.getByte();
			this.rdPadByte = false;
		}
	}
	
	@Override
	public void resetReadingToWordBoundary() {
		this.rdPadByte = false;
	}
	
	/*
	 * de-serializing
	 */

	@Override
	public void writeI48(long value) throws NoMoreWriteSpaceException {
		this.writePad();
		this.putByte((int)((value >> 40) & 0x00FFL));
		this.putByte((int)((value >> 32) & 0x00FFL));
		this.putByte((int)((value >> 24) & 0x00FFL));
		this.putByte((int)((value >> 16) & 0x00FFL));
		this.putByte((int)((value >> 8) & 0x00FFL));
		this.putByte((int)(value & 0x00FFL)); 
	}

	@Override
	public void writeI32(int value) throws NoMoreWriteSpaceException {
		this.writePad();
		this.putByte((int)(value >> 24) & 0x00FF);
		this.putByte((int)(value >> 16) & 0x00FF);
		this.putByte((int)(value >> 8) & 0x00FF);
		this.putByte((int)value & 0x00FF);
	}

	@Override
	public void writeI16(int value) throws NoMoreWriteSpaceException {
		this.writePad();
		this.putByte((int)(value >> 8) & 0x00FF);
		this.putByte((int)value & 0x00FF);
	}

	@Override
	public void writeS16(short value) throws NoMoreWriteSpaceException {
		this.writePad();
		this.putByte((int)(value >> 8) & 0x00FF);
		this.putByte((int)value & 0x00FF);
	}

	@Override
	public void writeI8(int value) throws NoMoreWriteSpaceException {
		this.putByte((int)value & 0x00FF);
		this.wrPadByte = !this.wrPadByte;
	}

	@Override
	public void writeS8(short value) throws NoMoreWriteSpaceException {
		this.putByte((int)value & 0x00FF);
		this.wrPadByte = !this.wrPadByte;
	}

	
	/*
	 * serializing
	 */

	@Override
	public long readI48() throws EndOfMessageException {
		this.readPad();
		long value
				= (long)this.getByte() << 40
				| (long)this.getByte() << 32
				| (long)this.getByte() << 24
				| (long)this.getByte() << 16
				| (long)this.getByte() << 8
				| (long)this.getByte();
		return value;
	}

	@Override
	public int readI32() throws EndOfMessageException {
		this.readPad();
		int value
				= this.getByte() << 24
				| this.getByte() << 16
				| this.getByte() << 8
				| this.getByte();
		return value;
	}

	@Override
	public int readI16() throws EndOfMessageException {
		this.readPad();
		int value
				= this.getByte() << 8
				| this.getByte();
		return (value > 0x7FFF) ? value | 0xFFFF0000 : value;
	}

	@Override
	public short readS16() throws EndOfMessageException {
		return (short)this.readI16();
	}

	@Override
	public int readI8() throws EndOfMessageException {
		int value = this.getByte();
		this.rdPadByte = !this.rdPadByte;
		return (value > 0x007F) ? value | 0xFFFFFF00 : value;
	}

	@Override
	public short readS8() throws EndOfMessageException {
		return (short)this.readI8();
	}

	@Override
	public void dropToEOM(byte reqDatastreamType) throws EndOfMessageException {
		while(!this.isAtEnd()) {
			this.getByte();
		}
	}
	
	@Override
	public Long getPeerHostId() {
		return null;
	}
	
	@Override
	public void sendAbort() {
		// ignored....
	}
	
}
