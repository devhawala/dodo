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

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.stream3270.AidCode3270;
import dev.hawala.vm370.stream3270.BufferAddress;
import dev.hawala.vm370.stream3270.DataOutStream3270;
import dev.hawala.vm370.stream3270.OrderCode3270;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.XnsException.ExceptionType;

/**
 * Simple 3270 replier application, simulating a connection to an external IBM
 * mainframe through an 3270 connection, displaying input lines entered so far
 * as a list in the screen.
 * <p>
 * A disconnect by the external system can be simulated by sending a "PF 3"
 * from the terminal emulation.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class Simple3270Replier extends GapConnectionHandler {
	
	private final DataOutStream3270 os3270 = new DataOutStream3270();
	private final BufferAddress ba = new BufferAddress();
	
	private final String[] textLines = { "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "" };
	
	private int lastClientControlCode = -1;
	
	private void addNewText(String text) {
		for (int i = this.textLines.length - 1; i > 0; i--) {
			this.textLines[i] = this.textLines[i - 1];
		}
		this.textLines[0] = text;
	}
	
	private void buildDisplay() {
		this.os3270
			.clearAndESC()
			.cmdEraseWrite(false, true, false)
			
			.setBufferAddress(1, 1)
			.startField(true, false, true, false, false)
			.appendUnicode("Simple 3270 Replier Application")
			.startField(true, false, false, false, false)
			
			.setBufferAddress(3, 1)
			.appendUnicode("Text lines added so far:")
			;
		
		for (int i = 0; i < this.textLines.length; i++) {
			this.os3270
				.setBufferAddress(4 + i, 4)
				.startField(true, false, true, false, false)
				.appendUnicode(textLines[i])
				.startField(true, false, false, false, false)
				;
		}
		
		this.os3270
			.setBufferAddress(22, 1)
			.appendUnicode("Please enter text to add")
			.setBufferAddress(23, 1)
			.appendUnicode("==>")
			.startField(false, false, true, false, false)
			.insertCursor()
			.setBufferAddress(23, 70)
			.startField(true, false, false, false, false)
			;
	}
	
	private void sendDisplay() throws XnsException, InterruptedException {
		// http://prycroft6.com.au/misc/3270.html
		try {
			this.os3270.consumeStream( (buffer,length) -> {
				this.sendInBand(GapCodes.ctl_unchained3270);
				this.sendDataEOM(buffer, 0, length);
				return true;
			});
		} catch (Exception e) {
			if (e instanceof XnsException) { throw (XnsException)e; }
			if (e instanceof InterruptedException) { throw (InterruptedException)e; }
			throw new XnsException(ExceptionType.Stopped, e.getMessage());
		}
	}

	@Override
	protected void handleClientConnected() throws XnsException, InterruptedException {
		logf("building and sending initial screen\n");
		this.buildDisplay();
		this.sendDisplay();
	}

	@Override
	protected void handleDisconnected() throws XnsException, InterruptedException {
		// nothing to do here (no threads to join or the like)
	}

	@Override
	protected boolean isRemotePresent() throws XnsException, InterruptedException {
		return true;
	}

	@Override
	protected void doCleanup() throws XnsException, InterruptedException {
		// no remote connection to close or other resources to free
	}

	@Override
	protected void handleControlCode(int code) throws XnsException, InterruptedException {
		this.lastClientControlCode = code;
	}
	
	private void doNothing(String reason) throws XnsException, InterruptedException {
		logf(reason);
		this.buildDisplay();
		this.sendDisplay();
	}

	@Override
	protected void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
		if (this.lastClientControlCode != GapCodes.ctl_readModified3270) {
			logf("--- lastClientControlCode != ctl_readModified3270, but 0x%02X\n", this.lastClientControlCode & 0xFF);
			doNothing("--- RESENDING screen\n");
			return;
		}
		if (usedLength < 1) {
			doNothing("--- ingoing 3270 buffer empty, doing nothing\n");
			return;
		}
		
		AidCode3270 aid = AidCode3270.map(buffer[0]);
		logf("-- Aid: %s\n", aid);
		if (aid == AidCode3270.PF03) {
			logf("--- PF3 => shutting down...\n");
			this.remoteConnectionLost();
			this.handleDataDisconnected(buffer, usedLength, isEOM);
			return;
		}
		if (aid != AidCode3270.Enter) {
			doNothing("--- not ENTER or PF3 => ignoring input...\n");
			return;
		}

		if (usedLength < 6) {
			doNothing("--- ingoing 3270 buffer < 6 bytes, doing nothing\n");
			return;
		}
		if (buffer[3] != OrderCode3270.SBA.getCode()) {
			doNothing("--- ingoing 3270 buffer < 6 bytes, doing nothing\n");
			return;
		}
		this.ba.decode(buffer[4], buffer[5]);
		// we don't need the address if the input field, as there is only one
		
		int textLength = usedLength - 6;
		if (textLength <= 0) {
			doNothing("--- no text entered, doing nothing\n");
			return;
		}
		EbcdicHandler txt = new EbcdicHandler();
		txt.appendEbcdic(buffer, 6, textLength);
		String text = txt.getString();
		logf("----- entered text: '%s'\n", text);
		this.addNewText(text);
		
		this.buildDisplay();
		this.sendDisplay();
	}

	@Override
	protected void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
		this.os3270
			.clearAndESC()
			.cmdEraseWrite(false, true, false)
			
			.setBufferAddress(1, 1)
			.startField(true, false, true, false, false)
			.appendUnicode("Thank you for using the Simple 3270 Replier Application")
			
			.setBufferAddress(2, 1)
			.startField(true, false, true, false, false)
			.appendUnicode("Please disconnect and close this window...")
	
			.setBufferAddress(3, 1)
			.startField(true, false, false, false, false)
			.insertCursor()
			;
		this.sendDisplay();
	}

}
