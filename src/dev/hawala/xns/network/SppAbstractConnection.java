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

package dev.hawala.xns.network;

import dev.hawala.xns.EndpointAddress;
import dev.hawala.xns.SppAttention;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.XnsException.ExceptionType;
import dev.hawala.xns.iSppInputStream;
import dev.hawala.xns.iSppOutputStream;
import dev.hawala.xns.iSppSocket;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level2.SppConnection;

/**
 * Common functionality for SPP client and server connections, providing
 * the stream implementations for SPP connections.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class SppAbstractConnection implements iSppSocket, iIDPReceiver {
	
	protected EndpointAddress remoteEndpoint = null;
	
	protected EndpointAddress localEndpoint = null;
	
	protected SppConnection connection = null;
	
	public EndpointAddress getLocalEndpoint() {
		return this.localEndpoint;
	}
	
	public EndpointAddress getRemoteEndpoint() {
		return this.remoteEndpoint;
	}

	@Override
	public synchronized void accept(IDP idp) {
		if (this.connection != null) {
			this.connection.handleIngonePacket(idp);
		}
	}

	@Override
	public synchronized void acceptError(Error err) {
		if (this.connection != null) {
			this.connection.handleErrorPacket(err);
		}
	}
	
	@Override
	public synchronized void stopped() {
//		// signal the closed connection by all streams in use
//		if (this.istream != null) { this.istream.setClosed(); }
//		if (this.ostream != null) { this.ostream.setClosed(); }
		// prevent handling ingoing packets
		this.connection = null;
		// prevent the get-stream methods to return a stream
		this.istream = null;
		this.ostream = null;
	}
	
	private static class SppIDataResult implements iSppInputStream.iSppReadResult {
		
		private final int length;
		private final boolean isEndOfMessage;
		private final byte datastreamType;
		
		private SppIDataResult(int length, boolean isEndOfMessage, byte datastreamType) {
			this.length = length;
			this.isEndOfMessage = isEndOfMessage;
			this.datastreamType = datastreamType;
		}

		@Override
		public int getLength() { return this.length; }

		@Override
		public boolean isEndOfMessage() { return this.isEndOfMessage; }

		@Override
		public boolean isAttention() { return false; }
		
		@Override
		public byte getAttentionByte() { throw new IllegalStateException("No attention pending"); }

		@Override
		public byte getDatastreamType() { return this.datastreamType; }
		
	}
	
	private static class SppIAttentionResult implements iSppInputStream.iSppReadResult {

		@FunctionalInterface
		private interface Callback {
			void doit();
		}
		
		private final byte attnByte;
		private final Callback cb;
		private final byte datastreamType;
		
		private boolean pendingReset = true;
		
		private SppIAttentionResult(byte attnByte, Callback cb, byte datastreamType) {
			this.attnByte = attnByte;
			this.cb = cb;
			this.datastreamType = datastreamType;
		}

		@Override
		public int getLength() { return 0; }

		@Override
		public boolean isEndOfMessage() { return false; }

		@Override
		public boolean isAttention() { return true; }

		@Override
		public byte getAttentionByte() { 
			if (this.pendingReset) {
				this.cb.doit();
				this.pendingReset = false;
			}
			return this.attnByte;
		}

		@Override
		public byte getDatastreamType() { return this.datastreamType; }
		
	}

	private static class SppIStream implements iSppInputStream {
		
		private final SppConnection connection;
		
		private boolean closed = false;
		
		private SPP currSpp = null;
		private int consumedBytes = 0;
		
		private byte datastreamType = 0;
		
		private boolean attnPending = false;
		private byte attnByte;
		
		public SppIStream(SppConnection connection) {
			this.connection = connection;
		}
		
//		public synchronized void setClosed() {
//			this.closed = true;
//		}
//		
//		@Override
//		public synchronized boolean isClosed() {
//			return this.closed;
//		}
		
		@Override
		public boolean isClosed() {
			return this.connection.isClosed();
		}
		
		private synchronized void resetAttention() {
			this.attnPending = false;
		}

		@Override
		public iSppReadResult read(byte[] buffer) throws SppAttention, InterruptedException, XnsException {
			return this.read(buffer, 0, buffer.length);
		}

		@Override
		public synchronized iSppReadResult read(byte[] buffer, int offset, int length) throws SppAttention, InterruptedException, XnsException {
			if (this.closed) { throw new XnsException(ExceptionType.ConnectionClosed); }
			
			if (attnPending) {
				this.attnPending = false;
				throw new SppAttention(this.attnByte);
			}
			if (this.connection.getAttentionState()) {
				this.attnPending = true;
				this.attnByte = this.connection.consumePendingAttention();
				return new SppIAttentionResult(this.attnByte, this::resetAttention, datastreamType);
			}
			
			if (this.currSpp == null) {
				this.currSpp = this.connection.dequeueIngonePacket();
				if (this.currSpp == null) {
					this.closed = true;
					return null;
				}
				this.consumedBytes = 0;
			}
			
			boolean isEndOfMessage = false;
			
			datastreamType = this.currSpp.getDatastreamType();
			
			int count = this.currSpp.rdBytes(
								this.consumedBytes,
								currSpp.getPayloadLength() - this.consumedBytes,
								buffer, offset, length);
			this.consumedBytes += count;
			if (this.consumedBytes >= currSpp.getPayloadLength()) {
				isEndOfMessage = this.currSpp.isEndOfMessage();
				this.currSpp = null;
			}
			
			return new SppIDataResult(count, isEndOfMessage, datastreamType);
		}
		
	}
	
	private SppIStream istream = null;
	
	public synchronized iSppInputStream getInputStream() {
		if (this.istream == null) { 
			if (this.connection == null) { throw new IllegalStateException("Not connected"); }
			this.istream = new SppIStream(this.connection);
		}
		return this.istream;
	}
	
	private static class SppOStream implements iSppOutputStream {
		
		private final SppConnection connection;
		
		private boolean closed = false;
		
		public SppOStream(SppConnection connection) {
			this.connection = connection;
		}
		
//		public synchronized void setClosed() {
//			this.closed = true;
//		}
//		
//		@Override
//		public synchronized boolean isClosed() {
//			this.closed |= this.connection.isClosed();
//			return this.closed;
//		}
		
		@Override
		public synchronized boolean isClosed() {
			return this.connection.isClosed();
		}

		@Override
		public void sendAttention(byte attnByte) throws XnsException {
			if (this.isClosed()) { throw new XnsException(ExceptionType.ConnectionClosed); }
			this.connection.sendAttention(attnByte);
		}

		@Override
		public int write(byte[] buffer, byte datastreamType) throws InterruptedException, XnsException {
			return this.write(buffer,  0, buffer.length, datastreamType, false);
		}

		@Override
		public int write(byte[] buffer, byte datastreamType, boolean isEndOfMessage) throws InterruptedException, XnsException {
			return this.write(buffer,  0, buffer.length, datastreamType, isEndOfMessage);
		}

		@Override
		public int write(byte[] buffer, int offset, int length, byte datastreamType) throws InterruptedException, XnsException {
			return this.write(buffer,  offset, length, datastreamType, false);
		}

		@Override
		public int write(byte[] buffer, int offset, int length, byte datastreamType, boolean isEndOfMessage) throws InterruptedException, XnsException {
			if (this.isClosed()) { throw new XnsException(ExceptionType.ConnectionClosed); }
			
			int sendLength = Math.max(Math.min(buffer.length - offset, length), 0);
			int totalLength = 0;
			
			while(sendLength > SPP.SPP_MAX_PAYLOAD_LENGTH) {
				int sent = this.connection.enqueueOutgoingPacket(buffer, offset, sendLength, datastreamType, false);
				if (sent < 0) {
//					this.setClosed();
					return -1;
				}
				sendLength -= sent;
				totalLength += sent;
			}
			int sent = this.connection.enqueueOutgoingPacket(buffer, offset, sendLength, datastreamType, isEndOfMessage);
			if (sent < 0) {
//				this.setClosed();
				return -1;
			}
			totalLength += sent;
			
			return totalLength;
		}

		@Override
		public void sync() {
			this.connection.sync();
		}

		@Override
		public Byte checkForInterrupt() {
			if (this.connection.getAttentionState()) {
				return this.connection.consumePendingAttention();
			}
			return null;
		}
		
	}
	
	private SppOStream ostream = null;
	
	public synchronized iSppOutputStream getOutputStream() {
		if (this.ostream == null) {
			if (this.connection == null) { throw new IllegalStateException("Not connected"); }
			this.ostream = new SppOStream(this.connection);
		}
		return this.ostream;
	}
	
}
