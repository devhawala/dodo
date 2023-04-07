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

import dev.hawala.xns.SppAttention;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.XnsException.ExceptionType;
import dev.hawala.xns.iSppInputStream;
import dev.hawala.xns.iSppInputStream.iSppReadResult;
import dev.hawala.xns.iSppOutputStream;
import dev.hawala.xns.iSppSocket;
import dev.hawala.xns.level3.courier.CrProgram.iRawCourierConnectionClient;

/**
 * Abstract communication channel for a GAP session. Each successfully initiated GAP
 * session will "borrow" the SSP stream from the Courier connection and pass it to an
 * instance of a subclass of this abstract class. While this class handles the basic
 * technical communication with the GAP client, the subclass handles the specific
 * data exchange with a foreign or remote system like a host system or some command
 * interaction shell.
 * <br/>
 * The GAP communication ends when either side closes the SPP stream (through the SST
 * 254/255 protocol), so the SPP stream is "given back" to Courier which can leave it
 * closed or (sort of) re-awake the SPP connection by continue using in a given time
 * frame (10 seconds in the Dodo SPP implementation)
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public abstract class GapConnectionHandler implements iRawCourierConnectionClient {
	
	/*
	 * connection target specific functionality to be implemented by subclasses
	 * 
	 * these abstract methods must handle events initiated by the GAP client (i.e.
	 * some terminal emulation or the like running on an XNS connected workstation)
	 * and translate these events to some interaction with the external/foreign
	 * system
	 */
	
	/** Handle a new GAP session for the implemented connection type (e.g. start a thread to receive asynchronous data from remote) */
	protected abstract void handleClientConnected() throws XnsException, InterruptedException;
	
	/** Handle closing the GAP session for the implemented connection type */
	protected abstract void handleDisconnected() throws XnsException, InterruptedException;
	
	/** Check if the remote site is alive, we will respond to {@code areYouThere} with {@code iAmHere} if {@code true} is returned */
	protected abstract boolean isRemotePresent() throws XnsException, InterruptedException;
	
	/** Disconnect from remote site and free all related resources as the GAP connections is about to be closed (no veto possible) */
	protected abstract void doCleanup() throws XnsException, InterruptedException;
	
	/** Do all necessary remote site related actions to react on the given GAP code */
	protected abstract void handleControlCode(int code) throws XnsException, InterruptedException;
	
	/** Process a new data chunck arrived from the client while the remote system is connected */
	protected abstract void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException;
	
	/** Process a new data chunck arrived from the client although the remote system is no longer connected(!) */
	protected abstract void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException;
	
	/*
	 * generic functionality: use the SPP connection borrowed from Courier
	 */
	
	protected iSppSocket spp = null;
	protected iSppInputStream remote2here = null;
	protected iSppOutputStream here2remote = null;
	
	boolean remoteDisconnected = false;

	@Override
	public void useConnection(iSppSocket sppSocket) {
		logf("-- ENTERING useConnection()\n");

		remoteDisconnected = false;
		this.spp = sppSocket;
		this.here2remote = sppSocket.getOutputStream();
		this.remote2here = sppSocket.getInputStream();
		
		byte[] buffer = new byte[8192];
		
		try {
			// this instance is assumed exist only if the remote thing is connected, so:
			this.sendOutOfBand(GapCodes.status_mediumUp);
			
			// inform this instance that the GAP connection is operational now
			// (this possibly involves starting a new thread for asynchronously receiving data from remote and transmit it to the client)
			this.handleClientConnected();
			
			// receive and process messages from the client in this thread
			int result = this.receive(buffer);
			while(true) {
//				logf("received %s\n", GapCodes.toString(result));
				if (GapCodes.isData(result)) {
					if (this.remoteDisconnected) {
						this.handleDataDisconnected(buffer, GapCodes.dataLength(result), GapCodes.isEOM(result));
					} else {
						this.handleData(buffer, GapCodes.dataLength(result), GapCodes.isEOM(result));
					}
				} else if (GapCodes.code(result) == GapCodes.ctl_areYouThere) {
					if (this.isRemotePresent()) {
						this.sendOutOfBand(GapCodes.ctl_iAmHere);
					}
				} else if (GapCodes.code(result) == GapCodes.ctl_cleanup) {
					logf("OOB cleanup received, doing local cleanup\n");
					this.doCleanup();
					logf("answering with OOB cleanup\n");
					this.sendOutOfBand(GapCodes.ctl_cleanup);
					// this also ends the GAP interactions
					break;
				} else if (!this.remoteDisconnected) {
					this.handleControlCode(GapCodes.code(result));
				}
				result = this.receive(buffer);
			}
			
		} catch (XnsException e) {
			if (e.getType() == ExceptionType.ConnectionClosed) {
				logf("#### connection closed by remote client\n");
			} else {
				logf("#### Exception %s : %s\n", e.getClass().getSimpleName(), e.getMessage());
				e.printStackTrace();
			}
		} catch (InterruptedException | SppAttention e) {
			logf("#### Exception %s : %s\n", e.getClass().getSimpleName(), e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				this.handleDisconnected();
			} catch (XnsException | InterruptedException e) {
				logf("#### Exception %s : %s\n", e.getClass().getSimpleName(), e.getMessage());
				e.printStackTrace();
			}
		}
		
		logf("-- LEAVING useConnection()\n");
	}
	
	/*
	 * utilities for subclasses allowing to send data and controls to the client and logging
	 */
	
	private String logPrefix = this.getClass().getSimpleName() + ":: ";
	protected void logf(String format, Object... args) {
		System.out.printf(logPrefix + format, args);
	}
	
	private static final byte[] NO_DATA = {};
	
	protected void remoteConnectionLost() throws XnsException, InterruptedException {
		this.remoteDisconnected = true;
	}
	
	protected synchronized void sendData(String text, boolean eom) throws XnsException, InterruptedException {
		byte[] bytes = text.getBytes();
		this.here2remote.write(bytes, (byte)GapCodes.ctl_none, eom);
	}
	
	protected synchronized void sendData(String text) throws XnsException, InterruptedException {
		byte[] bytes = text.getBytes();
		this.here2remote.write(bytes, (byte)GapCodes.ctl_none);
	}
	
	protected synchronized void sendDataEOM(String text) throws XnsException, InterruptedException {
		byte[] bytes = text.getBytes();
		this.here2remote.write(bytes, (byte)GapCodes.ctl_none, true);
	}
	
	protected synchronized void sendData(byte[] buffer, int offset, int length, boolean eom) throws XnsException, InterruptedException {
		this.here2remote.write(buffer, offset, length, (byte)GapCodes.ctl_none, eom);
	}
	
	protected synchronized void sendData(byte[] buffer, int offset, int length) throws XnsException, InterruptedException {
		this.here2remote.write(buffer, offset, length, (byte)GapCodes.ctl_none, false);
	}
	
	protected synchronized void sendDataEOM(byte[] buffer, int offset, int length) throws XnsException, InterruptedException {
		this.here2remote.write(buffer, offset, length, (byte)GapCodes.ctl_none, true);
	}
	
	protected synchronized void sendInBand(int code) throws XnsException, InterruptedException {
		this.here2remote.write(NO_DATA, (byte)code);
	}
	
	protected synchronized void sendOutOfBand(int code) throws XnsException, InterruptedException {
		this.here2remote.sendAttention((byte)code, (byte)GapCodes.ctl_none);
	}
	
	/*
	 * internal utilities
	 */
	
	private int receive(byte[] buffer) throws XnsException, SppAttention, InterruptedException {
		iSppReadResult result = this.remote2here.read(buffer);
		if (result == null) {
			throw new XnsException(ExceptionType.ConnectionClosed, "SPP connection apparently closed");
		}
		
		if (result.isAttention()) {
			return GapCodes.asOutOfBand(result.getAttentionByte());
		}
		
		int sst = result.getDatastreamType() & 0x00FF;
		if (sst == GapCodes.ctl_none) {
			return GapCodes.asData(result.getLength(), result.isEndOfMessage());
		} else if (sst == 0x00) {
			// Interlisp has it's own understanding of things
			// and uses SST 0x00 for data (instead of 0300 = 0xC0 as it was defined for GAP3)
			// ... so also accept that as data-SST
			return GapCodes.asData(result.getLength(), result.isEndOfMessage());
		} else {
			return GapCodes.asInBand(sst, result.isEndOfMessage());
		}
	}

}
