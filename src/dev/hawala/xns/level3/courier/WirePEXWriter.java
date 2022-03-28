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

import dev.hawala.xns.iPexResponder.ResponseSender;
import dev.hawala.xns.level2.PEX;

/**
 * Implementation of the wire writing functionality used by Courier
 * serialization to a single PEX packet.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WirePEXWriter extends WireBaseStream {
	
	private final byte[] buffer = new byte[PEX.PEX_MAX_PAYLOAD_SIZE];
	
	private int currIdx = 0;
	
	private PEX pex = null;
	
	public PEX closeAndGetPacket() {
		this.writeEOM();
		return this.pex;
	}
	
	public void sendAsResponse(ResponseSender responseSender) {
		this.writeEOM();
		responseSender.sendResponse(this.buffer, 0, currIdx);
	}

	@Override
	public void writeEOM() {
		if (this.pex != null) { return; }
		this.pex = new PEX(this.buffer, 0, this.currIdx);
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		// ignored, as there is no "intermediate packet" in single-packet-in-and-out Courier
	}

	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		// ignored 
	}

	@Override
	protected void putByte(int b) throws NoMoreWriteSpaceException {
		if (this.pex != null || this.currIdx >= PEX.PEX_MAX_PAYLOAD_SIZE) {
			throw new NoMoreWriteSpaceException();
		}
		this.buffer[this.currIdx++] = (byte)b;
	}
	
	/*
	 * read operations (unsupported here)
	 */

	@Override
	public boolean isAtEnd() {
		return true;
	}
	
	@Override
	public boolean checkIfAtEnd() {
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
