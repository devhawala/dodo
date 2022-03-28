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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import dev.hawala.xns.Log;
import dev.hawala.xns.level3.courier.CourierRegistry;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.StreamOf;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.chs.Clearinghouse3.AddDeleteMemberParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.AddDeleteSelfParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.AddGroupPropertyParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.AddItemPropertyParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ArgumentProblem;
import dev.hawala.xns.level4.chs.Clearinghouse3.AuthenticationErrorRecord;
import dev.hawala.xns.level4.chs.Clearinghouse3.Authenticator;
import dev.hawala.xns.level4.chs.Clearinghouse3.CallErrorRecord;
import dev.hawala.xns.level4.chs.Clearinghouse3.CallProblem;
import dev.hawala.xns.level4.chs.Clearinghouse3.ChangeItemParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.CreateAliasParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.DeleteAliasParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.DeletePropertyParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.DistinguishedObjectResults;
import dev.hawala.xns.level4.chs.Clearinghouse3.IsMemberParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.IsMemberResults;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListAliasesParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListDomainParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListObjectsOrAliasesParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListOrganizationsParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListPropertiesParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.ListPropertiesResults;
import dev.hawala.xns.level4.chs.Clearinghouse3.PropertyProblem;
import dev.hawala.xns.level4.chs.Clearinghouse3.RetrieveItemParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.RetrieveItemResults;
import dev.hawala.xns.level4.chs.Clearinghouse3.RetrieveMembersParams;
import dev.hawala.xns.level4.chs.Clearinghouse3.WhichArgument;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.AuthChsCommon.CredentialsType;
import dev.hawala.xns.level4.common.AuthChsCommon.Name;
import dev.hawala.xns.level4.common.AuthChsCommon.NetworkAddress;
import dev.hawala.xns.level4.common.AuthChsCommon.ObjectName;
import dev.hawala.xns.level4.common.AuthChsCommon.Problem;
import dev.hawala.xns.level4.common.AuthChsCommon.RetrieveAddressesResult;
import dev.hawala.xns.level4.common.AuthChsCommon.ThreePartName;
import dev.hawala.xns.level4.common.AuthChsCommon.TwoPartName;
import dev.hawala.xns.level4.common.BulkData1;
import dev.hawala.xns.level4.common.ChsDatabase;

