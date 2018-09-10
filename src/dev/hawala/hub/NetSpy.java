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

package dev.hawala.hub;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;

import dev.hawala.xns.level2.Error.ErrorCode;

/**
 * Pseudo machine connecting to the hub, simple dumping the content
 * of the packets incl. some XNS structural information to stdout.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class NetSpy {
	
	private static final int HUB_SOCKET = 3333;
	
	private static byte[] buffer = new byte[1026];
	private static int bufLen = 0;
	
	private static short readWord(int offset) {
		int byteOffset = offset << 1;
		if (byteOffset > bufLen) { return 0; }
		int w = ((buffer[byteOffset] & 0x00FF) << 8) | (buffer[byteOffset + 1] & 0x00FF);
		return (short)w;
	}
	
	private static final void logf(String template, Object... args) {
		System.out.printf(template, args);
	}

	/**
	 * @param args
	 * @throws IOException 
	 * @throws UnknownHostException 
	 */
	public static void main(String[] args) throws UnknownHostException, IOException {
		Socket s = new Socket("localhost", HUB_SOCKET);
		s.setTcpNoDelay(true);
		
		InputStream is = s.getInputStream();
		
		int pNum = 0;
		
		while(true) {
			pNum++;
			int b1 = is.read();
			int b2 = is.read();
			int pLen = Math.max(0, ((b1 << 8) & 0xFF00) | (b2 & 0xFF));
			if (pLen == 0) {
				logf("[%d] => ignoring empty packet\n", pNum);
				continue;
			}
			if (pLen > buffer.length - 2) {
				logf("[%d] => invalid packet size %d (> buffer.length - 2) => aborting\n", pNum, pLen);
				break;
			}
			
			if (pLen > 0) {
				int rLen = is.read(buffer, 0, pLen);
				if (rLen != pLen) {
					logf("[%d] => short read (%d instead of expected %d) => aborting\n", pNum, rLen, pLen);
					break;
				}
			}
			bufLen = pLen;
			long nanoTs = System.nanoTime();
			logf("\n[%d] => packet length: %d -- at %9d.%06d ms\n", pNum, bufLen, nanoTs / 1000000, nanoTs % 1000000);
			dumpPacket();
		}
		
		is.close();
		s.close();
	}
	
	private static void dumpPacket() {
		logf("\n          => raw packet content:");
		for (int i = 0; i < (bufLen + 1)/2; i++) {
			if ((i % 16) == 0) {
				logf("\n              0x%03X :", i);
			}
			logf(" %04X", readWord(i));
		}
		logf("\n");
		
		if (bufLen < 7) { return; }
		
		logf("\n          => ethernet packet header\n");
		dumpNetAddress("dst-addr", readWord(0), readWord(1), readWord(2));
		dumpNetAddress("src-addr", readWord(3), readWord(4), readWord(5));
		short etherType = readWord(6);
		logf("              ethType  : 0x%04X (%s)\n", etherType, (etherType == 0x0600) ? "xns" : "other" );
		
		if (etherType != 0x0600) { return; }
		
		logf("\n          => xns packet header\n");
		int pByteLen = readWord(8) & 0xFFFF;
		int pWordLen = (pByteLen + 1) /2;
		logf("              ckSum   : 0x%04X\n", readWord(7));
		logf("              length  : %d bytes => %d words\n", pByteLen, pWordLen);
		short ctlType = readWord(9);
		logf("              transCtl: %d\n", ctlType >>> 8);
		int ptype = ctlType & 0xFF;
		String typeName = "?";
		switch(ptype) {
		case 1: typeName = "Rip"; break;
		case 2: typeName = "Echo"; break;
		case 3: typeName = "Error"; break;
		case 4: typeName = "PEX"; break;
		case 5: typeName = "SPP"; break;
		case 9: typeName = "BootServerPacket"; break;
		case 12: typeName = "PUP"; break;
		default: typeName = "unknown";
		}
		logf("              pktType : %d = %s\n", ptype, typeName);
		dumpXnsEndpoint("destination",
				readWord(10),
				readWord(11),
				readWord(12),
				readWord(13),
				readWord(14),
				readWord(15));
		dumpXnsEndpoint("source",
				readWord(16),
				readWord(17),
				readWord(18),
				readWord(19),
				readWord(20),
				readWord(21));
		
		if (ptype == 1) { // Rip
			int wordsRemaining = (pWordLen + 7) - 22;
			int currWord = 22;
			logf("\n          => RIP( remaining words = %d , curr word = %d )\n", wordsRemaining, currWord);
			
			int operation = readWord(currWord++) & 0xFFFF;
			wordsRemaining--;
			String opName;
			switch(operation) {
			case 1: opName = "request"; break;
			case 2: opName = "response"; break;
			default: opName = "??";
			}
			logf("              operation: %d = %s\n", operation, opName);
			while(wordsRemaining >= 3) {
				int nw1 = readWord(currWord++) & 0xFFFF; wordsRemaining--;
				int nw2 = readWord(currWord++) & 0xFFFF; wordsRemaining--;
				int hops = readWord(currWord++); wordsRemaining--;
				logf("              network 0x %04X %04X , hops = %d\n", nw1, nw2, hops);
			}
		}
		
		if (ptype == 4) { // PEX
			logf("\n          => PEX header\n");
			logf("              identif.  : %04X - %04X\n", readWord(22), readWord(23));
			int ctype = readWord(24);
			String clientType;
			switch(ctype) {
			case 0: clientType = "unspecified"; break;
			case 1: clientType = "time"; break;
			case 2: clientType = "clearinghouse"; break;
			case 8: clientType = "teledebug"; break;
			default: clientType = "??";
			}
			logf("              clientType: %d = %s\n", ctype, clientType);
			dumpXnsBody(typeName, pByteLen, 3);
		}

		if (ptype == 5) { // SPP
			int w22 = readWord(22);
			int tctl = (w22 >> 8) & 0x00FF;
			int sst = w22 & 0x00FF;
			String transCtl = "";
			if ((tctl & 0x80) != 0) { transCtl += " SystemPacket"; }
			if ((tctl & 0x40) != 0) { transCtl += " SendAck"; }
			if ((tctl & 0x20) != 0) { transCtl += " Attention"; }
			if ((tctl & 0x10) != 0) { transCtl += " EndOfMessage"; }
			
			int srcConnId = readWord(23) & 0xFFFF;
			int dstConnId = readWord(24) & 0xFFFF;
			int seqNo = readWord(25) & 0xFFFF;
			int ackNo = readWord(26) & 0xFFFF;
			int allocNo = readWord(27) & 0xFFFF;

			logf("\n          => SPP header\n");
			logf("              TransCtl  : %02X (%s )\n", tctl, transCtl);
			logf("              Data SST  : %02X\n", sst);
			logf("              Source Id : %04X\n", srcConnId);
			logf("              Dest Id   : %04X\n", dstConnId);
			logf("              SequenceNo: %04X\n", seqNo);
			logf("              Ack No    : %04X\n", ackNo);
			logf("              Alloc No  : %04X\n", allocNo);
			dumpXnsBody(typeName, pByteLen, 6);
		}
		
		if (ptype == 3) { // Error
			ErrorCode errorCode = ErrorCode.forNumber(readWord(22) & 0xFFFF);
			int errorParam = readWord(23) & 0xFFFF;
			
			logf("\n          => Error header\n");
			logf("              ErrorCode : %s\n", errorCode);
			logf("              ErrorParam: 0x%04X\n", errorParam);
			dumpXnsBody(typeName, pByteLen, 2);
		}
		
	}
	
	private static void dumpNetAddress(String prefix, short w0, short w1, short w2) {
		logf("              %s : %02X-%02X-%02X-%02X-%02X-%02X\n",
			prefix, (w0 >>> 8) & 0xFF, w0 & 0xFF, (w1 >>> 8) & 0xFF, w1 & 0xFF, (w2 >>> 8) & 0xFF, w2 & 0xFF);
	}
	
	private static void dumpXnsEndpoint(String prefix, short w0, short w1, short w2, short w3, short w4, short w5) {
		logf("\n          => xns %s\n", prefix);
		logf("              network : %04X-%04X\n", w0, w1);
		dumpNetAddress("host   ", w2, w3, w4);
		String socket = null;
		switch(w5) {
		case 1: socket = "routing"; break;
		case 2: socket = "echo"; break;
		case 3: socket = "error"; break;
		case 4: socket = "envoy"; break;
		case 5: socket = "courier"; break;
		case 7: socket = "clearinghouse_old"; break;
		case 8: socket = "time"; break;
		case 10: socket = "boot"; break;
		case 19: socket = "diag"; break;
		case 20: socket = "clearinghouse -- Broadcast for servers / Clearinghouse"; break;
		case 21: socket = "auth -- Broadcast for servers / Authentication"; break;
		case 22: socket = "mail"; break;
		case 23: socket = "net_exec"; break;
		case 24: socket = "ws_info"; break;
		case 28: socket = "binding"; break;
		case 35: socket = "germ"; break;
		case 48: socket = "teledebug"; break;
		}
		logf("              socket  : %04X%s%s\n", w5, (socket == null) ? "" : " - ", (socket == null) ? "" : socket);
	}
	
	private static boolean dumpXnsBody(String prefix, int byteLength, int xnsSkip) {
		int offset = 22 + xnsSkip;
		byteLength -= (15 + xnsSkip) * 2;
		logf("\n          => xns %s payload ( bytes: %d => words: %d )", prefix, byteLength, (byteLength + 1) / 2);
		short w = 0;
		int b = 0;
		boolean isTimeReq = (byteLength == 4);
		for (int i = 0; i < byteLength; i++) {
			if ((i % 2) == 0) {
				w = readWord(offset + (i / 2));
				b = (w >> 8) & 0xFF;
				if (i == 0 && w != 2) { isTimeReq = false; }
				if (i == 2 && w != 1) { isTimeReq = false; }
			} else {
				b = w & 0xFF;
			}
			if ((i % 16) == 0) {
				logf("\n              0x%03X :", i);
			}
			logf(" %02X", b);
		}
		logf("\n");
		return isTimeReq;
	}
}
