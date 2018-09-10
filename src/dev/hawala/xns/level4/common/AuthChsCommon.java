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

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.UNSPECIFIED3;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;

/**
 * Definition of the Courier items common to the Courier
 * programs Clearinghouse and Authentication. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 */
public abstract class AuthChsCommon extends CrProgram {

	/*
	 * Clearinghouse 
	 */
	
	/*
	 * Organization: TYPE = STRING;
	 * Domain: TYPE = STRING;
	 * Object: TYPE = STRING;
	 * 
	 * maxOrganizationsLength: CARDINAL = 20; -- in bytes --
	 * maxDomainLength: CARDINAL = 20; -- in bytes --
	 * maxObjectLength: CARDINAL = 40; -- in bytes --
	 */
	
	public static final int maxOrganizationsLength = 20; // in bytes
	public static final int maxDomainLength = 20; // in bytes
	public static final int maxObjectLength = 40; // in bytes
	
	/*
	 * TwoPartName: TYPE = RECORD [
	 *     organization: Organization,
	 *     domain: Domain];
	 *
	 */
	public static class TwoPartName extends RECORD {
		public final STRING organization = mkSTRING(maxOrganizationsLength);
		public final STRING domain = mkSTRING(maxDomainLength);
		
		private TwoPartName() {}
		public static TwoPartName make() { return new TwoPartName(); }
	}
	
	/*
	 * ThreePartName: TYPE = RECORD [
	 *     organization: Organization,
	 *     domain: Domain,
	 *     object: Object];
	 */
	public static class ThreePartName extends RECORD {
		public final STRING organization = mkSTRING(maxOrganizationsLength);
		public final STRING domain = mkSTRING(maxDomainLength);
		public final STRING object = mkSTRING(maxObjectLength);
		
		private ThreePartName() {}
		public static ThreePartName make() { return new ThreePartName(); }
	}

	/*
	 * NetworkAddress: TYPE = RECORD [
	 *     network: UNSPECIFIED2,
	 *     host: UNSPECIFIED3,
	 *     socket: UNSPECIFIED ];
	 */
	public static class NetworkAddress extends RECORD {
		public final UNSPECIFIED2 network = mkMember(UNSPECIFIED2::create);
		public final UNSPECIFIED3 host    = mkMember(UNSPECIFIED3::create);
		public final UNSPECIFIED  socket  = mkUNSPECIFIED();
		
		private NetworkAddress() {}
		public static NetworkAddress make() { return new NetworkAddress(); } 
	}
	
	/*
	 * Authentication
	 */
	
	/*
	 * CredentialsType: TYPE = {simple(0), strong(1)};
	 */
	public enum CredentialsType { simple, strong };
	public static final EnumMaker<CredentialsType> mkCredentialsType = buildEnum(CredentialsType.class).get();
	
	/*
	 * Credentials: TYPE = RECORD [
	 *     type: CredentialsType,
	 *     value: SEQUENCE OF UNSPECIFIED];
	 */
	public static class Credentials extends RECORD {
		public final ENUM<CredentialsType> type = mkENUM(mkCredentialsType);
		public final SEQUENCE<UNSPECIFIED> value = mkSEQUENCE(UNSPECIFIED::create);
		
		private Credentials() {}
		public static Credentials make() { return new Credentials(); }
	}
	
	/*
	 * Verifier: TYPE = SEQUENCE 12 OF UNSPECIFIED;
	 */
	public static class Verifier extends SEQUENCE<UNSPECIFIED> {
		
		public Verifier() { super(12, UNSPECIFIED::create); }
		
		public static Verifier make() { return new Verifier(); }
		
	}
	
	/*
	 * Problem: TYPE = {
     *     credentialsInvalid(0),		-- credentials unacceptable --
     *     verifierInvalid(1),			-- verifier unacceptable --
     *     verifierExpired(2),			-- the verifier was too old --
     *     verifierReused(3),			-- the verifier has been used before --
     *     credentialsExpired(4),		-- the credentials have expired --
     *     inappropriateCredentials(5),	-- passed strong, wanted simple, or vica versa --
     *     proxyInvalid(6),				-- proxy has invalid format --
     *     proxyExpired(7),				-- the proxy was too old --
     *     otherProblem(8) };
	 */
	public enum Problem {
		credentialsInvalid,
	    verifierInvalid,
	    verifierExpired,
	    verifierReused,
	    credentialsExpired,
	    inappropriateCredentials,
	    // only from VERSION 3 upwards
	    proxyInvalid,
	    proxyExpired,
	    otherProblem
	};
	public static final EnumMaker<Problem> mkProblem = buildEnum(Problem.class).get();
	
	/*
	 * for procedure RetrieveAddresses (same PROC = 0 in Clearinghouse and Authentication)
	 */
	
	/*
	 * RetrieveAddresses: PROCEDURE
	 * RETURNS [address: NetworkAddressList]
	 * REPORTS [CallError] = 0;
	 */
	
	public static class RetrieveAddressesResult extends RECORD {
		public final SEQUENCE<NetworkAddress> address = mkSEQUENCE(40, NetworkAddress::make);
		
		private RetrieveAddressesResult() {}
		public static RetrieveAddressesResult make() { return new RetrieveAddressesResult(); }
	}

	
	
	/*
	 * ***************** non-Courier public methods
	 */
	
	/**
	 * Check that the password encoded in the {@code verifier} is the
	 * username ({@code credentials.value.object} of the simple credentials. 
	 * @param credentials the simple credentials (3-part username)
	 * @param verifier the verifier (hashed password)
	 * @return {@code false} if {@code credentials} is not 'simple, if the
	 * 		{@code verifier} is not a hashed password (i.e. length != 1)
	 * 		or the hashed object-name part in {@code credentials} is not
	 * 		the hash value in {@code verifier}, else {@code true}.
	 * @throws EndOfMessageException
	 */
	public static boolean simpleCheckPasswordForSimpleCredentials(Credentials credentials, Verifier verifier) throws EndOfMessageException {
		if (credentials.type.get() != CredentialsType.simple) {
			return false;
		}
		if (verifier.size() != 1) {
			return false;
		}
		
		int verifierHash = verifier.get(0).get();
		
		WireSeqOfUnspecifiedReader credReader = new WireSeqOfUnspecifiedReader(credentials.value);
		ThreePartName credsObject = ThreePartName.make();
		credsObject.deserialize(credReader);
		int usernameHash = computePasswdHash(credsObject.object.get());
		
		StringBuilder sb = new StringBuilder();
		String credsObjectString = credsObject.append(sb, "", "credentials.value").toString();
		Log.C.printf("AuthChs", "AuthChsCommon.simpleCheckPasswordForSimpleCredentials(), %s \n", credsObjectString);
		
		return (verifierHash == usernameHash);
	}
	
	private static int computePasswdHash(String password) {
		String passwd = password.toLowerCase();
		int hash = 0;
		for (int i = 0; i < passwd.length(); i++) {
			char c = passwd.charAt(i);
			int cv = c;
			hash = ((hash << 16) + cv) % 65357;
		}
		return hash;
	}

}
