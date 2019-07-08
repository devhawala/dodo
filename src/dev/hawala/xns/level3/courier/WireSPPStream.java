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

import dev.hawala.xns.Log;
import dev.hawala.xns.SppAttention;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iSppInputStream;
import dev.hawala.xns.iSppInputStream.iSppReadResult;
import dev.hawala.xns.iSppOutputStream;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Implementation of the wire functionality used by Courier (de)serialization
 * on an SPP stream. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WireSPPStream extends WireBaseStream {
	
	private final iSppInputStream sppIn;
	private final iSppOutputStream sppOut;
	
	private final byte[] inBuf = new byte[SPP.SPP_MAX_PAYLOAD_LENGTH];
	private int inIdx = 0;
	private int inMax = 0;
	private byte inSst = 0;
	private boolean inEomPending = false;
	
	private final byte[] outBuf = new byte[SPP.SPP_MAX_PAYLOAD_LENGTH];
	// TODO Auto-generated me
	private int outIdx = 0;
	private int outMax = SPP.SPP_MAX_PAYLOAD_LENGTH;
	private byte outSst = 0;
	
	public WireSPPStream(iSppInputStream in, iSppOutputStream out) {
		if (in == null && out == null) {
			throw new IllegalArgumentException("At most one stream may be null");
		}
		this.sppIn = in;
		this.sppOut = out;
	}
	
	/*
	 * write operations
	 */

	private void writeBuf(boolean isEom) throws NoMoreWriteSpaceException {
		try {
			this.sppOut.write(this.outBuf, 0, this.outIdx, this.outSst, isEom);
			this.outIdx = 0;
			if (this.sppOut.checkForInterrupt() != null) {
				throw new NoMoreWriteSpaceException();
			}
		} catch (XnsException | InterruptedException e) {
			Log.L4.printf(null, "** error %s in WireSPPStream.writeBuf(): %s\n%s\n", 
					e.getClass().getName(),
					e.getMessage(),
					e.getStackTrace().toString());
			throw new NoMoreWriteSpaceException();
		}
	}

	@Override
	public void writeEOM() throws NoMoreWriteSpaceException {
		// enforce word boundaries in RPC mode, but allow odd bytes-count on bulk-data transmissions
		if (this.outSst == Constants.SPPSST_RPC) {
			this.writePad(); // last write was a single byte at upper nibble position?
		}
		this.writeBuf(true);
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		this.writePad();
		this.writeBuf(false);
	}

	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		// enforce word boundaries in RPC mode, but allow odd bytes-count on bulk-data transmissions
		if (this.outSst == Constants.SPPSST_RPC) {
			this.writePad(); // last write was a single byte at upper nibble position?
		}
		if (this.outSst == datastreamType) {
			// no-op as in Pilot
			return;
		}
		if (this.outIdx > 0) {
			// handle a SST change explicitely as end-of-message (implicit in Pilot-stream)
			this.writeBuf(true);
		}
		this.outSst = datastreamType;
	}

	@Override
	protected void putByte(int b) throws NoMoreWriteSpaceException {
		this.outBuf[this.outIdx++] = (byte)b;
		if (this.outIdx >= this.outMax) {
			this.writeBuf(false);
		}
	}
	
	/*
	 * read operations
	 */

	@Override
	public boolean isAtEnd() {
		if (this.inIdx < this.inMax || !this.inEomPending) {
			return false;
		}
		this.inEomPending = false;
		return true;
	}

	@Override
	public byte getStreamType() {
		// if necessary read the next packet to get the SST
		if (this.inIdx >= this.inMax) {
			try {
				this.readNextPacket();
			} catch (EndOfMessageException e) {
				this.inEomPending = true;
			}
		}
		return this.inSst;
	}

	@Override
	protected int getByte() throws EndOfMessageException {
		if (this.isAtEnd()) {
			throw new EndOfMessageException();
		}
		while (this.inIdx >= this.inMax) {
			this.readNextPacket();
		}
		return this.inBuf[this.inIdx++] & 0x00FF;
	}
	
	private void readNextPacket() throws EndOfMessageException {
		try {
			iSppReadResult res = this.sppIn.read(this.inBuf, 0, this.inBuf.length);
			
			if (res == null) {
				// connection was closed
				Log.L4.printf(null, "** connection closed in WireSPPStream.getByte()\n");
				throw new EndOfMessageException();
			}
			
			if (res.isAttention()) {
				// WHAT?
				// does Courier transport protocol allow attentions (and if so: why) ??
				res.getAttentionByte();
				throw new EndOfMessageException(); // handle interrupt as EOM for now
			}
			
			this.inSst = res.getDatastreamType();
			
			if (res.getLength() == 0 && res.isEndOfMessage()) {
				// attempt to read beyond the Courier message => (de-)serialization error !!
				this.inMax = 0;
				this.inIdx = 0;
				throw new EndOfMessageException();
			}

			this.inEomPending = res.isEndOfMessage();
			if (res.getLength() > 0) {
				this.inMax = res.getLength();
				this.inIdx = 0;
			}
		} catch (XnsException | SppAttention | InterruptedException e) {
			Log.L4.printf(null, "** error %s in WireSPPStream.getByte(): %s\n%s", 
					e.getClass().getName(),
					e.getMessage(),
					e.getStackTrace().toString());
			throw new EndOfMessageException();
		}
	}
	
	@Override
	public Long getPeerHostId() {
		return this.sppIn.getRemoteHost();
	}

	@Override
	public void sendAbort() {
		try {
			this.sppOut.sendAttention((byte)1);
		} catch (XnsException | InterruptedException e) {
			Log.L4.printf(null, "** error %s in WireSPPStream.sendAttention(1): %s\n", 
					e.getClass().getName(),
					e.getMessage());
		}
	}

}
