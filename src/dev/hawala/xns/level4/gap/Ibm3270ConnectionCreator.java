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

import dev.hawala.vm370.stream3270.CommandCode3270;
import dev.hawala.vm370.stream3270.Ebcdic6BitEncoding;
import dev.hawala.vm370.stream3270.OrderCode3270;
import dev.hawala.xns.XnsException;

/**
 * Instantiator for connections to an (real or emulated) IBM mainframe through
 * an IBM 3270 telnet port.
 * <p>
 * Each instance can provide an arbitrary number of connections to the <i>same</i>
 * external system, even at the same time (in parallel) as long as the external
 * systems accepts new connections.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class Ibm3270ConnectionCreator implements iGapConnectionCreator {
	
	private final String connectAddress;
	private final int connectPort;
	private final String luName;
	
	/**
	 * 
	 * @param connectAddress
	 * @param connectPort
	 * @param luName
	 */
	Ibm3270ConnectionCreator(String connectAddress, int connectPort, String luName) {
		this.connectAddress = connectAddress;
		this.connectPort = connectPort;
		this.luName = luName;
	}

	@Override
	public GapConnectionHandler create() {
		// to do: create real connection
		System.out.printf("\n+++\n");
		System.out.printf("+++++ Ibm3270ConnectionCreator: connecting to:\n");
		System.out.printf("+++      -> connectAddress: '%s'\n", this.connectAddress);
		System.out.printf("+++      -> connectPort   : %d\n", this.connectPort);
		System.out.printf("+++      -> luName        : '%s'\n", this.luName);
		System.out.printf("+++\n");
		try {
			return new Ibm3270ConnectionHandler(this.connectAddress, this.connectPort, this.luName);
		} catch (IOException e) {
			System.out.printf("\n+++\n");
			System.out.printf("+++++ Ibm3270ConnectionCreator :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			System.out.printf("+++\n");
			return null;
		}
	}
	
	private static class Ibm3270ConnectionHandler extends GapConnectionHandler implements Runnable {
		
		private final Socket socketToHost;
		
		private final InputStream streamHost2client;
		private final OutputStream streamClient2host;
		
		private Thread threadProcess2client = null;
		
		private int lastClientControlCode = -1;
		
		Ibm3270ConnectionHandler(String connectAddress, int connectPort, String luName) throws IOException {
			this.socketToHost = new Socket(connectAddress, connectPort);
			this.socketToHost.setTcpNoDelay(true);
			
			this.streamHost2client = this.socketToHost.getInputStream();
			this.streamClient2host = this.socketToHost.getOutputStream();
			
			this.negotiateHost3270Mode(luName);
		}

		@Override
		protected void handleClientConnected() throws XnsException, InterruptedException {
			this.threadProcess2client = new Thread(this);
			this.threadProcess2client.start();
		}

		@Override
		protected void handleDisconnected() throws XnsException, InterruptedException {
			System.out.printf("+++++ Ibm3270ConnectionHandler.handleDisconnected() ... delegating to doCleanup()\n");
			this.doCleanup();
		}

		@Override
		protected boolean isRemotePresent() throws XnsException, InterruptedException {
			return this.socketToHost.isConnected() && !this.socketToHost.isClosed();
		}

		@Override
		protected void doCleanup() throws XnsException, InterruptedException {
			System.out.printf("+++ Ibm3270ConnectionHandler.doCleanup() begin\n");
			
			if (this.socketToHost.isConnected() && !this.socketToHost.isClosed()) {
				System.out.printf("+++++ Ibm3270ConnectionHandler.doCleanup() closing connection to host\n");
				try { this.streamClient2host.close(); } catch (IOException ioe) { }
				try { this.streamHost2client.close(); } catch (IOException ioe) { }
				try { this.socketToHost.close(); } catch (IOException ioe) { }
			}
		
			if (this.threadProcess2client != null) {
				System.out.printf("+++++ Ibm3270ConnectionHandler.doCleanup() ... this.threadProcess2client.interrupt()\n");
				this.threadProcess2client.interrupt();
				System.out.printf("+++++ Ibm3270ConnectionHandler.doCleanup() ... this.threadProcess2client.join()\n");
				this.threadProcess2client.join();
				System.out.printf("+++++ Ibm3270ConnectionHandler.doCleanup() ... this.threadProcess2client = null\n");
				this.threadProcess2client = null;
			}
			
			System.out.printf("+++ Ibm3270ConnectionHandler.doCleanup() done\n");
		}

		@Override
		protected void handleControlCode(int code) throws XnsException, InterruptedException {
			this.lastClientControlCode = code;
		}
		
		private final byte[] bufferToHost = new byte[32768];
		private int lengthToHost = 0;
		
		private void appendToHost(byte[] buffer, int usedLength) {
			System.arraycopy(buffer, 0, bufferToHost, lengthToHost, usedLength);
			lengthToHost += usedLength;
		}
		
		private void resetToHost() {
			this.lengthToHost = 0;
		}

		@Override
		protected void handleData(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
			if (this.lastClientControlCode != GapCodes.ctl_readModified3270) {
				return;
			}
			
			if (!this.socketToHost.isConnected() || this.socketToHost.isClosed()) {
				return;
			}
			
			try {
				this.appendToHost(buffer, usedLength);
				if (isEOM) {
					this.appendToHost(TN_EOR, TN_EOR.length);
					this.streamClient2host.write(this.bufferToHost, 0, lengthToHost);
					this.streamClient2host.flush();
					this.resetToHost();
				}
			} catch (IOException e) {
				System.out.printf("+++++ Ibm3270ConnectionHandler :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
				try { this.streamClient2host.close(); } catch (IOException ioe) { }
				try { this.streamHost2client.close(); } catch (IOException ioe) { }
				try { this.socketToHost.close(); } catch (IOException ioe) { }
				this.remoteConnectionLost();
				System.out.printf("+++++ Ibm3270ConnectionHandler :: closed connection to host\n");
			}
		}

		@Override
		protected void handleDataDisconnected(byte[] buffer, int usedLength, boolean isEOM) throws XnsException, InterruptedException {
			// if we are disconnected: do nothing...
		}

		@Override
		public void run() {
			byte[] buffer = new byte[32768];
			try {
				this.debug("+++++ Ibm3270ConnectionHandler.run() :: STARTED background thread [host=>client]");
				int length = this.readBlock(buffer);
				while(length > 1) {
					if (buffer[1] == CommandCode3270.WSF) {
						this.handleWSF(buffer, 1, length);
					} else {
						length = this.removeExtendedOrders(buffer, length);
						this.sendInBand(GapCodes.ctl_unchained3270);
						this.sendDataEOM(buffer, 0, length);
					}
					length = this.readBlock(buffer);
				}
				this.debug("+++++ Ibm3270ConnectionHandler.run() :: FINISHED");
			} catch (IOException|XnsException|InterruptedException e) {
				System.out.printf("+++++ Ibm3270ConnectionHandler.run() :: ERROR %s : %s\n", e.getClass().getName(), e.getMessage());
			}
		}
		
		private int readBlock(byte[] to) throws IOException {
			int toPos = 0;
			boolean lastEOR0 = false;
			to[toPos++] = (byte)0x27;
			
			int b = this.streamHost2client.read();
			while(b >= 0 && toPos < to.length) {
				if ((byte)b == TN_EOR[1] && lastEOR0) {
					return toPos;
				}
				if (lastEOR0) {
					to[toPos++] = TN_EOR[0];
					lastEOR0 = false;
				}
				if ((byte)b == TN_EOR[0]) {
					lastEOR0 = true;
				} else {
					to[toPos++] = (byte)b;
				}
				b = this.streamHost2client.read();
			}
			return toPos;
		}
		
		private int removeExtendedOrders(byte[] buffer, int length) {
			int wPos = 1;
			int rPos = 1;
			while (rPos < length) {
				byte b = buffer[rPos++];
				if (b == OrderCode3270.SFE.getCode()) {
					byte pairCount = (byte)(buffer[rPos++] & 0x7f);
					buffer[wPos++] = OrderCode3270.SF.getCode();
					byte fieldAttrs = Ebcdic6BitEncoding.encode6BitValue((byte)0xA0); // protected
					for (int pair = 0; pair < pairCount; pair++) {
						byte type = buffer[rPos++];
						byte value = buffer[rPos++];
						if (type == (byte)0xC0) { // basic field attributes
							fieldAttrs = value;
						}
					}
					buffer[wPos++] = fieldAttrs;
				} else if (b == OrderCode3270.MF.getCode()) {
					byte pairCount = (byte)(buffer[rPos++] & 0x7f);
					rPos += pairCount; // skip type/value pairs
				} else if (b == OrderCode3270.SA.getCode()) {
					rPos += 2; // skip type/value pair
				} else {
					buffer[wPos++] = b;
				}
			}
			return wPos;
		}
		
		//
		// handle a 3270 WSF-Query coming from the host
		//
		
		private static byte[] WSF_REPLY_NULL = {
			(byte)0x88, (byte)0x00, (byte)0x04, (byte)0x81, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xEF
		};
		
		private int asUnsigned(byte b) {
			if (b >= 0) { return (int)b; }
			return 256 + (int)(b);
		}
		
		private void sendWSFQueryReply(byte[] reply) throws IOException {
			try {
				Thread.sleep(5); // wait 5 ms
			} catch (InterruptedException e) {
				// ignored
			}
			this.streamClient2host.write(reply);
			this.streamClient2host.flush();
		}
		
		private int handleWSF(byte[] b, int start, int count) throws IOException {
			// b[0] is WSF = 0xF3
			// b[1],b[2] = length of structured field
			
			int len = (asUnsigned(b[1]) << 8) + asUnsigned(b[2]);
			
			if (len > 6) { // minimal length of a read partition for Query[List]
				byte sfid = b[start + 3];
				byte pid1 = b[start + 4];
				byte pid2 = b[start + 5];
				byte type = b[start + 6];
				if (sfid == (byte)0x01 && pid1 == (byte)0xFF && pid2 == (byte)0xFF) { // ReadPartition(01) && QueryOperation(FF)
					if (type == (byte)0x02 || type == (byte)0x03) {
						this.debug("handleWSF(other): sending WSF_REPLY_NULL");
						this.sendWSFQueryReply(WSF_REPLY_NULL);
					} // other types ignored
				} // other SFIDs or Partitions ignored
			}
			
			// find EOR and return the offset of 0xFF
			int i = start +  1;
			while(i < count) {
				if (b[i] == 0xFF && b[i+1] == 0xEF) { return i; }
				if (b[i] == 0xFF && b[i+1] == 0xFF) { i++; }
				i++;
			}
			return i;
		}
		
		/*
		 * ********************************************************* 3270 specifics for telnet
		 */

		
		private static byte[] TN_EOR = { (byte)0xFF, (byte)0xEF };
		
		private static byte[] TN_DO_TERMINAL_TYPE 
								= { (byte)0xFF, (byte)0xFD, (byte)0x18 };
		private static byte[] TN_WILL_TERMINAL_TYPE 
								= { (byte)0xFF, (byte)0xFB, (byte)0x18 };

		private static byte[] TN_SB_SEND_TERMINAL_TYPE 
								= { (byte)0xFF, (byte)0xFA, (byte)0x18, (byte)0x01, (byte)0xFF, (byte)0xF0 }; // incl. TN_SE


		private static byte[] TN_SB_TERMINAL_TYPE_IS_3270_2_E
								= { (byte)0xFF, (byte)0xFA, (byte)0x18, (byte)0x00,
									(byte)0x49, (byte)0x42, (byte)0x4d, (byte)0x2d, // IBM-
									(byte)0x33, (byte)0x32, (byte)0x37, (byte)0x38, // 3278
									(byte)0x2d, (byte)0x32, (byte)0x2d, (byte)0x45, // -2-E
									(byte)0xFF, (byte)0xF0                          // TN_SE
									};

		private static byte[] TN_DO_END_OF_RECORD 
								= { (byte)0xFF, (byte)0xFD, (byte)0x19 };
		private static byte[] TN_WILL_END_OF_RECORD 
								= { (byte)0xFF, (byte)0xFB, (byte)0x19 };
		
		private static byte[] TN_DO_BINARY 
								= { (byte)0xFF, (byte)0xFD, (byte)0x00 };
		private static byte[] TN_WILL_BINARY 
								= { (byte)0xFF, (byte)0xFB, (byte)0x00 };
		
		
		private void debug(String s) {
			//System.out.printf(" 3270::debug: %s\n", s);
		}
		
		private void info(String s) {
			//System.out.printf(" 3270::info.: %s\n", s);
		}
		
		private void error(String s) {
			System.out.printf(" 3270::ERROR: %s\n", s);
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
		
		/**
		 * Perform the complete telnet-negotiation with the host to enter the TN3270 binary
		 * transmission mode, claiming to be an IBM-3278-2-E terminal emulation.
		 * <p>
		 * The {@code IBM-3278-2} terminal type is the default terminal in the IBM host world, as
		 * supported by Star/ViewPoint: 80 columns, 24 lines, monochrome, initial (minimal) formatting
		 * set (hidden text, highlight)
		 * </p>
		 * <p>
		 * The {@code -E} type suffix signaling support for "extended data stream" is necessary
		 * for "SixPack"-type VM/370 systems with Bob Polmanter's fullscreen extension, so the
		 * CP extension will perform a 3270 WSF-query to get terminal capabilities: this successful
		 * WSF-query cycle will later inform the FSIO-part in the MECAFF tools that it is talking to
		 * a directly connected 3270 terminal, what in turn will prevent these programs from sending
		 * the text sequences to check for a MECAFF (Java-)process emulating the VM/370  fullscreen
		 * functionality.
		 * <br/>
		 * But as {@code -E} also signals extended formatting capabilities (colors, underline, ...), the
		 * corresponding order codes in the 3270 stream must be removed for compatibility with the
		 * "plain" 3270 terminal emulation supported by Star/ViewPoint.
		 * </p>
		 * 
		 * @param cfgLuName the L(ogical)U(nit)-Name to connect to (resp. <code>null</code> or empty string for
		 *   no LU-name).
		 * @throws IOException
		 */
		protected void negotiateHost3270Mode(String luName) throws IOException {
			byte[] buffer = new byte[256];
			boolean sentTerminal = false;
			boolean meBinary = false;
			boolean hostBinary = false;
			boolean meEOR = false;
			boolean hostEOR = false;
			
			this.info("Begin of TN3270 mode negotiation with Host");
			while(!(sentTerminal && meBinary && hostBinary && meEOR && hostEOR)) {
				int rcvd = this.streamHost2client.read(buffer);
				int offset = 0;
				while (offset < rcvd) {
					if (this.isPresent(TN_DO_TERMINAL_TYPE, buffer, offset)) {
						this.streamClient2host.write(TN_WILL_TERMINAL_TYPE);
						this.streamClient2host.flush();
						this.debug(" Rcvd: DO TERMINAL TYPE    =>   Sent: WILL TERMINAL TYPE");
						offset += TN_DO_TERMINAL_TYPE.length;
					} else if (this.isPresent(TN_SB_SEND_TERMINAL_TYPE, buffer, offset)) {
						byte[] termSeq = TN_SB_TERMINAL_TYPE_IS_3270_2_E;
						if (luName != null && !luName.isEmpty()) {
							// insert @<luname> into the predefined terminal name sequence
							if (luName.length() > 32) { luName = luName.substring(0, 32); }
							int oldLen = TN_SB_TERMINAL_TYPE_IS_3270_2_E.length;
							byte[] oldSeq = TN_SB_TERMINAL_TYPE_IS_3270_2_E;
							int newLen = oldLen + luName.length() + 1;
							termSeq = new byte[newLen];
							int pos = 0;
							for (int i = 0; i < oldLen - 2; i++) { termSeq[pos++] = oldSeq[i]; }
							termSeq[pos++] = '@';
							for (int i = 0; i < luName.length(); i++) { termSeq[pos++] = (byte)luName.charAt(i); }
							for (int i = oldLen - 2; i < oldLen; i++) { termSeq[pos++] = oldSeq[i]; }
						}
						this.streamClient2host.write(termSeq);
						this.streamClient2host.flush();
						this.debug(" Rcvd: SEND TERMINAL TYPE  =>   Sent: TERMINAL TYPE IS IBM3278-2");
						offset += TN_SB_SEND_TERMINAL_TYPE.length;
						sentTerminal = true;
					} else if (this.isPresent(TN_DO_END_OF_RECORD, buffer, offset)) {
						this.streamClient2host.write(TN_WILL_END_OF_RECORD);
						this.streamClient2host.flush();
						this.debug(" Rcvd: DO END OF RECORD    =>   Sent: WILL END OF RECORD");
						offset += TN_DO_END_OF_RECORD.length;
						meEOR = true;
					} else if (this.isPresent(TN_WILL_END_OF_RECORD, buffer, offset)) {
						this.streamClient2host.write(TN_DO_END_OF_RECORD);
						this.streamClient2host.flush();
						this.debug(" Rcvd: WILL END OF RECORD  =>   Sent: DO END OF RECORD");
						offset += TN_WILL_END_OF_RECORD.length;
						hostEOR = true;
					} else if (this.isPresent(TN_DO_BINARY, buffer, offset)) {
						this.streamClient2host.write(TN_WILL_BINARY);
						this.streamClient2host.flush();
						this.debug(" Rcvd: DO BINARY           =>   Sent: WILL BINARY");
						offset += TN_DO_BINARY.length;
						meBinary = true;
					} else if (this.isPresent(TN_WILL_BINARY, buffer, offset)) {
						this.streamClient2host.write(TN_DO_BINARY);
						this.streamClient2host.flush();
						this.debug(" Rcvd: WILL BINARY         =>   Sent: DO BINARY");
						offset += TN_WILL_BINARY.length;
						hostBinary = true;
					} else {
						this.error("Received invalid data while negocating into TN3270 binary mode");
						throw new IOException("Received invalid data while negocating into TN3270 binary mode");
					}
				}
			}
			this.info("Done successfull TN3270 mode negotiation with Host");
		}
		
	}

}
