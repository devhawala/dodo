/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.examples;

import dev.hawala.xns.LocalSite;
import dev.hawala.xns.Log;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.PEX;

/**
 * Example program for using the XNS-API in Dodo.
 * <p>
 * This simple program does first a Time service request broadcast,
 * then a Bfs for Clearinghouse broadcast. In both cases, the packet
 * content received as response is dumped with the interpreted IDP
 * and PEX structure and the raw PEX payload.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz / Berlin 2019
 */
public class XnsTestRequestor {
	
	private static iNetMachine localSite;
	
	private static void doTimeServiceRequest() throws XnsException {
		System.out.println("\n## sending Time service request");
		byte[] requestData = {
				0x00, 0x02,  // time serice version 2
				0x00, 0x01   // request
			};
		Payload response = localSite.pexRequest(
				IDP.BROADCAST_ADDR,
				IDP.KnownSocket.TIME.getSocket(),
				PEX.ClientType.TIME.getTypeValue(), 
				requestData,
				0,
				requestData.length);
		if (response == null) {
			System.out.println("=> time response: null (timeout)\n");
		} else if (response instanceof PEX) {
			System.out.println("=> time response:");
			PEX pex = (PEX)response;
			System.out.println(pex.toString());
			System.out.printf("=> PEX.payload: %s\n", pex.payloadToString());
		} else if (response instanceof Error) {
			System.out.println("=> time response is of type: Error\n");
		} else {
			System.out.printf("=> time response is of unexpected type: %s\n", response.getClass().getName());
		}
	}
	
	private static void doChsBfsrequest() throws XnsException {
		System.out.println("\n## sending Bfs for Clearinghouse request");
		byte[] requestData = {
				0x00, 0x03,              // lower accepted Courier version
				0x00, 0x03,              // upper accepted Courier version
				0x00, 0x00,              // Courier message type: call
				0x56, 0x78,              // transaction
				0x00, 0x00, 0x00, 0x02,  // program
				0x00, 0x03,              // program version
				0x00, 0x00               // procedure to invoke (no args)
			};
		Payload response = localSite.pexRequest(
				IDP.BROADCAST_ADDR,
				IDP.KnownSocket.CLEARINGHOUSE.getSocket(),
				PEX.ClientType.CLEARINGHOUSE.getTypeValue(), 
				requestData,
				0,
				requestData.length);
		if (response == null) {
			System.out.println("=> bfs response: null (timeout)\n");
		} else if (response instanceof PEX) {
			System.out.println("=> bfs response:");
			PEX pex = (PEX)response;
			System.out.println(pex.toString());
			System.out.printf("=> PEX.payload: %s\n", pex.payloadToString());
		} else if (response instanceof Error) {
			System.out.println("=> bfs response is of type: Error\n");
		} else {
			System.out.printf("=> bfs response is of unexpected type: %s\n", response.getClass().getName());
		}
	}

	public static void main(String[] args) throws XnsException {
		// silence down Dodo's logging
		Log.L0.doLog(false);
		Log.L1.doLog(false);
		Log.L2.doLog(false);
		Log.L3.doLog(false);
		Log.L4.doLog(false);
		
		// initialize local xns network link with defaults
		// System.out.printf("\nstarting LocalSite\n");
		LocalSite.configureHub("localhost", 3333);
		localSite = LocalSite.getInstance();

		// do a time service request and check response
		doTimeServiceRequest();
		
		// do a clearinghouse BfS request and check response
		doChsBfsrequest();
		
		// shutdown xns network link
		// System.out.printf("\n\nshutting down LocalSite\n");
		LocalSite.shutdown();
		// System.out.println("\n**\n*** LocalSite shut down\n**");
		
		// Dodo does currently not stop all threads on shutdown(), sorry about that
		// so terminate the program explicitely
		System.exit(0);
	}

}
