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

package dev.hawala.xns.level3.courier;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.CrProgram.iRawCourierConnectionClient;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Courier connection handler, managing the version handshake and the
 * initiation of procedure invocations by invoking the Courier dispatched.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class CourierConnection {
	
	// the range of Courier protocol versions we can support
	private static final int COURIER_VERSION_MIN = 2;
	private static final int COURIER_VERSION_MAX = 3;
	
	private final String connId;
	private final iWireStream wireStream;
	
	private boolean needVersions = true;
	private boolean sendVersions = true;
	private int courierVersion = COURIER_VERSION_MIN; // default to minimum
	
	public CourierConnection(iWireStream courierStream, String connId) {
		this.wireStream = courierStream;
		this.connId = connId;
	}
	
	public iRawCourierConnectionClient processSingleCall() throws EndOfMessageException, NoMoreWriteSpaceException {
		// make sure we are in RPC stream
		if (this.wireStream.getStreamType() != Constants.SPPSST_RPC) {
			this.wireStream.dropToEOM(Constants.SPPSST_RPC);
		}
		
		Log.C.printf(null, "CourierConnection.processSingleCall() - waiting for ingoing call\n");
		
		// check for version handshake
		if (this.needVersions) {
			int othersLow = this.wireStream.readI16();
			int othersHigh = this.wireStream.readI16();
			Log.C.printf(this.connId, "CourierConnection - needVersions: low = %d , high = %d\n", othersLow, othersHigh);
			if (othersHigh < othersLow || othersHigh < COURIER_VERSION_MIN || othersLow > COURIER_VERSION_MAX) {
				// no common version => simulate "no program", as there seems not to be an adequate rejection?
				int transaction = this.wireStream.readI16();
				Log.C.printf(this.connId, "CourierConnection # no common version, rejecting request with transaction %d\n", transaction);
				this.wireStream.dropToEOM(Constants.SPPSST_RPC);
				this.wireStream.writeI16(1); // MessageType.reject(1)
				this.wireStream.writeI16(transaction);
				this.wireStream.writeI16(0); // RejectCode.noSuchProgramNumber(0)
				this.wireStream.writeEOM();
				return null;
			}
			// this goes to the min . commonprotocol version: if (othersLow > this.courierVersion) { this.courierVersion = othersLow; }
			// use the max. possible common version
			this.courierVersion = Math.min(othersHigh,  COURIER_VERSION_MAX);
			this.needVersions = false;
			
			// skip EOM sent by Interlisp-D systems
			this.wireStream.isAtEnd();
		}
		if (this.sendVersions) { 
			Log.C.printf(this.connId, "CourierConnection - sendVersions: low = high = %d\n", this.courierVersion);
			this.wireStream.writeI16(this.courierVersion);
			this.wireStream.writeI16(this.courierVersion);
			this.wireStream.flush();
			this.sendVersions = false;
		}
		
		// get message type and assert for 'call' (as we are a *server* on this side!)
		int messageType = this.wireStream.readI16();
		Log.C.printf(null, "CourierConnection.processSingleCall() - begin\n");
		if (messageType != 0) {
			// not a call...
			int transaction = this.wireStream.readI16();
			Log.C.printf(this.connId, "CourierConnection # not 'call', rejecting request with transaction %d\n", transaction);
			this.wireStream.dropToEOM(Constants.SPPSST_RPC);
			this.wireStream.writeI16(1); // MessageType.reject(1)
			this.wireStream.writeI16(transaction);
			this.wireStream.writeI16(3); // RejectCode.invalidArguments(3) ... best we have...?
			this.wireStream.writeEOM();
			return null;
		}
		
		// get the conversation id for this call and dispatch this call
		int transaction = this.wireStream.readI16();
		Log.C.printf(this.connId, "CourierConnection - dispatching call with transaction %d\n", transaction);
		iRawCourierConnectionClient connectionClient = CourierRegistry.dispatch(this.courierVersion, transaction, this.wireStream);
		Log.C.printf(this.connId, "CourierConnection - done call with transaction %d\n\n", transaction);
		return connectionClient;
	}

}
