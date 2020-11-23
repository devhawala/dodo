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

import java.util.function.Supplier;

/**
 * Implementation of the wire reading functionality used by Courier
 * deserialization from a SEQUENCE OF UNSPECIFIED.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WireSeqOfUnspecifiedReader implements iWireStream {

	private final SEQUENCE<UNSPECIFIED> data;
	
	private int rdPos = 0;
	private int rdTemp;
	private boolean rdPadByte = false;
	
	public WireSeqOfUnspecifiedReader(SEQUENCE<UNSPECIFIED> data) {
		this.data = data;
	}
	
	public WireSeqOfUnspecifiedReader(int[] data) {
		this.data = new SEQUENCE<UNSPECIFIED>(UNSPECIFIED::make);
		for (int i = 0; i < data.length; i++) {
			this.data.add().set(data[i]);
		}
	}
	
	public WireSeqOfUnspecifiedReader(int size, Supplier<Short> source) {
		this.data = new SEQUENCE<UNSPECIFIED>(UNSPECIFIED::make);
		for (int i = 0; i < size; i++) {
			this.data.add().set(source.get() & 0xFFFF);
		}
	}
	
	private int get() throws EndOfMessageException {
		if (rdPos >= this.data.size()) {
			throw new EndOfMessageException();
		}
		this.rdPadByte = false;
		return this.data.get(this.rdPos++).get();
	}


	@Override
	public long readI48() throws EndOfMessageException {
		long value 
				= ((long)this.get() << 32)
				| ((long)this.get() << 16)
				| (long)this.get();	
		return value;
	}

	@Override
	public int readI32() throws EndOfMessageException {
		int value 
				= (this.get() << 16)
				| this.get();
		return value;
	}

	@Override
	public int readI16() throws EndOfMessageException {
		int value = this.get();
		return (value > 0x7FFF) ? value | 0xFFFF0000 : value;
	}

	@Override
	public short readS16() throws EndOfMessageException {
		return (short)this.readI16();
	}

	@Override
	public int readI8() throws EndOfMessageException {
		final int value;
		if (this.rdPadByte) {
			this.rdPadByte = false;
			value = this.rdTemp & 0x00FF;
		} else {
			this.rdTemp = this.get();
			this.rdPadByte = true;
			value = (this.rdTemp >>> 8) & 0x00FF;
		}
		return (value > 0x007F) ? value | 0xFFFFFF00 : value;
	}

	@Override
	public short readS8() throws EndOfMessageException {
		return (short)this.readI8();
	}

	@Override
	public boolean isAtEnd() {
		return (rdPos >= this.data.size());
	}

	@Override
	public void dropToEOM(byte reqDatastreamType) {
		this.rdPos = this.data.size();
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		// ignored
	}

	@Override
	public byte getStreamType() {
		return (byte)0;
	}

	@Override
	public void resetReadingToWordBoundary() {
		this.rdPadByte = false;
	}
	
	/*
	 * write methods not allowed on a SEQUENCE<UNSPECIFIED> reader
	 */

	@Override
	public void writeI48(long value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeI32(int value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeI16(int value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeS16(short value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeI8(int value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeS8(short value) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void writeEOM() throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		throw new NoMoreWriteSpaceException();
	}

	@Override
	public void resetWritingToWordBoundary() {
		// irrelevant
	}

	@Override
	public Long getPeerHostId() {
		// no remote host connected...
		return null;
	}
	
	@Override
	public void sendAbort() {
		// ignored....
	}
}
