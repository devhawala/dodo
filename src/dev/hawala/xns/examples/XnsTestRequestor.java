package dev.hawala.xns.examples;

import dev.hawala.xns.LocalSite;
import dev.hawala.xns.Log;
import dev.hawala.xns.XnsException;
import dev.hawala.xns.iNetMachine;
import dev.hawala.xns.level0.Payload;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level2.Error;
import dev.hawala.xns.level2.PEX;

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
