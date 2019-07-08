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
 * Simple wire writer for serializing an Courier object into a byte
 * or word array.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WireWriter extends WireBaseStream {
	
	private byte[] bytes = new byte[1024];
	private int currPos = 0;
	private boolean done = false;
	
	/**
	 * @return the serialized object as byte array
	 */
	public byte[] getBytes() {
		this.done = true;
		byte[] result = new byte[this.currPos];
		System.arraycopy(this.bytes, 0, result, 0, this.currPos);
		return result;
	}
	
	/**
	 * @return the serialized object as word array
	 */
	public int[] getWords() {
		this.done = true;
		int[] result = new int[(this.currPos + 1) / 2];
		int b = 0;
		for (int i = 0; i < result.length; i++) {
			int hi = (this.bytes[b++] & 0xFF) << 8;
			int lo = this.bytes[b++] & 0xFF;
			result[i] = hi | lo;
		}
		return result;
	}

	/*
	 * write operations
	 */

	@Override
	protected void putByte(int b) throws NoMoreWriteSpaceException {
		if (this.done) {
			throw new NoMoreWriteSpaceException();
		}
		if (this.currPos >= this.bytes.length) {
			byte[] newBytes = new byte[this.bytes.length + 1024];
			System.arraycopy(this.bytes, 0, newBytes, 0, this.bytes.length);
			this.bytes = newBytes;
		}
		this.bytes[this.currPos++] = (byte)b;
	}
	
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		// ignored
	}


	@Override
	public void writeEOM() throws NoMoreWriteSpaceException {
		this.done = true;
	}


	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		// ignored
	}

	
	/*
	 * read operations (unsupported here)
	 */

	@Override
	public boolean isAtEnd() {
		return true;
	}

	@Override
	public byte getStreamType() {
		return 0; // default SST n the SPP world
	}

	@Override
	protected int getByte() throws EndOfMessageException {
		throw new EndOfMessageException();
	}
}
