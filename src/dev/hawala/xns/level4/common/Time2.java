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

package dev.hawala.xns.level4.common;

import dev.hawala.xns.level3.courier.CARDINAL;
import dev.hawala.xns.level3.courier.CHOICE;
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;

/**
 * Definition of the data structures of the time protocol
 * as defined by Courier program (PROGRAM 15 VERSION 2).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class Time2 extends CrProgram {
	
	public static final int PROGRAM = 15;
	public static final int VERSION = 2;
	
	public int getProgramNumber() { return PROGRAM; }
	
	public int getVersionNumber() { return VERSION; }

	/*
	 * -- number of seconds since 12:00:00 AM, 1 Jan. 1901, GMT --
	 * Time: TYPE = LONG CARDINAL;
	 */
	public static class Time extends LONG_CARDINAL {
		private Time() {}
		public static Time make() { return new Time(); }
		
		public Time fromUnixMillisecs(long ms) {
			this.set(getMesaTime(ms));
			return this;
		}
		
		public Time now() {
			this.set(getMesaTime());
			return this;
		}
	}
	
	/*
	 * -- Thurs., 12:00:00AM, 1 Jan. 1968 --
	 * earliestTime: Time = 2114294400;
	 */
	public static final long earliestTime = 2114294400;
	
	/*
	 * PacketType:      TYPE = {request(1), response(2)};
	 */
	public enum PacketType implements CrEnum {
		request(1),
		response(2);
		
		private final int wireValue;
		
		private PacketType(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<PacketType> mkPacketType = buildEnum(PacketType.class).get();
	
	/*
	 * OffsetDirection: TYPE = {west(0), east(1)};
	 */
	public enum OffsetDirection { west, east };
	public static final EnumMaker<OffsetDirection> mkOffsetDirection = buildEnum(OffsetDirection.class).get();
	
	/*
	 * ToleranceType:   TYPE = {unknown(0), inMilliSeconds(1)};
	 */
	public enum ToleranceType {unknown, inMilliSeconds };
	public static final EnumMaker<ToleranceType> mkToleranceType = buildEnum(ToleranceType.class).get();
	
	/*
	 * Version: TYPE = CARDINAL;
	 * version: Version = 2;
	 */
	public static final int version = 2;
	
	/*
	 * PacketData: TYPE = CHOICE PacketType OF {
	 * 	request  => RECORD [],
	 * 	response => RECORD [
	 * 		currentTime:     LONG CARDINAL,
	 * 		offsetDirection: OffsetDirection,
	 * 		offsetHours:     CARDINAL,
	 * 		offsetMinutes:   CARDINAL,
	 * 		startOfDST:      CARDINAL,
	 * 		endOfDST:        CARDINAL,
	 * 		toleranceType:   ToleranceType,
	 * 		tolerance:       LONG CARDINAL]};
	 */
	public static class PacketData_response extends RECORD {
		public final LONG_CARDINAL         currentTime = mkLONG_CARDINAL();
		public final ENUM<OffsetDirection> offsetDirection = mkENUM(mkOffsetDirection);
		public final CARDINAL              offsetHours = mkCARDINAL();
		public final CARDINAL              offsetMinutes = mkCARDINAL();
		public final CARDINAL              startOfDST = mkCARDINAL();
		public final CARDINAL              endtOfDST = mkCARDINAL();
		public final ENUM<ToleranceType>   toleranceType = mkENUM(mkToleranceType);
		public final LONG_CARDINAL         tolerance = mkLONG_CARDINAL();
		
		private PacketData_response() {}
		
		public static PacketData_response make() { return new PacketData_response(); }
	}
	public static final ChoiceMaker<PacketType> mkPacketData = buildChoice(mkPacketType)
			.choice(PacketType.request, RECORD::empty)
			.choice(PacketType.response, PacketData_response::make)
			.get();
	
	/*
	 * Packet: TYPE = RECORD [
	 *     version: Version, -- must be 2
	 *     data:    PacketData];
	 */
	public static class Packet extends RECORD {
		public final CARDINAL              version = mkCARDINAL();
		public final CHOICE<PacketType>    data = mkCHOICE(mkPacketData);
		
		private Packet() {}
		
		public static Packet make() { return new Packet(); }
	}
	
	
	/*
	 * *********************** non-Courier public methods 
	 */
	
	private static int mesaSecondsAdjust = 0;
	
	/**
	 * Set the time warp as number of days to move the mesa date
	 * reference to the <i>past</i>.
	 * 
	 * @param moveDateBackDays number of days to subtract from the current
	 * 		date to get the final mesa timestamp, with 0 not changing the date.
	 */
	public static void setTimeWarp(int moveDateBackDays) {
		mesaSecondsAdjust = -86400 * moveDateBackDays;
	}
	
	/**
	 * Compute the current time in Mesa resp. Pilot representation.
	 * 
	 * @return number of seconds since 12:00:00 AM, 1 Jan. 1901, GMT
	 *   as LONG CARDINAL (adjusted for time warp).
	 */
	public static long getMesaTime() {
		return getMesaTime(System.currentTimeMillis());
		
	}
	
	/**
	 * Compute the given unix-time in Mesa resp. Pilot representation.
	 * 
	 * @param unixTimeMillis timestamp in unix time representation
	 * @return number of seconds since 12:00:00 AM, 1 Jan. 1901, GMT
	 *   as LONG CARDINAL (adjusted for time warp).
	 */
	public static long getMesaTime(long unixTimeMillis) {
		long unixTimeSecs = unixTimeMillis / 1000;
		long mesaSecs = (unixTimeSecs + (731 * 86400) + earliestTime) & 0x00000000FFFFFFFFL;
		return mesaSecs + mesaSecondsAdjust;
	}
}
