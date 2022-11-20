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

package dev.hawala.xns;

import dev.hawala.xns.iSppInputStream.iSppReadResult;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.PEX;
import dev.hawala.xns.level2.SPP;
import dev.hawala.xns.level4.chs.BfsClearinghouseResponder;
import dev.hawala.xns.level4.chs.Clearinghouse3Impl;
import dev.hawala.xns.level4.common.ChsDatabase;
import dev.hawala.xns.level4.time.TimeServiceResponder;

/*
 * probleme
 * - wenn server den thread noch nicht hat => es wird ERROR (no listener?) zurückgeschickt ??
 *   (evtl. auch race condition mit Anlegen des Listener sockets und senden der positiv-Antwort?)
 *   => ein serielles Packet geht verloren?? => kein Resend??
 *   
 * - => error handler in SPP: Ausgabe der Error Daten !!
 * 
 * - => logging von data re-sends in SPP
 * - => logging von system-packet versand
 * 
 * - connectionClose initierung/antworten: aktuelle direktes Senden? (besser/richtig: normalen daten packete?!?!?) 
 *   
 * - (erl.) keine Pr�fung ob die eigene und remote ConnId des Packets mit denen der Connection übereinstimmen
 * 
 * 
 */

/**
 * Test program for some functionalities, mainly SPP connections...
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class DodoTest {
	
	private static iNetMachine localSite;

	public static void main(String[] args) throws XnsException, InterruptedException {
		
		LocalSite.configureHub(null, 0);
		localSite = LocalSite.getInstance();
		
		// testTimeService();
		
		testBfsChsService();
		
//		// testSimpleSpp();
//		System.out.println("\n\nrunning: testSimpleSpp()");
//		testSimpleSpp();
//		System.out.printf("... done\n");
//		Log.reset();
//		
//		System.out.printf("\n\nrunning: testComplexSpp(2, 1, 1, 1024)\n");
//		testComplexSpp(2, 1, 1, 1024);
//		System.out.printf("... done\n");
//		Log.reset();
//		
//		System.out.printf("\n\nrunning: testComplexSpp(2, 1, 2, 1024)\n");
//		testComplexSpp(2, 1, 2, 1024);
//		System.out.printf("... done\n");
//		Log.reset();
//		
//		System.out.printf("\n\nrunning: testComplexSpp(2, 2, 1, 1024)\n");
//		testComplexSpp(2, 2, 1, 1024);
//		System.out.printf("... done\n");
//		Log.reset();
//		
//		System.out.printf("\n\nrunning: testComplexSpp(2, 2, 2, 1024)\n");
//		testComplexSpp(2, 2, 2, 1024);
//		System.out.printf("... done\n");
//		Log.reset();
//		
//		System.out.printf("\n\nrunning: testComplexSpp(4, 2, 3, 1024)\n");
//		testComplexSpp(2, 2, 3, 1024);
//		System.out.printf("... done\n");
//		Log.reset();
		
		System.out.printf("\n\nshutting down LocalSite\n");
		LocalSite.shutdown();
		System.out.println("\n**\n*** LocalSite shut down\n**");
	}
	
	// complex SPP test
	
	private static class SppServer implements Runnable {
		
		private final int ratio; // send packets after 'ratio' received packets
		private final int burst; // number of packets to send each time
		private final int recvCount; // actively close connection after 'recvCount' received packets
		
		private final iSppServerSocket srvSocket;
		
		public SppServer(int ratio, int burst, int recvCount) throws XnsException {
			this.ratio = ratio;
			this.burst = burst;
			this.recvCount = recvCount;
		
			Log.X.printf(null, "-- server: start listening\n");
			this.srvSocket = localSite.sppListen(SPP_SOCK);
		}
		
		private class Responder implements Runnable {

			private final iSppSocket cltSocket;
			private final iSppInputStream nis;
			private final iSppOutputStream nos;
			
			private final Thread thr;
			
			Responder(iSppSocket cltSocket) {
				Log.X.printf(null, "-- server; responder constructed\n");
				this.cltSocket = cltSocket;
				this.nis = cltSocket.getInputStream();;
				this.nos = cltSocket.getOutputStream();
				
				thr = new Thread(this);
				thr.setName("Server.responder");
				thr.start();
			}
			
			private void fillBuffer(byte[] buffer, int seed) {
				byte fillByte = (byte)(32 + (seed % 16));
				for (int i = 0; i < buffer.length; i++) {
					buffer[i] = fillByte;
				}
			}

			@Override
			public void run() {
				Log.X.printf(null, "-- server; responder - thread started\n");
				byte[] buffer = new byte[SPP.SPP_MAX_PAYLOAD_LENGTH];
				int recv = 0;
				int recvBytes = 0;
				int recvEoms = 0;
				int sent = 0;
				int attn = 0;
				int r = 0;
				int lastSeqNo = 0;
				long startTs = System.currentTimeMillis();
				while(recv < recvCount) {
					try {
						// receive next packet
						Log.X.printf(null, "-- server; responder -> nis.read(buffer)\n");
						iSppReadResult res = nis.read(buffer);
						Log.X.printf(null, "-- server; responder received packet # %d\n", recv+1);
						
						// check packet content (SST = 5 + oddness, length 333 + oddness, eom at 11th) or (attn with attnByte = 9 + oddness)
						if (res.isAttention()) {
							byte ab = res.getAttentionByte();
							// byte expAb = (byte)(9 + (recv % 2));
							// if (ab != expAb) {
							if (ab != 9 && ab != 10) {
								System.err.printf("(resp) recv = %d : received wrong attn byte\n", recv);
							}
							attn++;
						} else {
							recv++;
							r++;
							int seqNo = extractNumber(buffer);
							if (seqNo != (lastSeqNo + 1)) {
								System.err.printf("(resp) recv = %d : received wrong sequenceNo\n", recv);
							}
							lastSeqNo = seqNo;
							
							byte sst = res.getDatastreamType();
							byte expSst = (byte)(5 + (recv % 2));
							if (sst != expSst) {
								System.err.printf("(resp) recv = %d : received wrong sst value\n", recv);
							}
							
							int len = res.getLength();
							int expLen = 333 + (recv % 2);
							if (len != expLen) {
								System.err.printf("(resp) recv = %d : received wrong packet length\n", recv);
							}
							recvBytes += len;
							
							boolean expEom = (recv % 11) == 0;
							if (res.isEndOfMessage() != expEom) {
								System.err.printf("(resp) recv = %d : received wrong EOM flag\n");
							}
							if (res.isEndOfMessage()) { recvEoms++; }
							
							byte expByte = (byte)(64 + (recv % 16));
							for (int i = 4; i < len; i++) {
								if (buffer[i] != expByte) {
									System.err.printf("(resp) recv = %d : received wrong packet content\n", recv);
									break;
								}
							}
						}
						
						// check if sent back packets
						if (r >= ratio) {
							r = 0;
							// packets: (SST = 3 + oddness, length 222 + oddness, eom at 7th) or (sent%11: attn with attnByte = 23 + oddness)
							
							for (int i = 0; i < burst; i++) {
								sent++;
								if ((sent % 11) == 0) {
									byte attnByte = (byte)(23 + (sent % 2));
									Log.X.printf(null, "-- server; responder sending packet # %d - attn(%d)\n", sent, attnByte);
									nos.sendAttention(attnByte);
									Log.X.printf(null, "-- server; ... sent attn packet\n");
								}
								if ((sent % 2) == 0) {
									fillBuffer(buffer, sent);
									implantNumber(buffer, sent);
									Log.X.printf(null, "-- server; responder sending packet # %d - len=222 sst=3%s\n", sent, ((sent % 7) == 0) ? ", eom" : "");
									nos.write(buffer, 0, 222, (byte)3, (sent % 7) == 0);
									Log.X.printf(null, "-- server; ... sent data packet\n");
								} else {
									fillBuffer(buffer, sent);
									implantNumber(buffer, sent);
									Log.X.printf(null, "-- server; responder sending packet # %d - len=223 sst=4%s\n", sent, ((sent % 7) == 0) ? ", eom" : "");
									nos.write(buffer, 0, 223, (byte)4, (sent % 7) == 0);
									Log.X.printf(null, "-- server; ... sent data packet\n");
								}
							}
						}
					} catch (XnsException | SppAttention | InterruptedException e) {
						break;
					}
				}
				long duration = System.currentTimeMillis() - startTs + 1;
				Log.I.printf(null, ">> done responder loop in %d ms :: recv = %d (bytes: %d , eoms: %d) , sent = %d , attn-in = %d\n", duration, recv, recvBytes, recvEoms, sent, attn);
				Log.X.printf(null, "-- server; responder - thread done, closing connection\n");
				this.cltSocket.close();
				Log.X.printf(null, "-- server; responder - connection closed\n");
			}
			
		}

		@Override
		public void run() {
			while(true) {
				iSppSocket cltSocket = srvSocket.listen();
				if (cltSocket != null) {
					Log.X.printf(null, "-- server; listen() delivered connection => lauching new Responder\n");
					new Responder(cltSocket);
				} else {
					Log.X.printf(null, "-- server; listen() delivered null connection => leaving listen loop and thread\n");
					break;
				}
			}
		}
		
		public void shutdown() {
			this.srvSocket.close();
		}
	}
	
	private static class SppClient implements Runnable {
		
		private final iSppSocket sock;
		private final iSppInputStream nis;
		private final iSppOutputStream nos;
		
		private final Thread thrReceiver;
		private final Thread thrSender;
		
		SppClient() throws XnsException {
			Log.X.printf(null, "## client; connecting...\n");
			this.sock = localSite.sppConnect(localSite.getMachineId(), SPP_SOCK);
			Log.X.printf(null, "## client; ... connected\n");
			nis = sock.getInputStream();
			nos = sock.getOutputStream();
			
			// start receiver
			Log.X.printf(null, "## client; starting receiver thread\n");
			this.thrReceiver = new Thread(this);
			this.thrReceiver.setName("Client.Receiver");
			this.thrReceiver.start();
			
			// run sender
			Log.X.printf(null, "## client; starting sender thread\n");
			this.thrSender = new Thread(() -> {
				byte[] buffer = new byte[334];
				int sent = 0;
				// (SST = 5 + oddness, length 333 + oddness, eom at 11th) or (attn with attnByte = 9 + oddness)
				try {
					while(!nos.isClosed()) {
						sent++;
						if ((sent % 11) == 0) {
							byte attnByte = (byte)(9 + (sent % 2));
							Log.X.printf(null, "## client; sending attn(%d)\n", attnByte);
							nos.sendAttention(attnByte);
							Log.X.printf(null, "## client; ... sent attn(%d)\n", attnByte);
						}
						if ((sent % 2) == 0) {
							fillBuffer(buffer, sent);
							implantNumber(buffer, sent);
							Log.X.printf(null, "## client; sending data, len=333, sst=5%s\n", ((sent % 11) == 0) ? ", eom" : "");
							nos.write(buffer, 0, 333, (byte)5, (sent % 11) == 0);
							Log.X.printf(null, "## client; ... sent data packet\n");
						} else {
							fillBuffer(buffer, sent);
							implantNumber(buffer, sent);
							Log.X.printf(null, "## client; sending data, len=334, sst=6%s\n", ((sent % 11) == 0) ? ", eom" : "");
							nos.write(buffer, 0, 334, (byte)6, (sent % 11) == 0);
							Log.X.printf(null, "## client; ... sent data packet\n");
						}
					}
				} catch (XnsException | InterruptedException e) {
					// loop left => thread done
				}
				Log.X.printf(null, "## client; left sender loop, closing thread\n");
			});
			this.thrSender.setName("Client.Sender");
			this.thrSender.start();
		}
		
		private void fillBuffer(byte[] buffer, int seed) {
			byte fillByte = (byte)(64 + (seed % 16));
			for (int i = 0; i < buffer.length; i++) {
				buffer[i] = fillByte;
			}
		}

		// packet receiver (this class instance)
		@Override
		public void run() {
			byte[] buffer = new byte[SPP.SPP_MAX_PAYLOAD_LENGTH];
			int recv = 0;
			int lastSeqNo = 0;
			while(!nis.isClosed()) {
				try {
					Log.X.printf(null, "## client; nis.read(buffer)\n");
					iSppReadResult res = nis.read(buffer);
					if (res == null) {
						Log.X.printf(null, "## client; received null => end-of-stream\n");
						break;
					}
					Log.X.printf(null, "## client; received packet # %d\n", recv + 1);
					
					// expected packets: (SST = 3 + oddness, length 222 + oddness, eom at 7th) or (sent%11: attn with attnByte = 23 + oddness)
					if (res.isAttention()) {
						byte ab = res.getAttentionByte();
						// byte expAb = (byte)(23 + (recv % 2));
						// if (ab != expAb) {
						if (ab != 23 && ab != 24) {
							System.err.printf("(clnt) recv = %d : received wrong attn byte\n", recv);
						}
					} else {
						recv++;
						
						int seqNo = extractNumber(buffer);
						if (seqNo != (lastSeqNo + 1)) {
							System.err.printf("(clnt) recv = %d : received wrong sequenceNo\n", recv);
						}
						lastSeqNo = seqNo;
						
						byte sst = res.getDatastreamType();
						byte expSst = (byte)(3 + (recv % 2));
						if (sst != expSst) {
							System.err.printf("(clnt) recv = %d : received wrong sst value\n", recv);
						}
						
						int len = res.getLength();
						int expLen = 222 + (recv % 2);
						if (len != expLen) {
							System.err.printf("(clnt) recv = %d : received wrong packet length\n", recv);
						}
						
						byte expByte = (byte)(32 + (recv % 16));
						for (int i = 4; i < len; i++) {
							if (buffer[i] != expByte) {
								System.err.printf("(clnt) recv = %d : received wrong packet content\n", recv);
								break;
							}
						}
					}
				} catch (XnsException | SppAttention | InterruptedException e) {
					break;
				}
			}
			Log.X.printf(null, "## client; done receiving, left thread\n");
		}
		
		public void waitDone() throws InterruptedException {
			this.thrSender.join();
			this.thrReceiver.join();
		}
	}
	
	private static void implantNumber(byte[] buf, int no) {
		buf[0] = (byte)((no >> 24) & 0xFF);
		buf[1] = (byte)((no >> 16) & 0xFF);
		buf[2] = (byte)((no >> 8) & 0xFF);
		buf[3] = (byte)(no & 0xFF);
	}
	
	private static int extractNumber(byte[] buf) {
		int num = (buf[0] & 0xFF) << 24
				| (buf[1] & 0xFF) << 16
				| (buf[2] & 0xFF) << 8
				| (buf[3] & 0xFF);
		return num;
	}
	
	/**
	 * 
	 * @param clientCount number of clients
	 * @param ratio server-responder sends 'burst' packets after 'ratio' received packets
	 * @param burst number of packets to send each time the sender-responder replies
	 * @param recvCount server-responter actively closes connection after 'recvCount' received packets
	 * @throws XnsException
	 * @throws InterruptedException
	 */
	private static void testComplexSpp(int clientCount, int ratio, int burst, int recvCount) throws XnsException, InterruptedException {
		Log.X.doLog(false);
		Log.L0.doLog(false);
		Log.L1.doLog(false);
		Log.L2.doLog(false);
		Log.L3.doLog(false);
		
		SppServer server = new SppServer(ratio, burst, recvCount);
		Thread serverThread = new Thread(server);
		serverThread.setName("Server.ConnectionListener");
		serverThread.start();
		
		SppClient[] clients = new SppClient[clientCount];
		for (int i = 0; i < clients.length; i++) {
			SppClient client = new SppClient();
			clients[i] = client;
		}
		for (int i = 0; i < clients.length; i++) {
			clients[i].waitDone();
		}
		
		server.shutdown();
		serverThread.join();
	}
	
	
	
	// simple SPP test
	
	private static final int SPP_SOCK = 3344;
	private static final String[] PACKET_CONTENTS = {
		"11111111111111111",
		"22222222222222222222",
		"33333333333333333",
		"44444444444444444444",
		"55555555555555555",
		"66666666666666666666",
		"77777777777777777",
		"88888888888888888888",
		"99999999999999999",
		"00000000000000000000",
		"aaaaaaaaaaaaaaaaa",
		"bbbbbbbbbbbbbbbbbbbb",
		"ccccccccccccccccc",
		"dddddddddddddddddddd",
		"eeeeeeeeeeeeeeeee",
		"ffffffffffffffffffff",
		"ggggggggggggggggg",
		"hhhhhhhhhhhhhhhhhhhh"
	};
	
	private static void testSimpleSpp() throws InterruptedException, XnsException {
		Log.X.doLog(false);
		Log.L0.doLog(false);
		Log.L1.doLog(false);
		Log.L2.doLog(false);
		Log.L3.doLog(false);
		
		int tmp = 0;
		for(String s : PACKET_CONTENTS) {
			tmp += s.length();
		}
		int totalLength = tmp;
		
		Thread receiver = new Thread(() -> {
			try {
				Log.X.printf(null, "SPP-Srv: opening server socket\n");
				iSppServerSocket srvSocket = localSite.sppListen(SPP_SOCK);
				
				int recvLen = 0;
				byte[] buffer = new byte[5];
				Log.X.printf(null, "SPP-Srv: listening for ingoing open request\n");
				iSppSocket cltSocket = srvSocket.listen();
				
				Log.X.printf(null, "SPP-Srv: closing server socket\n");
				srvSocket.close();
				Log.X.printf(null, "SPP-Srv: server socket closed\n");
				
				iSppInputStream nis = cltSocket.getInputStream();
				Log.X.printf(null, "SPP-Srv: client connected !!\n");
				int i = 0;
				while (recvLen < totalLength) {
					iSppReadResult res = nis.read(buffer);
					Log.X.printf(null,
							"SPP-Rcv[%d]: sst=%d ; attn=%s ; eom=%s ; packetLen=%d ; content='%s'\n",
							i++, res.getDatastreamType(), res.isAttention() ? "true":"false", res.isEndOfMessage() ? "true":"false", res.getLength(),
							new String(buffer, 0, res.getLength()));
					if (res.isEndOfMessage()) {
						break;
					}
					recvLen += res.getLength();
				}
				Log.X.printf(null, "SPP-Srv: closing client connection\n");
				cltSocket.close();
			} catch (XnsException | SppAttention | InterruptedException e) {
				e.printStackTrace();
			}
		});
		receiver.start();
		Thread.sleep(1000);
		
		Log.X.printf(null, "## opening client connection\n");
		iSppSocket sock = localSite.sppConnect(localSite.getMachineId(), SPP_SOCK);
		iSppOutputStream nos = sock.getOutputStream();
		Log.X.printf(null, "## client connection opened\n");
		for (int i = 0; i < PACKET_CONTENTS.length; i++) {
			String data = PACKET_CONTENTS[i];
			byte sst = (byte)(10 + i);
			Log.X.printf(null, "## sending packet[%d] with: '%s'\n", i, data);
			nos.write(data.getBytes(), sst);
		}

		Log.X.printf(null, "## done sending, synching outgoing packets\n");
		nos.sync(); // Thread.sleep(1000);
		Log.X.printf(null, "## done sending, closing client connection\n");
		sock.close();
	}
	
	// time PEX service test (time service)
	
	private static void testTimeService() throws XnsException {
		localSite.pexListen(
				IDP.KnownSocket.TIME.getSocket(), 
				new TimeServiceResponder(0, 0, 0, 0));
		
		byte[] requestData = { 0x00, 0x02, 0x00, 0x01 };
		Payload response = localSite.pexRequest(
				IDP.BROADCAST_ADDR,
				IDP.KnownSocket.TIME.getSocket(),
				PEX.ClientType.TIME.getTypeValue(), 
				requestData,
				0,
				requestData.length);
		if (response == null) {
			System.out.println("time response: null (timeout)\n");
		} else if (response instanceof PEX) {
			System.out.println("time response is of type: PEX\n");
			PEX pex = (PEX)response;
			System.out.printf("=> PEX: %s\n", pex.toString());
			System.out.printf("=> PEX.payload: %s\n", pex.payloadToString());
		} else if (response instanceof Error) {
			System.out.println("time response is of type: Error\n");
		} else {
			System.out.printf("time response is of unexpected type: %s\n", response.getClass().getName());
		}
	}
	
	private static void testBfsChsService() throws XnsException {
		ChsDatabase chsDatabase = new ChsDatabase(0x1122_3344, "organization", "domain", null, true);
		Clearinghouse3Impl.init(0x1122_3344, 0xAABB_CCDD_EEFFL, chsDatabase);
		localSite.pexListen(
				IDP.KnownSocket.CLEARINGHOUSE.getSocket(), 
				new BfsClearinghouseResponder());
		
		byte[] requestData = { 0x00, 0x03, 0x00, 0x03, 0x00, 0x00, 0x56, 0x78, 0x00, 0x00, 0x00, 0x02, 0x00, 0x03, 0x00, 0x00 };
		Payload response = localSite.pexRequest(
				IDP.BROADCAST_ADDR,
				IDP.KnownSocket.CLEARINGHOUSE.getSocket(),
				PEX.ClientType.CLEARINGHOUSE.getTypeValue(), 
				requestData,
				0,
				requestData.length);
		if (response == null) {
			System.out.println("bfs response: null (timeout)\n");
		} else if (response instanceof PEX) {
			System.out.println("bfs response is of type: PEX\n");
			PEX pex = (PEX)response;
			System.out.printf("=> PEX: %s\n", pex.toString());
			System.out.printf("=> PEX.payload: %s\n", pex.payloadToString());
		} else if (response instanceof Error) {
			System.out.println("bfs response is of type: Error\n");
		} else {
			System.out.printf("bfs response is of unexpected type: %s\n", response.getClass().getName());
		}
	}

}
