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

package dev.hawala.xns.level4.chs;

import dev.hawala.xns.Log;
import dev.hawala.xns.level1.IDP;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.StreamOf;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.chs.Clearinghouse3.AuthenticationErrorRecord;
import dev.hawala.xns.level4.chs.Clearinghouse3.CallErrorRecord;
import dev.hawala.xns.level4.chs.Clearinghouse3.CallProblem;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.common.AuthChsCommon.Problem;
import dev.hawala.xns.level4.common.AuthChsCommon.RetrieveAddressesResult;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.TwoPartName;
import dev.hawala.xns.level4.common.ChsDatabase;

/**
 * Implementation of the Clearinghouse Courier protocols defined
 * in classes {@code Clearinghouse3} and {@code Clearinghouse2}. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
 *
 */
public class Clearinghouse3Impl {
	
	/*
	 * ************************* collection of data to respond (very rudimentary)
	 */
	
	// same defaults as in LocalSite (duplicated to avoid dependency)
	private static long networkId = 0x0001_0120;
	private static long machineId = 0x0000_1000_FF12_3401L;
	
	// the clearinghouse database
	private static ChsDatabase chsDatabase = null;
	
	/**
	 * Set values to use for responses
	 * 
	 * @param network networkId for 'retrieveAddresses'
	 * @param machine hostId for 'retrieveAddresses'
	 */
	public static void init(long network, long machine, ChsDatabase chsDb) {
		networkId = network;
		machineId = machine;
		chsDatabase = chsDb;
	}
	
	/*
	 * ************************* registration/deregistration
	 */
	
	/**
	 * register Courier-Programs Clearinghouse2 and Clearinghouse3 with
	 * this implementation to Courier dispatcher.
	 */
	public static void register() {
		Clearinghouse2 v2 = new Clearinghouse2();
		Clearinghouse3 v3 = new Clearinghouse3();
		
		v2.RetrieveAddresses.use(Clearinghouse3Impl::retrieveAddresses);
		v3.RetrieveAddresses.use(Clearinghouse3Impl::retrieveAddresses);
		
		v2.ListDomainsServed.use(Clearinghouse3Impl::listDomainsServed);
		v3.ListDomainsServed.use(Clearinghouse3Impl::listDomainsServed);
		
		v2.LookupObject.use(Clearinghouse3Impl::lookupObject);
		v3.LookupObject.use(Clearinghouse3Impl::lookupObject);
		
		// TODO: define remaining procedures in the courier program 
		
		CourierRegistry.register(v2);
		CourierRegistry.register(v3);
	}
	
	/**
	 * unregister Clearinghouse3 implementation from Courier dispatcher
	 */
	public static void unregister() {
		CourierRegistry.unregister(Clearinghouse3.PROGRAM, Clearinghouse3.VERSION);
	}
	
	/**
	 * Get the "Broadcast for Server" implementation for Clearinghouse3 requests
	 * 
	 * @return the minimal service implementation for Bfs
	 */
	public static Clearinghouse3 getBfsImplementationVersion3() {
		Clearinghouse3 prog = new Clearinghouse3();
		prog.RetrieveAddresses.use(Clearinghouse3Impl::retrieveAddresses);
		return prog;
	}
	
	/**
	 * Get the "Broadcast for Server" implementation for Clearinghouse2 requests
	 * 
	 * @return the minimal service implementation for Bfs
	 */
	public static Clearinghouse2 getBfsImplementationVersion2() {
		Clearinghouse2 prog = new Clearinghouse2();
		prog.RetrieveAddresses.use(Clearinghouse3Impl::retrieveAddresses);
		return prog;
	}
	
	/*
	 * ************************* implementation of service procedures
	 */
	
	/*
	 * RetrieveAddresses: PROCEDURE
	 *   RETURNS [address: NetworkAddressList]
	 *   REPORTS [CallError] = 0;
	 */

	public static void retrieveAddresses(
						RECORD params,
						RetrieveAddressesResult results) {
		// add the only known server to the address list: ourself
		NetworkAddress addr = results.address.add();
		addr.network.set((int)(networkId & 0xFFFFFFFFL));
		addr.host.set(machineId);
		addr.socket.set(IDP.KnownSocket.COURIER.getSocket()); // unclear what local socket is meant...
	}
	
