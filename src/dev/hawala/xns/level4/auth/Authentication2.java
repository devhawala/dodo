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

package dev.hawala.xns.level4.auth;

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level4.common.AuthChsCommon;

/**
 * Definition of the (so far supported) functionality
 * of the Courier Authentication program (PROGRAM 14 VERSION 2).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public class Authentication2 extends AuthChsCommon {
	
	public static final int PROGRAM = 14;
	public static final int VERSION = 2;
	
	public int getProgramNumber() { return PROGRAM; }
	
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * ********* plain data structures 
	 */
	
	// none so far
	
	
	/*
	 * ********* errors
	 */
	
	/*
	 * CallProblem: TYPE = {
     *     tooBusy(0),
     *     accessRightsInsufficient(1),
     *     keysUnavailable(2),
     *     strongKeyDoesNotExist(3),
     *     simpleKeyDoesNotExist(4),
     *     strongKeyAlreadyRegistered(5),
     *     simpleKeyAlreadyRegistered(6),
     *     domainForNewKeyUnavailable(7),
     *     domainForNewKeyUnknown(8),
     *     badKey(9),
     *     badName(10),
     *     databaseFull(11),
     *     other(12) };
	 * Which: TYPE = {notApplicable(0), initiator(1), recipient(2), client(3) };
	 * CallError: ERROR [problem: CallProblem, whichArg: Which] = 1;
	 */
	
	public enum CallProblem {
		tooBusy,
	    accessRightsInsufficient,
	    keysUnavailable,
	    strongKeyDoesNotExist,
	    simpleKeyDoesNotExist,
	    strongKeyAlreadyRegistered,
	    simpleKeyAlreadyRegistered,
	    domainForNewKeyUnavailable,
	    domainForNewKeyUnknown,
	    badKey,
	    badName,
	    databaseFull,
	    other
	}
	public static final EnumMaker<CallProblem> mkCallProblem = buildEnum(CallProblem.class).get();
	
	public enum Which { notApplicable, initiator, recipient, client }
	public static final EnumMaker<Which> mkWhich = buildEnum(Which.class).get();
	
	public static class CallErrorRecord extends ErrorRECORD {
		
		public final ENUM<CallProblem> problem = mkENUM(mkCallProblem);
		public final ENUM<Which> whichArg = mkENUM(mkWhich);

		@Override
		public int getErrorCode() { return 1; }
		
		public CallErrorRecord(CallProblem problem, Which whichArg) {
			this.problem.set(problem);
			this.whichArg.set(whichArg);
		}
		
	}
	
	public final ERROR<CallErrorRecord> CallError = mkERROR(CallErrorRecord.class);
	
	/*
	 * Problem: TYPE = {
     *     credentialsInvalid(0),		-- credentials unacceptable --
     *     verifierInvalid(1),			-- verifier unacceptable --
     *     verifierExpired(2),			-- the verifier was too old --
     *     verifierReused(3),			-- the verifier has been used before --
     *     credentialsExpired(4),		-- the credentials have expired --
     *     inappropriateCredentials(5),	-- passed strong, wanted simple, or vica versa --
     * -- from Version 3 on:
     *     proxyInvalid(6),				-- proxy has invalid format --
     *     proxyExpired(7),				-- the proxy was too old --
     *     otherProblem(8) };
	 * AuthenticationError: ERROR[problem: Problem] = 2;
	 */
	
	public enum Problem {
		credentialsInvalid,
	    verifierInvalid,
	    verifierExpired,
	    verifierReused,
	    credentialsExpired,
	    inappropriateCredentials,
	    proxyInvalid,
	    proxyExpired,
	    otherProblem 
	}
	public static final EnumMaker<Problem> mkProblem = buildEnum(Problem.class).get();
	
	public static class AuthenticationErrorRecord extends ErrorRECORD {
		
		public final ENUM<Problem> problem = mkENUM(mkProblem);

		@Override
		public int getErrorCode() { return 2; }
		
	}
	
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	/*
	 * ********* procedures
	 */
	
	/*
	 * in analogy to Clearinghouse...
	 */
	public final PROC<RECORD,RetrieveAddressesResult> RetrieveAddresses = mkPROC(
							0,
							RECORD::empty,
							RetrieveAddressesResult::make,
							CallError);
	
	
	/*
	 * GetStrongCredentials: PROCEDURE [
	 *		initiator, recipient: Clearinghouse_Name,
	 *		nonce: LONG CARDINAL ]
	 *	RETURNS [ credentialsPackage: SEQUENCE OF UNSPECIFIED ]
	 * 	REPORTS [ CallError ] = 1;
	 */
	public static class GetStrongCredentialsParams extends RECORD {
		public final ThreePartName initiator = mkRECORD(AuthChsCommon.ThreePartName::make);
		public final ThreePartName recipient = mkRECORD(AuthChsCommon.ThreePartName::make);
		public final LONG_CARDINAL nonce = mkLONG_CARDINAL();
		
		private GetStrongCredentialsParams() {}
		public static GetStrongCredentialsParams make() { return new GetStrongCredentialsParams(); }
	}
	public static class GetStrongCredentialsResults extends RECORD {
		public final SEQUENCE<UNSPECIFIED> credentialsPackage = mkSEQUENCE(UNSPECIFIED::create);
		
		private GetStrongCredentialsResults() {}
		public static GetStrongCredentialsResults make() { return new GetStrongCredentialsResults(); }
	}
	public final PROC<GetStrongCredentialsParams,GetStrongCredentialsResults> GetStrongCredentials = mkPROC(
							1,
							GetStrongCredentialsParams::make,
							GetStrongCredentialsResults::make,
							CallError);
	
	
	/*
	 * CheckSimpleCredentials: PROCEDURE [credentials: Credentials, verifier: Verifier ]
	 *  RETURNS[ok: BOOLEAN]
	 *  REPORTS[AuthenticationError, CallError] = 2;
	 */
	public static class CheckSimpleCredentialsParams extends RECORD {
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private CheckSimpleCredentialsParams() {}
		public static CheckSimpleCredentialsParams make() { return new CheckSimpleCredentialsParams(); }
	}
	public static class CheckSimpleCredentialsResults extends RECORD {
		public final BOOLEAN ok = mkBOOLEAN();
		
		private CheckSimpleCredentialsResults() {}
		public static CheckSimpleCredentialsResults make() { return new CheckSimpleCredentialsResults(); }
	}
	public final PROC<CheckSimpleCredentialsParams,CheckSimpleCredentialsResults> CheckSimpleCredentials = mkPROC(
							2,
							CheckSimpleCredentialsParams::make,
							CheckSimpleCredentialsResults::make,
							AuthenticationError,
							CallError); 
	
}