/**
 * Implementation of the Clearinghouse Courier protocols defined
 * in classes {@code Clearinghouse3} and {@code Clearinghouse2}. 
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019)
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
	
	// the list of hosts to respond in Bfs calls
	private static final List<NetworkAddress> bfsHosts = new ArrayList<>();
	
	/**
	 * Set values to use for responses, BfS requests will use the single host
	 * {@code network}/{@code machine} as response.
	 * 
	 * @param network networkId for the local network, also used for for 'retrieveAddresses'
	 * @param machine hostId of the local machine, also used for for 'retrieveAddresses'
	 * @param chsDb clearinghouse database to use for courier requests
	 */
	public static void init(long network, long machine, ChsDatabase chsDb) {
		init(network, machine, chsDb, null);
	}
	
	/**
	 * Set values to use for responses.
	 * 
	 * @param network networkId for the local network
	 * @param machine hostId of the local machine
	 * @param chsDb clearinghouse database to use for courier requests
	 * @param hostIds explicit list of network addresses to respond BfS requests with
	 * 		(if the local machine is to be part of the BfS responses, the host
	 * 		{@code network}/{@code machine} must be explicitely added to {@code hostIds})
	 */
	public static void init(long network, long machine, ChsDatabase chsDb, List<NetworkAddress> hostIds) {
		networkId = network;
		machineId = machine;
		chsDatabase = chsDb;
		
		bfsHosts.clear();
		if (hostIds == null || hostIds.isEmpty()) {
			// add the only known server to the address list: ourself
			NetworkAddress addr = NetworkAddress.make();
			addr.network.set((int)networkId);
			addr.host.set(machineId);
			addr.socket.set(0);
			bfsHosts.add(addr);
		} else {
			bfsHosts.addAll(hostIds);
		}
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
		
		v2.CreateObject.use(Clearinghouse3Impl::createObject);
		v3.CreateObject.use(Clearinghouse3Impl::createObject);
		
		v2.DeleteObject.use(Clearinghouse3Impl::deleteObject);
		v3.DeleteObject.use(Clearinghouse3Impl::deleteObject);
		
		v2.LookupObject.use(Clearinghouse3Impl::lookupObject);
		v3.LookupObject.use(Clearinghouse3Impl::lookupObject);
		
		v2.ListOrganizations.use(Clearinghouse3Impl::listOrganizations);
		v3.ListOrganizations.use(Clearinghouse3Impl::listOrganizations);
		
		v2.ListDomain.use(Clearinghouse3Impl::listDomain);
		v3.ListDomain.use(Clearinghouse3Impl::listDomain);
		
		v2.ListObjects.use(Clearinghouse3Impl::listObjects);
		v3.ListObjects.use(Clearinghouse3Impl::listObjects);
		
		v2.ListAliases.use(Clearinghouse3Impl::listAliases);
		v3.ListAliases.use(Clearinghouse3Impl::listAliases);
		
		v2.ListAliasesOf.use(Clearinghouse3Impl::listAliasesOf);
		v3.ListAliasesOf.use(Clearinghouse3Impl::listAliasesOf);
		
		v2.CreateAlias.use(Clearinghouse3Impl::createAlias);
		v3.CreateAlias.use(Clearinghouse3Impl::createAlias);
		
		v2.DeleteAlias.use(Clearinghouse3Impl::deleteAlias);
		v3.DeleteAlias.use(Clearinghouse3Impl::deleteAlias);
		
		v2.AddGroupProperty.use(Clearinghouse3Impl::addGroupProperty);
		v3.AddGroupProperty.use(Clearinghouse3Impl::addGroupProperty);
		
		v2.AddItemProperty.use(Clearinghouse3Impl::addItemProperty);
		v3.AddItemProperty.use(Clearinghouse3Impl::addItemProperty);
		
		v2.DeleteProperty.use(Clearinghouse3Impl::deleteProperty);
		v3.DeleteProperty.use(Clearinghouse3Impl::deleteProperty);
		
		v2.ListProperties.use(Clearinghouse3Impl::listProperties);
		v3.ListProperties.use(Clearinghouse3Impl::listProperties);
		
		v2.RetrieveItem.use(Clearinghouse3Impl::retrieveItem);
		v3.RetrieveItem.use(Clearinghouse3Impl::retrieveItem);
		
		v2.ChangeItem.use(Clearinghouse3Impl::changeItem);
		v3.ChangeItem.use(Clearinghouse3Impl::changeItem);
		
		v2.RetrieveMembers.use(Clearinghouse3Impl::retrieveMembers);
		v3.RetrieveMembers.use(Clearinghouse3Impl::retrieveMembers);
		
		v2.AddMember.use(Clearinghouse3Impl::addMember);
		v3.AddMember.use(Clearinghouse3Impl::addMember);
		
		v2.AddSelf.use(Clearinghouse3Impl::addSelf);
		v3.AddSelf.use(Clearinghouse3Impl::addSelf);
		
		v2.DeleteMember.use(Clearinghouse3Impl::deleteMember);
		v3.DeleteMember.use(Clearinghouse3Impl::deleteMember);
		
		v2.DeleteSelf.use(Clearinghouse3Impl::deleteSelf);
		v3.DeleteSelf.use(Clearinghouse3Impl::deleteSelf);
		
		v2.IsMember.use(Clearinghouse3Impl::isMember);
		v3.IsMember.use(Clearinghouse3Impl::isMember);
		
		CourierRegistry.register(v2);
		CourierRegistry.register(v3);
	}
	
	/**
	 * unregister Clearinghouse implementations from Courier dispatcher
	 */
	public static void unregister() {
		CourierRegistry.unregister(Clearinghouse2.PROGRAM, Clearinghouse2.VERSION);
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
	private static void retrieveAddresses(
							RECORD params,
							RetrieveAddressesResult results) {
		int count = 0;
		for (NetworkAddress a : bfsHosts) {
			results.address.add(a);
			count++;
			if (count >= 40) { return; }
		}
	}
	
	
	/*
	 * ListDomainServed: PROCEDURE [domains: BulkData.Sink, agent: Authenticator]
	 *   REPORTS [AuthenticationError, CallError] = 1;
	 *   
	 * problem: there is no definitive specification of what is to be
	 *          transported through the bulk data transfer...
	 *          ... according to "bfsgetdoms.c" it is: StreamOfDomainName 
	 */
	private static void listDomainsServed(
							Clearinghouse3.ListDomainServedParams params,
							RECORD results) {
		Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: start\n");
		
		// create data for transfer the single domain we serve
		StreamOf<TwoPartName> streamData = new StreamOf<>(0, 1, 2, TwoPartName::make);
		TwoPartName name1 = streamData.add();
		name1.organization.set(chsDatabase.getOrganizationName());
		name1.domain.set(chsDatabase.getDomainName());
		
		// and send the bulk data
		sendBulkData("listDomainsServed", params.domains, streamData);
		
		
		Log.C.printf("CHS3", "Clearinghouse3.listDomainServed() :: end\n");
	}
	
	
	/*
	 * CreateObject: PROCEDURE [name: ObjectName, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 2;
	 */
	private static void createObject(
							Clearinghouse3.CreateDeleteObjectParams params,
							RECORD results) {
		abortDueToReadOnlyCHS("createObject");
	}
	
	
	/*
	 * DeleteObject: PROCEDURE [name: ObjectName, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 3;
	 */
	private static void deleteObject(
							Clearinghouse3.CreateDeleteObjectParams params,
							RECORD results) {
		abortDueToReadOnlyCHS("deleteObject");
	}
	
	
	/*
	 * LookupObject: PROCEDURE [name: ObjectNamePattern, agent: Authenticator]
	 *   RETURNS [distinguishedObject: ObjectName]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 4;
	 */
	private static void lookupObject(
							Clearinghouse3.LookupObjectParams params,
							Clearinghouse3.DistinguishedObjectResults results) {
		Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() for name: %s \n", params.name.getString());
		
		// authentication
		checkCredentials("lookupObject", params.agent, true);
		
		// lookup the name(pattern) and raise an error if not found
		if (!chsDatabase.findDistinguishedName(params.name, results.distinguishedObject)) {
			Clearinghouse3.ArgumentErrorRecord err = new Clearinghouse3.ArgumentErrorRecord(
					Clearinghouse3.ArgumentProblem.noSuchObject,
					Clearinghouse3.WhichArgument.first);
			Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject() -> ArgumentErrorRecord[noSuchObject,first], reason: not found in CHS\n");
			err.raise();
		}
		
		Log.C.printf("CHS3", "Clearinghouse3Impl.LookupObject(), results.distinguishedObject: %s \n", results.distinguishedObject.getString());
	}
	
	
	/*
	 * ListOrganizations: PROCEDURE [pattern: OrganizationNamePattern,
	 *     list: BulkData.Sink, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 5;
	 */
	private static void listOrganizations(
							ListOrganizationsParams params,
							RECORD results) {
		Log.C.printf("CHS3", "Clearinghouse3.listOrganizations( name = '%s' )\n", params.name);
		
		// authentication
		checkCredentials("listOrganizations", params.agent, true);
		
		// prepare the stream...
		// but unclear stream of what: STRING?, ThreePartName? (seems to be STRING)
		StreamOf<STRING> streamData = new StreamOf<>(0, 1, 2, STRING::make);
		String pattern = getJavaPattern(params.name.get());
		String thisOrgName = chsDatabase.getOrganizationName().toLowerCase();
		System.out.printf("+++ listOrganizations(): matching '%s' to '%s'\n", pattern, thisOrgName); 
		if (Pattern.matches(pattern, thisOrgName)) {
			streamData.add().set(chsDatabase.getOrganizationName());
		}
		
		// ... and send the bulk data
		sendBulkData("listOrganizations", params.list, streamData);
		
		Log.C.printf("CHS3", "Clearinghouse3.listOrganizations() :: end\n");
	}
	
	
	/*
	 * ListDomain: PROCEDURE [pattern: DomainNamePattern, list: BulkData.Sink,
	 *     agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 6;
	 */
	private static void listDomain(
							ListDomainParams params,
							RECORD results) {
		Log.C.printf("CHS3", "Clearinghouse3.listDomain( pattern = '%s:%s')\n",
				params.pattern.domain.get(), params.pattern.organization.get());
		
		// authentication
		checkCredentials("listDomain", params.agent, true);
		
		// prepare the stream...
		// but unclear stream of what: Domain(=STRING)?, DomainName(=TwoPartname)?, ThreePartName? (seems to be STRING)
		StreamOf<STRING> streamData = new StreamOf<>(0, 1, 2, STRING::make);
		String patternOrg = params.pattern.organization.get().toLowerCase();
		String patternDomain = getJavaPattern(params.pattern.domain.get());
		String thisOrgName = chsDatabase.getOrganizationName().toLowerCase();
		String thisDomainName = chsDatabase.getDomainName().toLowerCase();
		if (thisOrgName.equals(patternOrg) && Pattern.matches(patternDomain, thisDomainName)) {
			streamData.add().set(chsDatabase.getDomainName());
		}
		
		// ... and send the bulk data
		sendBulkData("listDomain", params.list, streamData);
		
		Log.C.printf("CHS3", "Clearinghouse3.listDomain() :: end\n");
	}
	
	
	/*
	 * ListObjects: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *    list: BulkData.Sink, agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 7;
	 */
	private static void listObjects(
							ListObjectsOrAliasesParams params,
							RECORD results) {
		innerListObjectsOrAliases(true, params.agent, params.pattern, params.property.get(), params.list);
	}
	
	
	/*
	 * ListAliases: PROCEDURE [pattern: ObjectNamePattern, list: BulkData.Sink,
	 *    agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 8;
	 */
	private static void listAliases(
							ListAliasesParams params,
							RECORD results) {
		innerListObjectsOrAliases(false, params.agent, params.pattern, Clearinghouse3.all, params.list);
	}
	
	private static void innerListObjectsOrAliases(
							boolean doObjects,
							Authenticator agent,
							ThreePartName pattern,
							long property,
							BulkData1.Sink list) {
		String procName = (doObjects) ? "listObjects" : "listAliases";
		
		Log.C.printf("CHS3", "Clearinghouse3.%s( pattern = '%s:%s:%s' , property = %d)\n",
				procName,
				pattern.object.get(), pattern.domain.get(), pattern.organization.get(),
				property);
		
		// authentication
		checkCredentials(procName, agent, true);
		
		// prepare the stream...
		// but unclear stream of what: Object(=STRING)?, ThreePartName? (seems to be STRING)
		StreamOf<STRING> streamData = new StreamOf<>(0, 1, 2, STRING::make);
		String patternOrg = pattern.organization.get().toLowerCase();
		String patternDomain = pattern.domain.get().toLowerCase();
		String patternObject = getJavaPattern(pattern.object.get());
		String thisOrgName = chsDatabase.getOrganizationName().toLowerCase();
		String thisDomainName = chsDatabase.getDomainName().toLowerCase();
		if (thisOrgName.equals(patternOrg) && thisDomainName.equals(patternDomain)) {
			List<String> matches = (doObjects)
					? chsDatabase.findNames(patternObject, property)
					: chsDatabase.findAliases(patternObject, property);
			matches.sort( (l,r) -> l.compareTo(r) );
			for (String match : matches) {
				streamData.add().set(match);
			}
		}
		
		// ... and send the bulk data
		sendBulkData(procName, list, streamData);
		
		Log.C.printf("CHS3", "Clearinghouse3.%s() :: end\n", procName);
	}
	
	/*
	 * ListAliasesOf: PROCEDURE [pattern: ObjectNamePattern, list: BulkData.Sink,
	 *    agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 9;
	 */
	private static void listAliasesOf(
							ListAliasesParams params,
							DistinguishedObjectResults results) {
		StringBuilder sb = new StringBuilder();
		String paramsString = params.append(sb, "", "params").toString();
		Log.C.printf("CHS3", "Clearinghouse3Impl.listAliasesOf(), %s \n", paramsString);
		
		// authentication
		checkCredentials("lookupObject", params.agent, true);
		
		// lookup the name(pattern) and raise an error if not found
		if (!chsDatabase.findDistinguishedName(params.pattern, results.distinguishedObject)) {
			Clearinghouse3.ArgumentErrorRecord err = new Clearinghouse3.ArgumentErrorRecord(
					Clearinghouse3.ArgumentProblem.noSuchObject,
					Clearinghouse3.WhichArgument.first);
			Log.C.printf("CHS3", "Clearinghouse3Impl.listAliasesOf() -> ArgumentErrorRecord[noSuchObject,first], reason: not found in CHS\n");
			err.raise();
		}
		
		// prepare the stream...
		// but unclear stream of what: Object(=STRING)?, ThreePartName? (seems to be (ThreePart-)Name)
		StreamOf<Name> streamData = new StreamOf<>(0, 1, 2, Name::make); 
		List<String> aliases = chsDatabase.getAliasesOf(results.distinguishedObject);
		for (String alias : aliases) {
			Name a = streamData.add();
			a.object.set(alias);
			a.domain.set(chsDatabase.getDomainName());
			a.organization.set(chsDatabase.getOrganizationName());
		}
		
		// ... and send the bulk data
		sendBulkData("listAliasesOf", params.list, streamData);
		
		// done
		sb.setLength(0);
		String resultsString = results.append(sb, "", "results").toString();
		Log.C.printf("CHS3", "Clearinghouse3Impl.listAliasesOf(), %s \n", resultsString);
	}
	
	/*
	 * CreateAlias: PROCEDURE [alias, sameAs: ObjectName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 10;
	 */
	private static void createAlias(
							CreateAliasParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("createAlias");
	}
	
	/*
	 * DeleteAlias: PROCEDURE [alias: ObjectName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 11;
	 */
	private static void deleteAlias(
							DeleteAliasParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("createAlias");
	}
	
	/*
	 * AddGroupProperty: PROCEDURE [name: ObjectName, newProperty: Property,
	 *     membership: BulkData.Source, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 12;
	 */
	private static void addGroupProperty(
							AddGroupPropertyParams params,
							DistinguishedObjectResults results) {
		// consume the membership bulk stream
		StreamOf<ObjectName> membersStream = new StreamOf<>(0, 1, 2, ObjectName::make);
		try {
			params.membership.receive(membersStream);
		} catch (EndOfMessageException e) {
			// ignored...
		}
		
		// reject call...
		abortDueToReadOnlyCHS("addGroupProperty");
	}
	
	/*
	 * AddItemProperty: PROCEDURE [name: ObjectName, newProperty: Property,
	 *     value: Item, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 13;
	 */
	private static void addItemProperty(
							AddItemPropertyParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("addItemProperty");
	}
	
	/*
	 * DeleteProperty: PROCEDURE [name: ObjectName, property: Property,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 14;
	 */
	private static void deleteProperty(
							DeletePropertyParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("deleteProperty");
	}
	
	/*
	 * ListProperties: PROCEDURE [pattern: ObjectNamePattern, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName, properties: Properties]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 15;
	 */
	private static void listProperties(
							ListPropertiesParams params,
							ListPropertiesResults results) {
		Log.C.printf("CHS3", "Clearinghouse3Impl.listProperties( pattern = '%s:%s:%s' )\n",
				params.pattern.object.get(), params.pattern.domain.get(), params.pattern.organization.get());
		
		// authentication
		checkCredentials("listProperties", params.agent, true);
		
		// check existence and fill result data if found
		if (!chsDatabase.getPropertyList(params.pattern, results.distinguishedObject, results.properties)) {
			Log.C.printf("CHS3", "Clearinghouse3Impl.listProperties(): entry not found\n");
			new Clearinghouse3.ArgumentErrorRecord(ArgumentProblem.noSuchObject, WhichArgument.first).raise();
		}
	}
	
	/*
	 * RetrieveItem: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName, value: Item]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 16;
	 */
	private static void retrieveItem(
							RetrieveItemParams params,
							RetrieveItemResults results) {
		Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem( pattern = '%s:%s:%s' , property = %d )\n",
				params.pattern.object.get(), params.pattern.domain.get(), params.pattern.organization.get(),
				params.property.get());
		
		// authentication
		checkCredentials("retrieveItem", params.agent, true);
		
		// lookup the database and check the outcome
		int result = chsDatabase.getEntryProperty(
						params.pattern, 
						(int)(params.property.get() & 0xFFFFFFFFL), 
						results.distinguishedObject,
						results.value);
		switch(result) {
		case 3: // entry and property found
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem(): entry and property found\n");
			return;
		case 1: // entry found, but not property
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem(): entry found, but not property\n");
			new Clearinghouse3.PropertyErrorRecord(PropertyProblem.missing, results.distinguishedObject).raise();
			return;
		case 2: // entry found, but wrong property type (group, not item)
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem(): entry found, but wrong property type (group, not item)\n");
			new Clearinghouse3.PropertyErrorRecord(PropertyProblem.wrongType, results.distinguishedObject).raise();
			return;
		default: // entry not found (treat any none of the above as such)
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem(): entry not found\n");
			new Clearinghouse3.ArgumentErrorRecord(ArgumentProblem.noSuchObject, WhichArgument.first).raise();
			return;
		}
	}
	
	/*
	 * ChangeItem: PROCEDURE [name: ObjectName, property: Property, newValue: Item,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 17;
	 */
	private static void changeItem(
							ChangeItemParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("changeItem");
	}
	
	/*
	 * RetrieveMembers: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *     membership: BulkData.Sink, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 18;
	 */
	private static void retrieveMembers(
							RetrieveMembersParams params,
							DistinguishedObjectResults results) {
		Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveMembers( pattern = '%s:%s:%s' , property = %d )\n",
				params.pattern.object.get(), params.pattern.domain.get(), params.pattern.organization.get(),
				params.property.get());
		
		// authentication
		checkCredentials("retrieveMembers", params.agent, true);
		
		// get the members and if available stream them back
		try {
			
			List<ThreePartName> members = chsDatabase.getEntryGroupMembers(
												params.pattern,
												(int)(params.property.get() & 0xFFFFFFFFL),
												results.distinguishedObject);
			if (members == null) {
				Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveItem(): entry found, but wrong property type (group, not item)\n");
				new Clearinghouse3.PropertyErrorRecord(PropertyProblem.wrongType, results.distinguishedObject).raise();
			}
			
			StreamOf<ObjectName> membersStream = new StreamOf<>(0, 1, 2, ObjectName::make);
			for (ThreePartName member : members) {
				ThreePartName m = membersStream.add();
				m.object.set(member.object.get());
				m.domain.set(member.domain.get());
				m.organization.set(member.organization.get());
			}
			
			sendBulkData("retrieveMembers", params.membership, membersStream);
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveMembers(): entry and group property found, %d members\n", members.size());
			
		} catch(IllegalArgumentException e) {
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveMembers(): entry not found\n");
			new Clearinghouse3.ArgumentErrorRecord(ArgumentProblem.noSuchObject, WhichArgument.first).raise();
		}
	}
	
	/*
	 * AddMember: PROCEDURE [name: ObjectName, property: Property,
	 *     newMember: ThreePartName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 19;
	 */
	private static void addMember(
							AddDeleteMemberParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("addMember");
	}

	/*
	 * AddSelf: PROCEDURE [name: ObjectName, property: Property, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 20;
	 */
	private static void addSelf(
							AddDeleteSelfParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("addSelf");
	}

	/*
	 * DeleteMember: PROCEDURE [name: ObjectName, property: Property,
	 *     member: ThreePartName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 21;
	 */
	private static void deleteMember(
							AddDeleteMemberParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("deleteMember");
	}

	/*
	 * DeleteSelf: PROCEDURE [name: ObjectName, property: Property, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 22;
	 */
	private static void deleteSelf(
							AddDeleteSelfParams params,
							DistinguishedObjectResults results) {
		abortDueToReadOnlyCHS("deleteSelf");
	}
	
	/*
	 * IsMember: PROCEDURE [memberOf: ObjectNamePattern,
	 *     property, secondaryProperty: Property, name: ThreePartName,
	 *     agent: Authenticator]
	 *  RETURNS [isMember: BOOLEAN, distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 23;
	 */
	private static void isMember(
							IsMemberParams params,
							IsMemberResults results) {
		Log.C.printf("CHS3", "Clearinghouse3Impl.isMember( memberOf = '%s:%s:%s' , property = %d , secondaryProperty = %d , name = '%s:%s:%s' )\n",
				params.memberOf.object.get(), params.memberOf.domain.get(), params.memberOf.organization.get(),
				params.property.get(),
				params.secondaryProperty.get(),
				params.name.object.get(), params.name.domain.get(), params.name.organization.get());
		
		// authentication
		checkCredentials("isMembers", params.agent, true);
		
		// get the members of the top-level and scan those and possibly recursively
		try {
			
			List<ThreePartName> members = chsDatabase.getEntryGroupMembers(
												params.memberOf,
												(int)(params.property.get() & 0xFFFFFFFFL),
												results.distinguishedObject);
			if (members == null) {
				Log.C.printf("CHS3", "Clearinghouse3Impl.isMember(): entry found, but wrong property type (group, not item)\n");
				new Clearinghouse3.PropertyErrorRecord(PropertyProblem.wrongType, results.distinguishedObject).raise();
			}
			
			results.isMember.set(scanMembers(
									members,
									params.name.getLcFqn(),
									(int)(params.property.get() & 0xFFFFFFFFL),
									(int)(params.secondaryProperty.get() & 0xFFFFFFFFL)));
			
		} catch(IllegalArgumentException e) {
			Log.C.printf("CHS3", "Clearinghouse3Impl.retrieveMembers(): entry not found\n");
			new Clearinghouse3.ArgumentErrorRecord(ArgumentProblem.noSuchObject, WhichArgument.first).raise();
		}
	}
	
	/* Citation for functionality
	 * from: Clearinghouse Programmer's Manual
	 * in  : Services Programmers Guide (Nov 84)
	 * for : IsMemberClosure(...), page 3-8
	 * 
	 * This is a recursive version of IsMember and works as follows: element is sought in the
	 * group property pn of name. If it is found, isMember = TRUE is returned immediately. If it is
	 * not found, each of the non-pattern elements of the group property pn of name is treated as
	 * a name which has a group property pn2 which must also be searched for element. If this
	 * level fails, each of the elements of each of those groups (if they really are groups) is
	 * searched for element (via pn2), and so forth until either element is found or there are no
	 * groups left to search. If pn2 is defaulted, then pn2 = pn. Because groups are often nested,
	 * most clients should use IsMemberClosure instead ofisMember.
	 */
	private static boolean scanMembers(List<ThreePartName> members, String fqn, int property, int secondaryProperty) {
		List<ThreePartName> potentialSubgroups = new ArrayList<>(); 
		
		for (ThreePartName m : members) {
			String member = m.getLcFqn();
			if (member.contains("*")) {
				if (Pattern.matches(getJavaPattern(member), fqn)) { return true; }
			} else {
				if (member.equals(fqn))  { return true; }
				potentialSubgroups.add(m);
			}
		}
		
		for (ThreePartName group : potentialSubgroups) {
			try {
				List<ThreePartName> subMembers = chsDatabase.getEntryGroupMembers(group, secondaryProperty, null);
				if (subMembers != null && !subMembers.isEmpty()) {
					if (scanMembers(subMembers, fqn, secondaryProperty, secondaryProperty)) {
						return true;
					}
				}
			} catch(IllegalArgumentException e) {
				// ignored, as should not happen (members of group always exist due to postprocessing!)
			}
		}
		
		return false;
	}
	
	/* internal functionality */
	
	private static void checkCredentials(String procName, Authenticator agent, boolean allowEmptyCredentials) {
		boolean credentialsOk = false;
		try {
			if (agent.credentials.value.size() == 0) {
				credentialsOk = allowEmptyCredentials;
			} else if (agent.credentials.type.get() == CredentialsType.simple) {
				credentialsOk = AuthChsCommon.simpleCheckPasswordForSimpleCredentials(
									chsDatabase,
									agent.credentials,
									agent.verifier) != null;
			} else {
				credentialsOk = AuthChsCommon.checkStrongCredentials(
									chsDatabase,
									agent.credentials,
									agent.verifier,
									chsDatabase.getChsQueryName(),
									machineId,
									null, null) != null;
			}
		} catch (IllegalArgumentException iac) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("CHS3", "Clearinghouse3Impl.%s() IllegalArgumentException (name not existing) -> rejecting with AuthenticationError[credentialsInvalid]\n", procName);
			err.raise();
		} catch (EndOfMessageException e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.inappropriateCredentials);
			Log.C.printf("CHS3", "Clearinghouse3Impl.%s() EndOfMessageException when deserializing credsObject -> rejecting with AuthenticationError[inappropriateCredentials]\n", procName);
			err.raise();
		} catch (Exception e) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.otherProblem);
			Log.C.printf("CHS3", "Clearinghouse3Impl.%s() Exception when checking credentials -> rejecting with AuthenticationError[otherProblem]: %s\n", procName, e.getMessage());
			err.raise();
		}
		if (!credentialsOk) {
			AuthenticationErrorRecord err = new AuthenticationErrorRecord(Problem.credentialsInvalid);
			Log.C.printf("CHS3", "Clearinghouse3Impl.%s() -> rejecting with AuthenticationError[credentialsInvalid]\n", procName);
			err.raise();
		}
	}
	
	private static void sendBulkData(String procName, BulkData1.Sink sink, iWireData streamData) {
		Log.C.printf("CHS3", "Clearinghouse3.%s() :: sending data via bulk data transfer\n", procName);
		try {
			sink.send(streamData, true);
		} catch (NoMoreWriteSpaceException e) { 
			Log.C.printf("CHS3", "Clearinghouse3.%s() :: NoMoreWriteSpaceException (i.e. abort) during BDT.send()\n", procName);
			CallErrorRecord err = new CallErrorRecord(CallProblem.other);
			err.raise();
		}
	}
	
	private static void abortDueToReadOnlyCHS(String procName) {
		Log.C.printf("CHS3", "** Clearinghouse3Impl.%s() -> CallError[accessRightsInsufficient]\n", procName);
		new Clearinghouse3.CallErrorRecord(CallProblem.accessRightsInsufficient).raise();
	}
	
	private static String getJavaPattern(String xeroxPattern) {
		String normalized = xeroxPattern.toLowerCase().replace(".", "\\.").replace("**", "*").replace("**", "*").replace("**", "*");
		return normalized.replace("*", ".*");
	}
	
}
