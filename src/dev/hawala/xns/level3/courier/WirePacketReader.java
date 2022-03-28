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
 * Implementation of the wire reading functionality used by Courier
 * deserialization from a single packet (e.g. broadcast packets).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WirePacketReader extends WireBaseStream {
	
	private final byte[] buffer;
	
	private final int limitIdx;
	
	private int currIdx = 0;
	
	public WirePacketReader(byte[] buf, int startPos, int bufLength) {
		this.buffer = buf;
		this.limitIdx = bufLength;
		this.currIdx = startPos;
	}
	
	public WirePacketReader(byte[] buf) {
		this(buf, 0, buf.length);
	}

	@Override
	public boolean isAtEnd() {
		return (this.currIdx >= this.limitIdx);
	}
	
	@Override
	public boolean checkIfAtEnd() {
		return (this.currIdx >= this.limitIdx);
	}

	@Override
	public byte getStreamType() {
		return 0; // this is the default SST in the SPP world 
	}

	@Override
	protected int getByte() throws EndOfMessageException {
		if (this.isAtEnd()) {
			throw new EndOfMessageException();
		}
		return this.buffer[this.currIdx++] & 0x00FF;
	}
	
	/*
	 * write operations (unsupported here)
	 */

	@Override
	public void writeEOM() throws NoMoreWriteSpaceException {
		// call of write method on reader?
		throw new NoMoreWriteSpaceException();
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		// ignored
	}

	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		// call of write method on reader?
		throw new NoMoreWriteSpaceException();
	}

	@Override
	protected void putByte(int b) throws NoMoreWriteSpaceException {
		// call of write method on reader?
		throw new NoMoreWriteSpaceException();
	}

}
