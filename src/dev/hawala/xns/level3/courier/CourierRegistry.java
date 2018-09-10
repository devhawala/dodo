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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;

/**
 * Central management of registered Courier programs and dispatching
 * of Courier procedure invocations.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class CourierRegistry {
	
	private static Map<Long,CrProgram> crPrograms = new HashMap<>();
	
	public static void register(CrProgram program) {
		long key = getProgKey(program.getProgramNumber(), program.getVersionNumber());
		crPrograms.put(key, program);
	}
	
	public static void unregister(int progNo, int progVersion) {
		long key = getProgKey(progNo, progVersion);
		if (crPrograms.containsKey(key)) {
			crPrograms.remove(key);
		}
	}
	
	public static void unregister(int progNo) {
		List<CrProgram> unregs = new ArrayList<>();
		for (CrProgram p : crPrograms.values()) {
			if (p.getProgramNumber() == progNo) {
				unregs.add(p);
			}
		}
		for (CrProgram p : unregs) {
			unregister(p.getProgramNumber(), p.getVersionNumber());
		}
	}
	
	public static boolean isRegistered(int progNo, int progVersion) {
		long key = getProgKey(progNo, progVersion);
		return crPrograms.containsKey(key);
	}
	
	private static long getProgKey(int progNo, int progVersion) {
		long key = ((long)progNo & 0xFFFFFFFFL) << 16;
		key |= (long)progVersion & 0xFFFFL;
		return key;
	}
	
	public static void dispatch(
						int courierVersion,
						int transaction,
						iWireStream connection) throws NoMoreWriteSpaceException, EndOfMessageException {
		int programNo = (courierVersion == 3) ? connection.readI32() : connection.readI16() & 0xFFFF;
		int programVersion = connection.readI16() & 0xFFFF;
		long key = getProgKey(programNo, programVersion);
		
		// ok, program is registered in the requested version
		if (crPrograms.containsKey(key)) {
			Log.C.printf(null, "CourierRegistry: dispatching to program %d version %d\n", programNo, programVersion);
			CrProgram program = crPrograms.get(key);
			program.dispatch(transaction, connection);
			return;
		}
		
		// fail: not registered => find out if not at all or not in the requested version
		// (attention: the registered program version should have continuous (adjacent) versions, as gaps are ignored!!)
		boolean progExists = false;
		int minVersion = 0x7FFFFFFF;
		int maxVersion = -1;
		for (CrProgram p : crPrograms.values()) {
			if (p.getProgramNumber() == programNo) {
				progExists = true;
				int pVersion = p.getVersionNumber();
				if (pVersion < minVersion) { minVersion = pVersion; }
				if (pVersion > maxVersion) { maxVersion = pVersion; }
			}
		}
		
		// reject the call with the appropriate failure response for the courier protocol version in use
		int procNo = connection.readI16();
		connection.dropToEOM(Constants.SPPSST_RPC);
		connection.writeI16(1); // MessageType.reject(1)
		connection.writeI16(transaction);
		if (!progExists) {
			Log.C.printf(null, "CourierRegistry: program %d version %d not registered (procNo %d) => RejectCode.noSuchProgramNumber\n", programNo, programVersion, procNo);
			connection.writeI16(0); // RejectCode.noSuchProgramNumber(0)
		} else {
			Log.C.printf(null, "CourierRegistry: program %d version %d not registered (procNo %d) => RejectCode.noSuchVersionNumber[%d,%d]\n", programNo, programVersion, procNo,  minVersion, maxVersion);
			connection.writeI16(1); // RejectCode.noSuchVersionNumber(1)
			if (courierVersion == 3) {
				// VersionRange (RejectBody for noSuchVersionNumber in Courier protocol version 3)
				connection.writeI16(minVersion);
				connection.writeI16(maxVersion);
			}
		}
		connection.writeEOM();
	}
	
}
