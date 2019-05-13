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
import dev.hawala.xns.level3.courier.ARRAY;
import dev.hawala.xns.level3.courier.CrProgram;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.UNSPECIFIED2;
import dev.hawala.xns.level3.courier.UNSPECIFIED3;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level4.common.Time2.Time;

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
	 * DomainName: TYPE = TwoPartName;
	 */
	public static class DomainName extends TwoPartName {
		private DomainName() {}
		public static DomainName make() { return new DomainName(); }
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
		
		public ThreePartName from(String objName, String domName, String orgName) {
			this.organization.set(orgName);
			this.domain.set(domName);
			this.object.set(objName);
			return this;
		}
		
		public ThreePartName from(String fqn) {
			String[] parts = fqn.split(":");
			if (parts.length > 3) {
				throw new IllegalArgumentException("Full qualified (distinguished) name has more than 3 components");
			}
			return this.from(
						(parts.length > 0) ? parts[0] : "",
						(parts.length > 1) ? parts[1] : "", 
						(parts.length > 2) ? parts[2] : ""
						);
		}
		
		public ThreePartName from(ThreePartName name) {
			return this.from(name.object.get(), name.domain.get(), name.organization.get());
		}
		
		public String getLcFqn() {
			return this.getString().toLowerCase(); 
		}
		
		public String getString() {
			return this.object.get() + ":" + this.domain.get() + ":" + this.organization.get();
		}
		
		@Override
		public String toString() {
			return this.getString();
		}
	}
	
	/*
	 * ObjectName: TYPE = ThreePartName;
	 */
	public static class ObjectName extends ThreePartName {
		private ObjectName() {}
		public static ObjectName make() { return new ObjectName(); }
	}
	
	/*
	 * Name: TYPE = ThreePartName;
	 */
	public static class Name extends ThreePartName {
		private Name() {}
		public static Name make() { return new Name(); }
	}
	
	/*
	 * ObjectNamePattern: TYPE = ThreePartName;
	 */
	public static class ObjectNamePattern extends ThreePartName {
		private ObjectNamePattern() {}
		public static ObjectNamePattern make() { return new ObjectNamePattern(); }
	}

	/*
	 * NetworkAddress: TYPE = RECORD [
	 *     network: UNSPECIFIED2,
	 *     host: UNSPECIFIED3,
	 *     socket: UNSPECIFIED ];
	 */
	public static class NetworkAddress extends RECORD {
		public final UNSPECIFIED2    network = mkMember(UNSPECIFIED2::make);
		public final UNSPECIFIED3    host    = mkMember(UNSPECIFIED3::make);
		public final UNSPECIFIED     socket  = mkUNSPECIFIED();
		
		private NetworkAddress() {}
		public static NetworkAddress make() { return new NetworkAddress(); } 
	}
	
	public static class NetworkAddressList extends SEQUENCE<NetworkAddress> {
		private NetworkAddressList() { super(40, NetworkAddress::make); }
		public static NetworkAddressList make() { return new NetworkAddressList(); } 
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
		public final SEQUENCE<UNSPECIFIED> value = mkSEQUENCE(UNSPECIFIED::make);
		
		private Credentials() {}
		public static Credentials make() { return new Credentials(); }
	}
	
	/*
	 * Verifier: TYPE = SEQUENCE 12 OF UNSPECIFIED;
	 */
	public static class Verifier extends SEQUENCE<UNSPECIFIED> {
		
		public Verifier() { super(12, UNSPECIFIED::make); }
		public static Verifier make() { return new Verifier(); }
		
	}
	
	/*
	 * Key: TYPE = ARRAY 4 OF UNSPECIFIED;  -- lsb of each octet is odd parity bit --
	 */
	public static class Key extends ARRAY<UNSPECIFIED> {
		private Key() { super(4, UNSPECIFIED::make); }
		public static Key make() { return new Key(); }
	}
	
	/*
	 * Block: TYPE = ARRAY 4 OF UNSPECIFIED;  -- cipher text or plain text block --
	 */
	public static class Block extends ARRAY<UNSPECIFIED> {
		private Block() { super(4, UNSPECIFIED::make); }
		public static Block make() { return new Block(); }
	}
	
	/*
	 * StrongCredentials: TYPE = RECORD [
	 *     conversationKey: Key,
	 *     expirationTime: Time.Time,
	 *     initiator: Clearinghouse_Name ];
	 */
	public static class StrongCredentials extends RECORD {
		public final Key             conversationKey = mkMember(Key::make);
		public final Time            expirationTime = mkMember(Time::make);
		public final Name            initiator = mkMember(Name::make);
		
		private StrongCredentials() {}
		public static StrongCredentials make() { return new StrongCredentials(); }
	}
	
	/*
	 * StrongVerifier: TYPE = RECORD [
	 *     timeStamp: Time.Time,
	 *     ticks: LONG CARDINAL ];
	 */
	public static class StrongVerifier extends RECORD {
		public final Time            timeStamp = mkMember(Time::make);
		public final LONG_CARDINAL   ticks = mkLONG_CARDINAL();
		
		private StrongVerifier() {}
		public static StrongVerifier make() { return new StrongVerifier(); }
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
		public final NetworkAddressList address = mkMember(NetworkAddressList::make);
		
		private RetrieveAddressesResult() {}
		public static RetrieveAddressesResult make() { return new RetrieveAddressesResult(); }
	}

	
	
	/*
	 * ***************** non-Courier public methods
	 */
	
	private static boolean skipTimestampChecks = false;
	
	public static void skipTimestampChecks() {
		skipTimestampChecks = true;
	}
	
	/**
	 * Check that the password encoded in the {@code verifier} is the
	 * username ({@code credentials.value.object} of the simple credentials.
	 * @param chsDatabase the clearinghouse database to check against
	 * @param credentials the simple credentials (3-part username)
	 * @param verifier the verifier (hashed password)
	 * @return {@code null} if {@code credentials} is not 'simple', if the
	 * 		{@code verifier} is not a hashed password (i.e. length != 1)
	 * 		or the hashed object-name part in {@code credentials} is not
	 * 		the hash value in {@code verifier}, else the Clearinghouse name
	 *      for the user.
	 * @throws EndOfMessageException
	 */
	public static ThreePartName simpleCheckPasswordForSimpleCredentials(ChsDatabase chsDatabase, Credentials credentials, Verifier verifier) throws EndOfMessageException {
		if (credentials.type.get() != CredentialsType.simple) {
			return null;
		}
		if (verifier.size() != 1) {
			return null;
		}
		
		int verifierHash = verifier.get(0).get();
		
		WireSeqOfUnspecifiedReader credReader = new WireSeqOfUnspecifiedReader(credentials.value);
		Name credsObject = Name.make();
		credsObject.deserialize(credReader);
		int usernameHash = chsDatabase.getSimplePassword(credsObject);
		
		StringBuilder sb = new StringBuilder();
		String credsObjectString = credsObject.append(sb, "", "credentials.value").toString();
		Log.C.printf("AuthChs", "AuthChsCommon.simpleCheckPasswordForSimpleCredentials(), %s \n", credsObjectString);
		
		System.out.printf(
				"*** simpleCheckPasswordForSimpleCredentials() for '%s:%s:%s' :: verifierHash = %d , userHash = %d\n",
				credsObject.object.get(), credsObject.domain.get(), credsObject.organization.get(),
				verifierHash, usernameHash);
		
		return (verifierHash == usernameHash) ? credsObject : null;
	}
	
	/**
	 * Check that the strong credentials and the strong verifier are
	 * both valid for the recipient on the given machine.
	 * 
	 * @param chsDatabase the clearinghouse database to check against
	 * @param credentials the credentials to verify
	 * @param verifier the verifier going with the credentials
	 * @param recipient the recipient for which the the strong credentials are encoded
	 * @param recipientMachineId the target machine for which the verifier is encoded
	 * @param decodedConversationKey target where to store the conversation encryption key for
	 *   the session (the key will only be stored there if not {@code null} and at least
	 *   4 entries long).
	 * @return {@code null} if the credentials is not of strong type or the
	 *   initiator encoded in the credentials is invalid or if the expiration time
	 *   of the credentials are expired or the verifier timestamp is invalid;
	 *   else the Clearinghouse name of the user if the credentials passed the tests.
	 * @throws EndOfMessageException if decoding the credentials or verifier after
	 *    decryption fails
	 * @throws IllegalArgumentException if the recipient is invalid or has no
	 *    strong password for decryption
	 * @throws Exception if any decryption fails
	 */
	public static ThreePartName checkStrongCredentials(
							ChsDatabase chsDatabase,
							Credentials credentials,
							Verifier verifier,
							ThreePartName recipient,
							long recipientMachineId,
							int[] decodedConversationKey,
							StrongVerifier decodedVerifier) throws Exception {
		// get the recipient decryption password
		if (credentials.type.get() != CredentialsType.strong) {
			return null;
		}
		byte[] recipientStrongPw = chsDatabase.getStrongPassword(recipient);
		if (recipientStrongPw == null) {
			throw new IllegalArgumentException("Invalid recipient (strong password not found)");
		}
		int[] recipientDecryptPw = StrongAuthUtils.toWords(recipientStrongPw);
		
		// decode the credentials with the recipient's strong password
		StrongCredentials creds = StrongCredentials.make();
		decryptFrom(recipientDecryptPw, credentials.value, creds);
		
		// decrypt the verifier
		if (decodedConversationKey == null || decodedConversationKey.length < 4) {
			decodedConversationKey = new int[4];
		}
		decodedConversationKey[0] = creds.conversationKey.get(0).get();
		decodedConversationKey[1] = creds.conversationKey.get(1).get();
		decodedConversationKey[2] = creds.conversationKey.get(2).get();
		decodedConversationKey[3] = creds.conversationKey.get(3).get();
		StrongVerifier verfr = StrongVerifier.make();
		decryptFrom(decodedConversationKey, verifier, verfr);
		long rcptTimestampMachineId32Bits = (recipientMachineId >> 16) & 0xFFFFFFFFL; // left justified machine-id => upper 32 bits 
		long rcptTicksMachineId32Bits = (recipientMachineId & 0x0000FFFFL) << 16;     // left justified machine-id => lower 32 bits
		long verifierTicks = verfr.ticks.get() ^ rcptTicksMachineId32Bits;
		long verifierTimestamp = verfr.timeStamp.get() ^ rcptTimestampMachineId32Bits;
		if (decodedVerifier != null) {
			decodedVerifier.ticks.set(verifierTicks);
			decodedVerifier.timeStamp.set(verifierTimestamp);
		}
		
		// (temp) log the relevant data
		Time now = Time.make().now();
		System.out.printf(
				"creds.initiator: %s:%s:%s\n",
				creds.initiator.object.get(),
				creds.initiator.domain.get(),
				creds.initiator.organization.get());
		System.out.printf(
				"creds.expiration: %d (now: %d)\n",
				creds.expirationTime.get(),
				now.get());
		System.out.printf(
				"verifier.timeStamp: 0x%08X = %d -> xor-ed(machineId): 0x%08X = %s (now: 0x%08X =  %d)\n",
				verfr.timeStamp.get(), verfr.timeStamp.get(),
				verifierTimestamp, verifierTimestamp,
				now.get(), now.get());
		System.out.printf(
				"verifier.ticks: 0x%08X = %d -> xor-ed(machineId): 0x%08X = %d\n",
				verfr.ticks.get(), verfr.ticks.get(), verifierTicks, verifierTicks);
		
		
		// check the credentials / verifier
		if (!chsDatabase.isValidName(creds.initiator)) {
			System.out.println("** checkStrongCredentials() => ERR: creds.initiator is not a valid name");
			return null;
		}
		if (!skipTimestampChecks) {
			if (now.get() > creds.expirationTime.get()) {
				System.out.println("** checkStrongCredentials() => ERR: now > creds.expirationTime");
				return null;
			}
			if (now.get() < verifierTimestamp) {
				System.out.println("** checkStrongCredentials() => ERR: now < verifierTimestamp");
				return null;
			}
			if (now.get() > (verifierTimestamp + 60)) {
				System.out.println("** checkStrongCredentials() => ERR: now > verifierTimestamp+60secs");
				return null;
			}
		} else {
			System.out.println("** checkStrongCredentials() => timestamp checks skipped (creds.expirationTime, verifier.timestamp)");
		}
		
		System.out.println("** checkStrongCredentials() => strong credentials OK");
		return new ThreePartName().from(creds.initiator);
	}
	
	private static void decryptFrom(int[] strongPw, SEQUENCE<UNSPECIFIED> data, iWireData target) throws Exception {
		int[] rawData = new int[data.size()];
		for (int i = 0; i < rawData.length; i++) {
			rawData[i] = data.get(i).get();
		}
		int[] decryptedData = StrongAuthUtils.xnsDesDecrypt(strongPw, rawData);
		iWireStream dataStream = new WireSeqOfUnspecifiedReader(decryptedData);
		target.deserialize(dataStream);
	}

}