	/*
	 * ListDomainServed: PROCEDURE [domains: BulkData.Sink, agent: Authenticator]
	 *   REPORTS [AuthenticationError, CallError] = 1;
	 *   
	 * problem: there is no definitive specification of what is to be
	 *          transported through the bulk data transfer...
	 *          ... according to "bfsgetdoms.c" it is: StreamOfDomainName 
	 */
	public static void listDomainsServed(
						Clearinghouse3.ListDomainServedParams params,
						RECORD results) {
		Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: start\n");
		
		// create data for transfer the single domain we serve
		StreamOf<TwoPartName> streamData = new StreamOf<>(0, 1, 2, TwoPartName::make);
		TwoPartName name1 = streamData.add();
		name1.organization.set(chsDatabase.getOrganizationName());
		name1.domain.set(chsDatabase.getDomainName());

		Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: sending data via bulk data transfer\n");
		try {
			params.domains.send(streamData);
		} catch (NoMoreWriteSpaceException e) {
			Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: NoMoreWriteSpaceException during BDT.send()\n");
			CallErrorRecord err = new CallErrorRecord(CallProblem.other);
			err.raise();
		}
		
		Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: end\n");
	}
	
	/*
	 * LookupObject: PROCEDURE [name: ObjectNamePattern, agent: Authenticator]
	 *   RETURNS [distinguishedObject: ObjectName]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 4;
	 */
	public static void lookupObject(
						Clearinghouse3.LookupObjectParams params,
						Clearinghouse3.LookupObjectResults results) {
		StringBuilder sb = new StringBuilder();
		String paramsString = params.append(sb, "", "params").toString();
		Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject(), %s \n", paramsString);
		
		// check if it is simpleAuth, reject if not else analyze the content 
		if (params.agent.credentials.type.get() != CredentialsType.simple) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.inappropriateCredentials);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() -> rejecting with AuthenticationError[inappropriateCredentials]\n");
			err.raise();
		}
		boolean credentialsOk = false;
		try {
			credentialsOk = AuthChsCommon.simpleCheckPasswordForSimpleCredentials(
								chsDatabase,
								params.agent.credentials,
								params.agent.verifier);
		} catch (IllegalArgumentException iac) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() IllegalArgumentException (name not existing) -> rejecting with AuthenticationError[credentialsInvalid]\n");
			err.raise();
		} catch (EndOfMessageException e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.inappropriateCredentials);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() EndOfMessageException when deserializing credsObject -> rejecting with AuthenticationError[inappropriateCredentials]\n");
			err.raise();
		}
		if (!credentialsOk) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() -> rejecting with AuthenticationError[credentialsInvalid]\n");
			err.raise();
		}
		
		/*
		 * resolve the name to lookup to the primary distinguished name, rejecting patern lookups (not supportd so far)
		 * TODO: add pattern matching 
		 */
		boolean isPatternLookup = 
				params.name.object.get().contains("*")
			 || params.name.domain.get().contains("*")
			 || params.name.organization.get().contains("*");
		
		if (isPatternLookup) {
			Clearinghouse3.ArgumentErrorRecord err = new Clearinghouse3.ArgumentErrorRecord(
					Clearinghouse3.ArgumentProblem.noSuchObject,
					Clearinghouse3.WhichArgument.first);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() -> ArgumentErrorRecord[noSuchObject,first], reason: pattern\n");
			err.raise();
		} else {
			ThreePartName dn = (chsDatabase == null) ? params.name : chsDatabase.getDistinguishedName(params.name);
			if (dn == null) {
				Clearinghouse3.ArgumentErrorRecord err = new Clearinghouse3.ArgumentErrorRecord(
						Clearinghouse3.ArgumentProblem.noSuchObject,
						Clearinghouse3.WhichArgument.first);
				Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() -> ArgumentErrorRecord[noSuchObject,first], reason: not found in CHS\n");
				err.raise();
			}
			
			results.distinguishedObject.object.set(dn.object.get());
			results.distinguishedObject.domain.set(dn.domain.get());
			results.distinguishedObject.organization.set(dn.organization.get());
			sb.setLength(0);
			String resultsString = results.append(sb, "", "results").toString();
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject(), %s \n", resultsString);
		}
	}
	
}
