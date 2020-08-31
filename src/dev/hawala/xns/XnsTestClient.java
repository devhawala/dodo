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
import dev.hawala.xns.level2.SPP;

public class XnsTestClient {
	
	private static iNetMachine localSite;
	
	private static final long XNSHOST = 0x0000_0800_2BCC_DDEEL;
	private static final int SPP_SOCK = 0x0777;

	public static void main(String[] args) throws XnsException, InterruptedException, SppAttention {
		
		//LocalSite.configureHub(null, 0);
		LocalSite.configureLocal(0x0401, LocalSite.getMachineId(), "XnsClient", true, false);
		localSite = LocalSite.getInstance();
		
		iSppSocket sock= localSite.sppConnect(XNSHOST, SPP_SOCK);
		iSppInputStream nis = sock.getInputStream();
		
		int pNum = 0;
		
		byte[] buffer = new byte[SPP.SPP_MAX_PAYLOAD_LENGTH];
		while(!nis.isClosed()) {
			iSppReadResult res = nis.read(buffer);
			
			pNum++;
			
			if (res.isAttention()) {
				System.out.printf("[%2d] ** is attendtion, attention byte: %c\n", pNum, res.getAttentionByte());
			} else {
				System.out.printf("[%2d] len = %d (content: %s) , SST = 0x%02X , isEOM = %s\n",
						pNum,
						res.getLength(),
						(res.getLength() > 0) ? "" + buffer[0] : "",
						res.getDatastreamType() & 0xFF,
						res.isEndOfMessage() ? "true" : "false");
			}
		}
	}

}
