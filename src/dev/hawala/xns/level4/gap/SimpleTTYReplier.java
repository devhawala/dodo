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

import java.io.UnsupportedEncodingException;

import dev.hawala.xns.XnsException;

/**
 * Simple TTY replier, simulating an external system by echoing any input
 * back to the client.
 * <p>
 * A disconnect by the external system can be simulated by sending a "Break"
 * from the terminal emulation.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class SimpleTTYReplier extends GapConnectionHandler {

	@Override
	protected void handleClientConnected() throws XnsException, InterruptedException {
		logf("sending greet message\n");
		this.sendDataEOM("Welcome to SimpleTTYReplier\r\n\r\n");
	}

	@Override
	protected void handleDisconnected() throws XnsException, InterruptedException {
		// nothing to do here (no threads to join or the like)
	}

	@Override
	protected boolean isRemotePresent() {
		return true;
	}

	@Override
	protected void doCleanup() throws XnsException, InterruptedException {
		// no remote connection to close or other resources to free
	}

	@Override
	protected void handleControlCode(int code) throws XnsException, InterruptedException {
		if (code == GapCodes.ctl_interrupt) {
			logf("interrupt request from client => ending GAP session\n");
			this.sendDataEOM(
					  "\r\n\r\n"
					+ "Thank you for using the SimpleTTYReplier\r\n"
					+ "Please disconnect and close this window...\r\n");
			this.remoteConnectionLost();
		}
	}

	@Override
	protected void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
		try {
			String text = new String(buffer, 0, usedLength, "Cp1252").replace("\r\n", "\r").replace("\r", "\r\n"); // normalize line-ends
			this.sendDataEOM(text);
		} catch (UnsupportedEncodingException e) {
			logf("-- SimpleTTYReplier: invalid data ignored (UnsupportedEncodingException)\n");
		}
	}

	@Override
	protected void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
		// simply swallow any input from the client
	}

}
