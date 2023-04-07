/*
Copyright (c) 2023, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.gap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import dev.hawala.xns.XnsException;

/**
 * Instantiator for connections to an external system through a plain telnet
 * connection.
 * <p>
 * Each instance can provide an arbitrary number of connections to the <i>same</i>
 * external system, even at the same time (in parallel) as long as the external
 * systems accepts new connections.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class TTYTelnetConnectionCreator implements iGapConnectionCreator {
	
	private final String connectAddress;
	private final int connectPort;
	private final String terminalType;
	private final XeroxControlCharMapper clientCharMapper;
	
	TTYTelnetConnectionCreator(
			String connectAddress,
			int connectPort,
			String terminalType,
			XeroxControlCharMapper clientCharMapper) {
		this.connectAddress = (connectAddress == null || connectAddress.isEmpty()) ? "127.0.0.1" : connectAddress;
		this.connectPort = connectPort;
		this.terminalType = (terminalType == null || terminalType.isEmpty()) ? "ansi" : terminalType;
		this.clientCharMapper = clientCharMapper;
	}

	@Override
	public GapConnectionHandler create() {
		System.out.printf("\n+++\n");
		System.out.printf("+++++ TTYTelnetConnectionCreator: connecting to:\n");
		System.out.printf("+++      -> connectAddress  : '%s'\n", this.connectAddress);
		System.out.printf("+++      -> connectPort     : %d\n", this.connectPort);
		System.out.printf("+++      -> terminalType    : '%s'\n", this.terminalType);
		System.out.printf("+++      -> clientCharMapper: '%s'\n", this.clientCharMapper);
		System.out.printf("+++\n");
		
		try {
			return new TTYTelnetConnectionHandler(this.connectAddress, this.connectPort, this.terminalType, this.clientCharMapper);
		} catch (Exception e) {
			System.out.printf("\n+++\n");
			System.out.printf("+++++ TTYTelnetConnectionCreator :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			System.out.printf("+++\n");
			return null;
		}
	}
	
	private static class TTYTelnetConnectionHandler extends GapConnectionHandler implements Runnable {
		
		private final String terminalType;
		private final XeroxControlCharMapper clientCharMapper;
		
		private final Socket socketToRemote;
		private final InputStream streamRemote2client;
		private final OutputStream streamClient2remote;
		
		private Thread threadProcess2client = null;
		
		TTYTelnetConnectionHandler(String connectAddress, int connectPort, String terminalType, XeroxControlCharMapper clientCharMapper) throws IOException {
			this.terminalType = terminalType;
			this.clientCharMapper = clientCharMapper;
			
			this.socketToRemote = new Socket(connectAddress, connectPort);
			this.socketToRemote.setTcpNoDelay(true);
			
			this.streamRemote2client = this.socketToRemote.getInputStream();
			this.streamClient2remote = this.socketToRemote.getOutputStream();
		}

		@Override
		protected void handleClientConnected() throws XnsException, InterruptedException {
			this.threadProcess2client = new Thread(this);
			this.threadProcess2client.start();
		}

		@Override
		protected void handleDisconnected() throws XnsException, InterruptedException {
			System.out.printf("+++++ TTYTelnetConnectionHandler.handleDisconnected() ... delegating to doCleanup()\n");
			this.doCleanup();
		}

		@Override
		protected boolean isRemotePresent() throws XnsException, InterruptedException {
			return this.socketToRemote.isConnected() && !this.socketToRemote.isClosed();
		}

		@Override
		protected void doCleanup() throws XnsException, InterruptedException {
			System.out.printf("+++ TTYTelnetConnectionHandler.doCleanup() begin\n");
			
			if (this.socketToRemote.isConnected() && !this.socketToRemote.isClosed()) {
				System.out.printf("+++++ TTYTelnetConnectionHandler.doCleanup() closing connection to host\n");
				try { this.streamClient2remote.close(); } catch (IOException ioe) { }
				try { this.streamRemote2client.close(); } catch (IOException ioe) { }
				try { this.socketToRemote.close(); } catch (IOException ioe) { }
			}
		
			if (this.threadProcess2client != null) {
				System.out.printf("+++++ TTYTelnetConnectionHandler.doCleanup() ... this.threadProcess2client.interrupt()\n");
				this.threadProcess2client.interrupt();
				System.out.printf("+++++ TTYTelnetConnectionHandler.doCleanup() ... this.threadProcess2client.join()\n");
				this.threadProcess2client.join();
				System.out.printf("+++++ TTYTelnetConnectionHandler.doCleanup() ... this.threadProcess2client = null\n");
				this.threadProcess2client = null;
			}
			
			System.out.printf("+++ TTYTelnetConnectionHandler.doCleanup() done\n");
		}

		@Override
		protected void handleControlCode(int code) throws XnsException, InterruptedException {
			try {
				this.clientCharMapper.handleControlCode(code, this.streamClient2remote);
			} catch (IOException e) {
				System.out.printf("+++++ TTYTelnetConnectionHandler :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
				try { this.streamClient2remote.close(); } catch (IOException ioe) { }
				try { this.streamRemote2client.close(); } catch (IOException ioe) { }
				try { this.socketToRemote.close(); } catch (IOException ioe) { }
				this.remoteConnectionLost();
				System.out.printf("+++++ TTYTelnetConnectionHandler :: closed connection to host\n");
			}
		}

		@Override
		protected void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
			try {
				this.clientCharMapper.map(buffer, usedLength, this.streamClient2remote);
			} catch (IOException e) {
				System.out.printf("+++++ TTYTelnetConnectionHandler :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
				try { this.streamClient2remote.close(); } catch (IOException ioe) { }
				try { this.streamRemote2client.close(); } catch (IOException ioe) { }
				try { this.socketToRemote.close(); } catch (IOException ioe) { }
				this.remoteConnectionLost();
				System.out.printf("+++++ TTYTelnetConnectionHandler :: closed connection to host\n");
			}
		}

		@Override
		protected void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
			// if we are disconnected: ignore input from the client ...
		}

		@Override
		public void run() {
			byte[] recvBuffer = new byte[1024];
			byte[] sendBuffer = new byte[1024];
			try {
				int length = this.streamRemote2client.read(recvBuffer);
				while (length > 0) {
					int recvPos = 0;
					int sendPos = 0;

					// did the remote machine request a telnet negociation ?
					if (length > 1 && recvBuffer[0] == (byte)0xFF && recvBuffer[1] != (byte)0xFF) {
						recvPos = this.handleHost3215Negotiation(recvBuffer, length);
					}
					
					// process (remaining) bytes from remote
					while(recvPos < length) {
						sendBuffer[sendPos++] = recvBuffer[recvPos++];
					}
					
					// transmit to client workstation
					if (sendPos > 0) {
						this.sendDataEOM(sendBuffer, 0, sendPos);
					}
					
					length = this.streamRemote2client.read(recvBuffer);
				}
			} catch (IOException|XnsException|InterruptedException e) {
				System.out.printf("+++++ TTYTelnetConnectionHandler.run() :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			}
		}
		
		/**
		 * Process a single telnet-negotiation request from the remote connection and send back
		 * the correct negotiation response to stay in plain (i.e. non-TN3270) connection mode. 
		 * @param buffer the buffer containing the telnet-negotiation command. 
		 * @param count length of the data block received from the host.
		 * @return number of bytes processed (i.e. length of the negotiation-request) from the buffer.
		 * @throws IOException
		 * @throws InterruptedException
		 */
		protected int handleHost3215Negotiation(byte[] buffer, int count) throws IOException {
			int processed = 0;
			
			while (processed < (count - 1) && buffer[processed] == TN_IAC && buffer[processed+1] != TN_IAC) {
				if (this.isPresent(TN_DO_TERMINAL_TYPE, buffer, processed)) {
					this.debug("got TN_DO_TERMINAL_TYPE from remote");
					this.streamClient2remote.write(TN_WILL_TERMINAL_TYPE);
					processed += TN_DO_TERMINAL_TYPE.length;
					this.debug("sent TN_WILL_TERMINAL_TYPE to remote");
				} else if (this.isPresent(TN_SB_SEND_TERMINAL_TYPE, buffer, processed)) {
					this.debug("got TN_SB_SEND_TERMINAL_TYPE from remote");
					
					byte[] termBytes = this.terminalType.getBytes();
					byte[] terminalTypeIs = new byte[TN_SE.length + termBytes.length + TN_SB_TERMINAL_TYPE_IS.length];
					int pos = 0;
					for (int i = 0; i < TN_SE.length; i++) { terminalTypeIs[pos++] = TN_SE[i]; }
					for (int i = 0; i < termBytes.length; i++) { terminalTypeIs[pos++] = termBytes[i]; }
					for (int i = 0; i < TN_SB_TERMINAL_TYPE_IS.length; i++) { terminalTypeIs[pos++] = TN_SB_TERMINAL_TYPE_IS[i]; }
					
					this.streamClient2remote.write(terminalTypeIs);
					this.streamClient2remote.flush();
					processed += TN_SB_SEND_TERMINAL_TYPE.length;
					this.debug("sent TN_SB_TERMINAL_TYPE_IS_... to remote");
				} else if (this.isPresent(TN_WONT_ECHO, buffer, processed)) {
					this.debug("got TN_WONT_ECHO from remote");
					this.streamClient2remote.write(TN_DONT_ECHO);
					// TODO: what own behavior must we change?
					processed = TN_WONT_ECHO.length;
					this.debug("sent TN_DONT_ECHO to remote");
				} else if (buffer[processed+1] == TN_WILL) {
					this.debug("got TN_WILL " + byCode(buffer[processed+2]) + " from remote");
					processed += 3;
				} else if (buffer[processed+1] == TN_WONT) {
					this.debug("got TN_WONT " + byCode(buffer[processed+2]) + " from remote");
					processed += 3;
				} else if (buffer[processed+1] == TN_DO) {
					TN_OPTION what = byCode(buffer[processed+2]);
					this.debug("got TN_DO " + what + " from remote");
					processed += 3;
					if (what == TN_OPTION.TRANSMIT_BINARY) {
						// we already do that
						this.streamClient2remote.write(TN_IAC);
						this.streamClient2remote.write(TN_WILL);
						this.streamClient2remote.write(TN_OPTION.TRANSMIT_BINARY.code());
						this.streamClient2remote.flush();
						this.debug("sent TN_WILL TRANSMIT_BINARY to remote");
					}
				} else {
					this.info("got unknown negotiation from remote, content:");
					for (int i = processed; i < count; i++) {
						System.out.printf("  0x%02X", buffer[i] & 0xFF);
					}
					System.out.println();
					break;
				}
			}
			
			return processed;
		}
		
		private void debug(String s) {
			System.out.printf(" telnet::debug: %s\n", s);
		}
		
		private void info(String s) {
			System.out.printf(" telnet::info.: %s\n", s);
		}
		
		private void error(String s) {
			System.out.printf(" telnet::ERROR: %s\n", s);
		}
		
		private boolean isPresent(byte[] what, byte[] in, int at) {
			return this.isPresent(what, in, at, null);
		}
		
		private boolean isPresent(byte[] what, byte[] in, int at, String failMsg) {
			if (in.length < at || in.length < (at + what.length - 1)) { 
				if (failMsg != null) { this.error(failMsg + " - to short"); }
				return false; 
			}
			for (int i = 0; i < what.length; i++) {
				if (what[i] != in[at+i]) { 
					if (failMsg != null) { this.error(failMsg + " - different at src[" + i + "]"); }
					return false; 
				}
			}
			if (failMsg != null) { this.debug(failMsg + " - present in response at offset " + at); }
			return true;
		}
		
	}
	
	private static final byte TN_IAC = (byte)0xFF;
	private static final byte TN_END = (byte)0xF0;
	
	private static final byte TN_DO   = (byte)0xFD;
	private static final byte TN_DONT = (byte)0xFE;
	private static final byte TN_WILL = (byte)0xFB;
	private static final byte TN_WONT = (byte)0xFC;
	private static final byte TN_SEND = (byte)0xFA;

	private static byte[] TN_SE = { TN_IAC, TN_END };
	
	private static byte[] TN_DO_TERMINAL_TYPE 
							= { TN_IAC, TN_DO, (byte)0x18 };
	private static byte[] TN_WILL_TERMINAL_TYPE 
							= { TN_IAC, TN_WILL, (byte)0x18 };

	
	private static byte[] TN_SB_SEND_TERMINAL_TYPE 
							= { TN_IAC, TN_SEND, (byte)0x18, (byte)0x01, TN_IAC, TN_END }; // incl. TN_SE

	private static byte[] TN_SB_TERMINAL_TYPE_IS
							= { TN_IAC, TN_SEND, (byte)0x18, (byte)0x00
							}; // ... add terminal-type-string and TN_SE
	
	private static byte[] TN_WONT_ECHO 
							= { TN_IAC, TN_WONT, (byte)0x01 };
	private static byte[] TN_DONT_ECHO 
							= { TN_IAC, TN_DONT, (byte)0x01 };

	private enum TN_OPTION {
		TRANSMIT_BINARY(0),
		ECHO(1),
		SUPPRESS_GO_AHEAD(3),
		STATUS(5),
		TIMING_MARK(6),
		NAOCRD(10),
		NAOHTS(11),
		NAOHTD(12),
		NAOFFD(13),
		NAOVTS(14),
		NAOVTD(15),
		NAOLFD(16),
		EXTEND_ASCII(17),
		TERMINAL_TYPE(24),
		NAWS(31),
		TERMINAL_SPEED(32),
		TOGGLE_FLOW_CONTROL(33),
		LINEMODE(34),
		AUTHENTICATION(37);
		
		private final byte code;
		private TN_OPTION(int code) { this.code = (byte)code; codes[code&0xFF] = this; }
		public byte code() { return this.code; }
	}
	private static TN_OPTION[] codes = null;
	private static TN_OPTION byCode(byte value) {
		if (codes == null) {
			codes = new TN_OPTION[256];
			for (TN_OPTION o : TN_OPTION.values()) {
				codes[o.code()] = o;
			}
		}
		return codes[value & 0xFF];
	}
	
}
