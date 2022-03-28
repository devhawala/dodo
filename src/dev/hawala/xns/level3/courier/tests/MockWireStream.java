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

package dev.hawala.xns.level3.courier.tests;

import dev.hawala.xns.level3.courier.iWireStream;

/**
 * WireStream used for tests.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class MockWireStream implements iWireStream {
	
	public void dumpWritten() {
		System.out.println("MockWireStream content:");
		int i = 0;
		while (i < this.wrPos) {
			int w = this.content[i++];
			if (w == EOM) {
				System.out.println("  EOM");
			} else if (w == NEW_SST) {
				int sst = this.content[i++];
				System.out.printf("  new SST(%d)\n", sst);
			} else {
				System.out.printf("  0x %02X %02X\n", (w >> 8) & 0xFF, w & 0xFF);
			}
		}
	}
	
	private static final int EOM = 0xFFFFFFFF;
	private static final int NEW_SST = 0xFEFEFEFE; // next entry has the SST byte 
	
	private int[] content = new int[512];
	
	private int rdPos = 0;
	private int wrPos = 0;
	
	private int rdSst = 0;
	private int wrSst = 0;
	
	private int rdTemp;
	private boolean rdPadByte = false;
	
	private int wrTemp;
	private boolean wrPadByte = false;
	
	private void put(int i) {
		if (this.wrPadByte) {
			this.wrPadByte = false;
			this.put(this.wrTemp);
		}
		if (this.wrPos == this.content.length) {
			int[] tmp = new int[this.content.length + 512];
			System.arraycopy(this.content, 0, this.content.length, 0, this.content.length);
			this.content = tmp;
		}
		this.content[this.wrPos++] = i;
	}

	@Override
	public void resetWritingToWordBoundary() {
		this.wrPadByte = false;
	}
	
	private int get() throws EndOfMessageException {
		if (this.rdPos > this.wrPos) { throw new EndOfMessageException(); }
		int next = this.content[this.rdPos++];
		this.rdPadByte = false;
		if (next == EOM) { throw new EndOfMessageException(); }
		if (next != NEW_SST) { return next & 0x0000FFFF; }
		this.rdSst = this.get() & 0x00FF;
		return this.get();
	}

	@Override
	public void resetReadingToWordBoundary() {
		this.rdPadByte = false;
	}

	@Override
	public void writeI48(long value) {
		this.put((int)((value >>> 32) & 0x0000FFFFL));
		this.put((int)((value >>> 16) & 0x0000FFFFL));
		this.put((int)(value & 0xFFFF));
	}

	@Override
	public void writeI32(int value) {
		this.put((int)((value >>> 16) & 0x0000FFFF));
		this.put((int)(value & 0xFFFF));
	}

	@Override
	public void writeI16(int value) {
		this.put((int)(value & 0x0000FFFF));
	}

	@Override
	public void writeS16(short value) {
		this.put((int)(value & 0x0000FFFF));
	}

	@Override
	public void writeI8(int value) {
		if (this.wrPadByte) {
			this.wrTemp |= value & 0x00FF;
			this.wrPadByte = false;
			this.put(this.wrTemp);
		} else {
			this.wrTemp = (value << 8) & 0x0000FF00;
			this.wrPadByte = true;
		}
		
	}

	@Override
	public void writeS8(short value) {
		this.writeI8(value & 0x00FF);
	}

	@Override
	public void writeEOM() {
		this.put(EOM);
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		// not relevant
	}

	@Override
	public void beginStreamType(byte datastreamType) {
		if (this.wrPos != 0 && this.content[this.wrPos - 1] != EOM) {
			this.put(EOM);
		}
		this.put(NEW_SST);
		this.put((int)(datastreamType & 0x00FF));
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
		if (this.rdPos >= this.wrPos) { return true; }
		if (this.content[this.rdPos] == EOM) {
			this.rdPos++;
			return true;
		}
		return false;
	}
	
	@Override
	public boolean checkIfAtEnd() {
		if (this.rdPos >= this.wrPos) { return true; }
		if (this.content[this.rdPos] == EOM) {
			return true;
		}
		return false;
	}

	@Override
	public void dropToEOM(byte reqDatastreamType) {
		while(!this.isAtEnd() && this.getStreamType() == reqDatastreamType) {
			try {
				this.get();
			} catch (EndOfMessageException e) {
				break; // as EOM was explicitely checked, this exception means no more data
			}
		}
	}

	@Override
	public byte getStreamType() {
		if (this.rdPos < (this.wrPos - 2)) {
			if (this.content[this.rdPos] == NEW_SST) {
				this.rdPos++;
				this.rdSst = this.content[this.rdPos++];
				return (byte)(this.rdSst & 0x00FF);
			}
		}
		return (byte)(this.rdSst & 0x00FF);
	}

	@Override
	public Long getPeerHostId() {
		// no remote connection...
		return null;
	}
	
	@Override
	public void sendAbort() {
		// ignored....
	}

}
