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

/**
 * Definition of the generic control codes used as SSTs (Sequence Sub Types) and
 * attention codes for exchanging control and status data over the SSP transmission
 * channel between the GAP clients and the GAP server.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2023)
 */
public class GapCodes {

	/*
	 * ---------------------------------------------------------------------------- SST/OOB constants for transmission control
	 */
	
	public static final int ctl_none = 0300;					/* inband, of course */
	
	public static final int ctl_interrupt = 0301;				/* oob */
	public static final int ctl_resume = 0302;					/* oob */
	
	public static final int ctl_areYouThere = 0304;				/* oob */
	public static final int ctl_iAmHere = 0305;					/* oob */
	
	public static final int ctl_abortGetTransaction = 0306;		/* oob with mark */
	public static final int ctl_abortMark = 0315;				/* inband */
	public static final int ctl_abortPutTransaction = 0307;		/* oob with mark */
	
	public static final int ctl_audibleSignal = 0303;			/* oob */
	
	public static final int ctl_cleanup = 0320;					/* inband, oob */
	public static final int ctl_disconnect = 0312;				/* inband */
	
	public static final int ctl_endOfTransaction = 0310;		/* inband */
	public static final int ctl_endOfTransparentData = 0314;	/* inband */
	public static final int ctl_excessiveRetransmissions = 0333;/* oob with mark */
	public static final int ctl_remoteNotResponding = 0331;		/* oob with mark */
	public static final int ctl_transparentDataFollows = 0313;	/* inband */
	public static final int ctl_yourTurnToSend = 0311;			/* inband */
	
	public static final int ctl_unchained3270 = 0335;			/* inband */
	public static final int ctl_readModified3270 = 0336;		/* inband */
	public static final int ctl_status3270 = 0337;				/* inband */
	public static final int ctl_testRequest3270 = 0340;			/* inband */

	public static final int ctl_sscpData = 0342;				/* inband */
	public static final int ctl_readModifiedAll3270 = 0344;		/* inband */
	public static final int ctl_read3270 = 0345;				/* inband */

	
	/*
	 * ---------------------------------------------------------------------------- OOB status bytes
	 */
	
	public static final int status_mediumDown = 0322;
	public static final int status_mediumUp = 0321;
	public static final int status_noGetForData = 0325;
	public static final int status_ourAccessIDRejected = 0323;
	public static final int status_unsupportedProtocolFeature = 0326;
	public static final int status_unexpectedRemoteBeharior = 0327;
	public static final int status_unexpectedSoftwareFailure = 0330;
	public static final int status_weRejectedAccessID = 0324;
	public static final int status_puActive = 0347;
	public static final int status_puInactive = 0350; // ?? gapcontrols.h: 0359 (which is an invalid octal value!), so we define it as (puActive+1)
	
	/*
	 * ############################################################################# encoding of codes and transmission
	 */
	
	private static final int flagInBand = 0x08000000;
	private static final int flagOutOfBand = 0x04000000;
	private static final int flagEndOfMessage = 0x02000000;
	private static final int flagData = 0x01000000;
	
	private static final int maskCode = 0x000000FF;
	private static final int maskLength = 0x00FFFF00;

	
	/*
	 * ---------------------------------------------------------------------------- encoding
	 */
	
	public static int asData(int length, boolean eom) {
		return flagData | (eom ? flagEndOfMessage : 0) | ((length << 8) & maskLength) | ctl_none;
	}
	
	public static int asInBand(int code, boolean eom) {
		return flagInBand | (eom ? flagEndOfMessage : 0) | (code & maskCode);
	}
	
	public static int asOutOfBand(byte code) {
		return flagOutOfBand | (code & maskCode);
	}

	
	/*
	 * ---------------------------------------------------------------------------- decoding
	 */
	
	public static int code(int coded) { return coded & maskCode; }
	public static boolean isInBand(int coded) { return (coded & flagInBand) != 0; }
	public static boolean isOutOfband(int coded) { return (coded & flagOutOfBand) != 0; }
	
	public static boolean isData(int coded) { return (coded & flagData) != 0; }
	public static boolean isEOM(int coded) { return (coded & flagEndOfMessage) != 0; }
	public static int dataLength(int coded) { return isData(coded) ? (coded & maskLength) >> 8 : 0; }
	
	public static String toString(int coded) {
		if (isData(coded)) {
			return String.format("data[ length = %d, eom = %s ]", dataLength(coded), isEOM(coded));
		}
		
		String band = isInBand(coded) ? " InBand" : " OutOfBand";
		String eom = isEOM(coded) ? " EOM" : "";
		String name;
		switch(code(coded)) {
		case ctl_abortGetTransaction: name = "abortGetTransaction"; break;
		case ctl_abortMark: name = "abortMark"; break;
		case ctl_abortPutTransaction: name = "abortPutTransaction"; break;
		case ctl_areYouThere: name = "areYouThere"; break;
		case ctl_audibleSignal: name = "audibleSignal"; break;
		case ctl_cleanup: name = "cleanup"; break;
		case ctl_disconnect: name = "disconnect"; break;
		case ctl_endOfTransaction: name = "endOfTransaction"; break;
		case ctl_endOfTransparentData: name = "endOfTransparentData"; break;
		case ctl_excessiveRetransmissions: name = "excessiveRetransmissions"; break;
		case ctl_iAmHere: name = "iAmHere"; break;
		case ctl_interrupt: name = "interrupt"; break;
		case ctl_none: name = "none"; break;
		case ctl_remoteNotResponding: name = "remoteNotResponding"; break;
		case ctl_resume: name = "resume"; break;
		case ctl_transparentDataFollows: name = "transparentDataFollows"; break;
		case ctl_yourTurnToSend: name = "yourTurnToSend"; break;

		case ctl_unchained3270: name = "unchained3270"; break;
		case ctl_readModified3270: name = "readModified3270"; break;
		case ctl_status3270: name = "status3270"; break;
		case ctl_testRequest3270: name = "testRequest3270"; break;
		case ctl_sscpData: name = "sscpData"; break;
		case ctl_readModifiedAll3270: name = "readModifiedAll3270"; break;
		case ctl_read3270: name = "read3270"; break;

		case status_mediumDown: name = "mediumDown"; break;
		case status_mediumUp: name = "mediumUp"; break;
		case status_noGetForData: name = "noGetForData"; break;
		case status_ourAccessIDRejected: name = "ourAccessIDRejected"; break;
		case status_puActive: name = "puActive"; break;
		case status_puInactive: name = "puInactive"; break;
		case status_unsupportedProtocolFeature: name = "unsupportedProtocolFeature"; break;
		case status_unexpectedRemoteBeharior: name = "unexpectedRemoteBeharior"; break;
		case status_unexpectedSoftwareFailure: name = "unexpectedSoftwareFailure"; break;
		case status_weRejectedAccessID: name = "weRejectedAccessID"; break;
		
		default:
			name = String.format("undefined(%d,o%04o,x%02X)", coded, coded, coded);
			break;
		}
		
		return String.format("%s[%s%s ]", name, band, eom);
	}
	
}
