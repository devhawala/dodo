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
 * Implementation of the wire functionality used by Courier (de)serialization,
 * reading on a single packet (e.g. from a broadcast) and creating a new PEX
 * packet for the response. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class WirePEXRequestResponse implements iWireStream {
	
	private final WirePEXWriter wrPex;
	private final WirePacketReader rdPex;
	
	public WirePEXRequestResponse(byte[] buf, int startPos, int bufLength) {
		this.rdPex = new WirePacketReader(buf, startPos, bufLength);
		this.wrPex = new WirePEXWriter();
	}
	
	public WirePEXRequestResponse(byte[] buf) {
		this(buf, 0, buf.length);
	}
	
	public PEX closeAndGetPacket() {
		return this.wrPex.closeAndGetPacket();
	}
	
	public void sendAsResponse(ResponseSender responseSender) {
		this.wrPex.sendAsResponse(responseSender);
	}

	@Override
	public void writeI48(long value) throws NoMoreWriteSpaceException {
		this.wrPex.writeI48(value);
	}

	@Override
	public void writeI32(int value) throws NoMoreWriteSpaceException {
		this.wrPex.writeI32(value);
	}

	@Override
	public void writeI16(int value) throws NoMoreWriteSpaceException {
		this.wrPex.writeI16(value);
	}

	@Override
	public void writeS16(short value) throws NoMoreWriteSpaceException {
		this.wrPex.writeS16(value);
	}

	@Override
	public void writeI8(int value) throws NoMoreWriteSpaceException {
		this.wrPex.writeI8(value);
	}

	@Override
	public void writeS8(short value) throws NoMoreWriteSpaceException {
		this.wrPex.writeS8(value);
	}

	@Override
	public void writeEOM() throws NoMoreWriteSpaceException {
		this.wrPex.writeEOM();
	}
	
	@Override
	public void flush() throws NoMoreWriteSpaceException {
		this.wrPex.flush();
	}

	@Override
	public void beginStreamType(byte datastreamType) throws NoMoreWriteSpaceException {
		this.wrPex.beginStreamType(datastreamType);
	}

	@Override
	public void resetWritingToWordBoundary() {
		this.wrPex.resetWritingToWordBoundary();
	}

	@Override
	public long readI48() throws EndOfMessageException {
		return this.rdPex.readI48();
	}

	@Override
	public int readI32() throws EndOfMessageException {
		return this.rdPex.readI32();
	}

	@Override
	public int readI16() throws EndOfMessageException {
		return this.rdPex.readI16();
	}

	@Override
	public short readS16() throws EndOfMessageException {
		return this.rdPex.readS16();
	}

	@Override
	public int readI8() throws EndOfMessageException {
		return this.rdPex.readI8();
	}

	@Override
	public short readS8() throws EndOfMessageException {
		return this.rdPex.readS8();
	}

	@Override
	public boolean isAtEnd() {
		return this.rdPex.isAtEnd();
	}
	
	@Override
	public boolean checkIfAtEnd() {
		return this.rdPex.checkIfAtEnd();
	}

	@Override
	public void dropToEOM(byte reqDatastreamType) throws EndOfMessageException {
		this.rdPex.dropToEOM(reqDatastreamType);
	}

	@Override
	public byte getStreamType() {
		return this.rdPex.getStreamType();
	}

	@Override
	public void resetReadingToWordBoundary() {
		this.rdPex.resetReadingToWordBoundary();
	}

	@Override
	public Long getPeerHostId() {
		// remote host-id not known (for now)
		return null;
	}
	
	@Override
	public void sendAbort() {
		// ignored....
	}

}
