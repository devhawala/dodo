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

package dev.hawala.xns.level4.time;

import dev.hawala.xns.Log;
import dev.hawala.xns.iPexResponder;
import dev.hawala.xns.level2.Error.ErrorCode;
import dev.hawala.xns.level2.PEX.ClientType;

/**
 * Time server implementation.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class TimeServiceResponder implements iPexResponder {

	// local time offset data computed in constructor from 
	private final short direction; // 0 = west, 1 = east
	private final short offsetHours;
	private final short offsetMinutes;
	private final int mesaSecondsAdjust;
	
	/**
	 * Initialize the internal time service with the time zone information
	 * (without DST, meaning if DST is active, the {@code gmtOffsetMinutes}
	 * value must be adjusted accordingly).
	 * 
	 * @param gmtOffsetMinutes difference between local time and GMT in
	 * 		minutes, with positive values being to the east and negative
	 * 		to the west (e.g. Germany is +60 without DST and +120 with DST
	 * 		whereas Alaska is -560 without DST resp -480 with DST).
	 * @param moveDateBackDays number of days to subtract from the current
	 * 		date to get the final mesa timestamp, with 0 not changing the date.
	 */
	public TimeServiceResponder(int gmtOffsetMinutes, int moveDateBackDays) {
		if (gmtOffsetMinutes >= 0) {
			this.direction = 1;
		} else {
			this.direction = 0;
			gmtOffsetMinutes = -gmtOffsetMinutes;
		}
		gmtOffsetMinutes = gmtOffsetMinutes % 720;
		this.offsetHours = (short)(gmtOffsetMinutes / 60);
		this.offsetMinutes = (short)(gmtOffsetMinutes % 60);
		
		this.mesaSecondsAdjust = -86400 * moveDateBackDays;
	}

	@Override
	public void handlePacket(
					int clientType,
					byte[] payload,
					ResponseSender responseSender,
					ErrorSender errorSender) {
		Log.L2.printf(null, "TimeServiceResponder.handlePacket()\n");
		boolean isTimeRequest = (clientType == ClientType.TIME.getTypeValue())
					&& payload.length == 4
					&& payload[0] == 0
					&& payload[1] == 2
					&& payload[2] == 0
					&& payload[3] == 1;
		if (!isTimeRequest) {
			Log.L2.printf(null, "TimeServiceResponder.handlePacket(): not a time request, sending back error\n");
			errorSender.sendError(ErrorCode.INVALID_PACKET_TYPE, 0);
			return;
		}
		
		// time data
		long unixTimeMillis = System.currentTimeMillis();
		int  milliSecs = (int)(unixTimeMillis % 1000);
		long unixTimeSecs = unixTimeMillis / 1000;
		int mesaSecs = (int)((unixTimeSecs + (731 * 86400) + 2114294400) & 0x00000000FFFFFFFFL) + this.mesaSecondsAdjust;
		short mesaSecs0 = (short)(mesaSecs >>> 16);
		short mesaSecs1 = (short)(mesaSecs & 0xFFFF);
		
		// payload: time response (12 words)
		byte[] b = new byte[24];
		setWord(b, 0, 2);                  // version(0): WORD -- TimeVersion = 2
		setWord(b, 1, 2);                  // tsBody(1): SELECT type(1): PacketType FROM -- timeResponse = 2
		setWord(b, 2, mesaSecs0);          // time(2): WireLong -- computed mesa time
		setWord(b, 3, mesaSecs1);          // ...
		setWord(b, 4, this.direction);     // zoneS(4): System.WestEast
		setWord(b, 5, this.offsetHours);   // zoneH(5): [0..177B]
		setWord(b, 6, this.offsetMinutes); // zoneM(6): [0..377B]
		setWord(b, 7, 0);                  // beginDST(7): WORD -- no dst (temp)
		setWord(b, 8, 0);                  // endDST(8): WORD -- no dst (temp)
		setWord(b, 9, 1);                  // errorAccurate(9): BOOLEAN -- true
		setWord(b, 10, 0);                 // absoluteError(10): WireLong]
		setWord(b, 11, (short)((milliSecs > 500) ? 1000 - milliSecs : milliSecs)); // no direction ?? (plus or minus)?

		Log.L2.printf(null, "TimeServiceResponder.handlePacket(): sending back time response\n");
		responseSender.sendResponse(b, 0, b.length);
	}
	
	private void setWord(byte[] b, int wOffset, int value) {
		int bOffset = wOffset * 2;
		if ((bOffset + 1) >= b.length) { return; }
		b[bOffset] = (byte)((value >> 8) & 0x00FF);
		b[bOffset+1] = (byte)(value & 0x00FF);
	}

}
