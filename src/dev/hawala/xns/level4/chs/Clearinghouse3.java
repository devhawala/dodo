package dev.hawala.xns.level4.chs;

import dev.hawala.xns.level3.courier.BOOLEAN;
import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.LONG_CARDINAL;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.SEQUENCE;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level3.courier.UNSPECIFIED;
import dev.hawala.xns.level3.courier.WireSeqOfUnspecifiedReader;
import dev.hawala.xns.level3.courier.WireWriter;
import dev.hawala.xns.level3.courier.iWireData;
import dev.hawala.xns.level3.courier.iWireStream.EndOfMessageException;
import dev.hawala.xns.level3.courier.iWireStream.NoMoreWriteSpaceException;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.BulkData1;

/**
 * Definition of the (so far supported) functionality
 * of the Courier Clearinghouse program (PROGRAM 2 VERSION 3).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018,2019)
 */
public class Clearinghouse3 extends AuthChsCommon {
	
	public static final int PROGRAM = 2;
	public static final int VERSION = 3;
	
	public int getProgramNumber() { return PROGRAM; }
	
	public int getVersionNumber() { return VERSION; }
	
	/*
	 * ********* plain data structures 
	 */
	
	/*
	 * Property: TYPE = LONG CARDINAL;
	 */
	// Property => int
	
	/*
	 * Properties: TYPE = SEQUENCE 250 OF Property;
	 */
	public static class Properties extends SEQUENCE<LONG_CARDINAL> {
		private static final int MAXLEN = 250;
		
		private Properties() { super(MAXLEN, LONG_CARDINAL::make); }
		public static Properties make() { return new Properties(); }
	}
	
	// all: Property = 0;
	public static final int all = 0;
	
	// nullProperty: Property = 37777777777B;
	public static final int nullProperty = 0xFFFFFFFF;
	
	/*
	 * Item: TYPE = SEQUENCE 500 OF UNSPECIFIED;
	 */
	public static class Item extends SEQUENCE<UNSPECIFIED> {
		private static final int MAXLEN = 500;
		
		private Item() { super(MAXLEN,UNSPECIFIED::make); }
		public static Item make() { return new Item(); }
		
		public static Item from(iWireData o) throws NoMoreWriteSpaceException {
			WireWriter wire = new WireWriter();
			o.serialize(wire);
			int[] words = wire.getWords();
			if (words.length > MAXLEN) {
				throw new NoMoreWriteSpaceException(); // "Item overflow, serialized length > 500"
			}
			
			Item item = new Item();
			for (int i = 0; i < words.length; i++) {
				item.add().set(words[i]);
			}
			
			return item;
		}
		
		public <T extends iWireData> T to(T o) throws EndOfMessageException {
			WireSeqOfUnspecifiedReader wire = new WireSeqOfUnspecifiedReader(this);
			o.deserialize(wire);
			return o;
		}
	}
	
	/*
	 * Authenticator: TYPE = RECORD [
	 *     credentials: Authentication.Credentials,
	 *     verifier: Authentication.Verifier];
	 */
	public static class Authenticator extends RECORD {
		public final Credentials credentials = mkRECORD(Credentials::make);
		public final Verifier verifier = mkMember(Verifier::make);
		
		private Authenticator() {}
		public static Authenticator make() { return new Authenticator(); }
	}
	
	
	/*
	 * ********* errors
	 */
	
	/*
	 * CallProblem: TYPE = {
	 *     accessRightsInsufficient(1), -- operation prevented by access controls --
	 *     tooBusy(2), -- server is too busy to service this request --
	 *     serverDown(3), -- a remote Clearinghouse server was down and was needed for this request --
	 *     useCourier(4), -- server insists that Courier be used for this particular request --
	 *     other(5) };
	 * CallError: ERROR [problem: CallProblem] = 1;
	 */
	
	public enum CallProblem implements CrEnum {
		accessRightsInsufficient(1), // operation prevented by access controls
		tooBusy(2),    // server is too busy to service this request
		serverDown(3), // a remote Clearinghouse server was down and was needed for this request
		useCourier(4), // server insists that Courier be used for this particular request
		other(5)
		;
		
		private final int wireValue;
		
		private CallProblem(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	};
	public static final EnumMaker<CallProblem> mkCallProblem = buildEnum(CallProblem.class).get();
	
	public static class CallErrorRecord extends ErrorRECORD {
		
		public final ENUM<CallProblem> problem = mkENUM(mkCallProblem);

		@Override
		public int getErrorCode() { return 1; }
		
		public CallErrorRecord(CallProblem problem) {
			this.problem.set(problem);
		}
	}
	
	public final ERROR<CallErrorRecord> CallError = mkERROR(CallErrorRecord.class);
	
	
	/*
	 * AuthenticationError: ERROR [problem: Authentication.Problem] = 6;
	 */
	public static class AuthenticationErrorRecord extends ErrorRECORD {
		
		public final ENUM<Problem> problem = mkENUM(mkProblem);

		@Override
		public int getErrorCode() { return 6; }
		
		public AuthenticationErrorRecord(Problem problem) {
			this.problem.set(problem);
		}
	}
	
	public final ERROR<AuthenticationErrorRecord> AuthenticationError = mkERROR(AuthenticationErrorRecord.class);
	
	
	/*
	 * ArgumentProblem: TYPE = {
	 *   	illegalProperty(10), -- property is not usable by a client --
	 *   	illegalOrganizationName(11), -- the organization component of the name
	 *   		-- is incorrect, e.g., too long or short, or has wild card
	 *   		-- characters when not allowed --
	 *   	illegalDomainName(12), -- the domain component of the name
	 *   		-- is incorrect, e.g., too long or short, or has wild card
	 *   		-- characters when not allowed --
	 *   	illegalObjectName(13), -- the object component of the name
	 *   		-- is incorrect, e.g., too long or short, or has wild card
	 *   		-- characters when not allowed --
	 *   	noSuchOrganization(14), -- the name's organization component does not exist --
	 *   	noSuchDomain(15), -- the name's domain component does not exist --
	 *   	noSuchObject(16) }; -- the name's object component does not exist --
	 * ArgumentError: ERROR [problem: ArgumentProblem, which: WhichArgument] = 2;
	 */
	public enum ArgumentProblem implements CrEnum {
		illegalProperty(10),
		illegalOrganizationName(11),
		illegalDomainName(12),
		illegalObjectName(13),
		noSuchOrganization(14),
		noSuchDomain(15),
		noSuchObject(16);
		
		private final int wireValue;
		
		private ArgumentProblem(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<ArgumentProblem> mkArgumentProblem = buildEnum(ArgumentProblem.class).get();
	
	public enum WhichArgument { first, second; }
	public static final EnumMaker<WhichArgument> mkWhichArgument = buildEnum(WhichArgument.class)
							.map(1, WhichArgument.first)
							.map(2, WhichArgument.second)
							.get();
	
	public static class ArgumentErrorRecord extends ErrorRECORD {
		public final ENUM<ArgumentProblem> problem = mkENUM(mkArgumentProblem);
		public final ENUM<WhichArgument> which = mkENUM(mkWhichArgument);

		@Override
		public int getErrorCode() { return 2; }
		
		public ArgumentErrorRecord(ArgumentProblem problem, WhichArgument which) {
			this.problem.set(problem);
			this.which.set(which);
		}
	}
	
	public final ERROR<ArgumentErrorRecord> ArgumentError = mkERROR(ArgumentErrorRecord.class);
	
	
	/*
	 * WrongServer: ERROR [hint: ObjectName] = 5;
	 */
	public static class WrongServerRecord extends ErrorRECORD {
		public final ThreePartName hint = mkRECORD(ThreePartName::make);

		@Override
		public int getErrorCode() { return 5; }
		
		public WrongServerRecord(ThreePartName hint) {
			this(hint.object.get(), hint.domain.get(), hint.organization.get());
		}
		
		public WrongServerRecord(String object, String domain, String organization) {
			this.hint.object.set(object);
			this.hint.domain.set(domain);
			this.hint.organization.set(organization);
		}
	}
	
	public final ERROR<WrongServerRecord> WrongServer = mkERROR(WrongServerRecord.class);
	
	
	/*
	 * PropertyProblem: TYPE = {
	 *   	missing(20), -- the object exists, but the property doesn't --
	 *   	wrongType(21)}; -- client wanted a Group but it was an Item, or vice versa --
	 * PropertyError: ERROR [problem: PropertyProblem, distinguishedObject: ObjectName] = 3;
	 */
	public enum PropertyProblem implements CrEnum {
		missing(20),
		wrongType(21);
		
		private final int wireValue;
		
		private PropertyProblem(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<PropertyProblem> mkPropertyProblem = buildEnum(PropertyProblem.class).get();
	
	public static class PropertyErrorRecord extends ErrorRECORD {
		public final ENUM<PropertyProblem> problem = mkENUM(mkPropertyProblem);
		public final ObjectName distinguishedObject = mkMember(ObjectName::make);

		@Override
		public int getErrorCode() { return 3; }
		
		public PropertyErrorRecord(PropertyProblem problem, ObjectName object) {
			this.problem.set(problem);
			this.distinguishedObject.from(object);
		}
	}
	
	public final ERROR<PropertyErrorRecord> PropertyError = mkERROR(PropertyErrorRecord.class);
	
	
	/*
	 * UpdateProblem: TYPE = {
	 *   	noChange(30), -- operation wouldn't change the database --
	 *   	outOfDate(31), -- more recent information was in database --
	 *   	objectOverflow(32), -- the particular object will have too much data
	 *                          -- associated with it --
	 *   	databaseOverflow(33)}; -- the server has run out of room --
	 * UpdateError: ERROR [problem: UpdateProblem, found: BOOLEAN,
	 *   	which: WhichArgument, distinguishedObject: ObjectName] = 4;
	 */
	public enum UpdateProblem implements CrEnum {
		noChange(30),
		outOfDate(31),
		objectOverflow(32),
		databaseOverflow(33);
		
		private final int wireValue;
		
		private UpdateProblem(int w) { this.wireValue = w; }

		@Override
		public int getWireValue() { return this.wireValue; }
	}
	public static final EnumMaker<UpdateProblem> mkUpdateProblem = buildEnum(UpdateProblem.class).get();
	
	public static class UpdateErrorRecord extends ErrorRECORD {
		public final ENUM<UpdateProblem> problem = mkENUM(mkUpdateProblem);
		public final BOOLEAN found = mkBOOLEAN();
		public final ENUM<WhichArgument> which = mkENUM(mkWhichArgument);
		public final ObjectName distinguishedObject = mkMember(ObjectName::make);

		@Override
		public int getErrorCode() { return 4; }
		
		public UpdateErrorRecord(
				UpdateProblem problem,
				boolean found,
				WhichArgument which,
				ObjectName object) {
			this.problem.set(problem);
			this.found.set(found);
			this.which.set(which);
			this.distinguishedObject.from(object);
		}
	}
	
	public final ERROR<UpdateErrorRecord> UpdateError = mkERROR(UpdateErrorRecord.class);
	
	
	
	/*
	 * ********* procedures
	 */

	/*
	 * common results record for procedures returning the three-part-name of a single object
	 */
	public static class DistinguishedObjectResults extends RECORD {
		public final ObjectName distinguishedObject = mkRECORD(ObjectName::make);
		
		private DistinguishedObjectResults() {}
		public static DistinguishedObjectResults make() { return new DistinguishedObjectResults(); }
	}
	
	/*
	 * RetrieveAddresses: PROCEDURE
	 *   RETURNS [address: NetworkAddressList]
	 *   REPORTS [CallError] = 0;
	 */
	public final PROC<RECORD,RetrieveAddressesResult> RetrieveAddresses = mkPROC(
							0,
							RECORD::empty,
							RetrieveAddressesResult::make,
							CallError);
	
	/*
	 * ListDomainServed: PROCEDURE [domains: BulkData.Sink, agent: Authenticator]
	 *   REPORTS [AuthenticationError, CallError] = 1;
	 */
	public static class ListDomainServedParams extends RECORD {
		public final BulkData1.Sink domains = mkRECORD(BulkData1.Sink::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListDomainServedParams() {}
		public static ListDomainServedParams make() { return new ListDomainServedParams(); }
	}
	public final PROC<ListDomainServedParams,RECORD> ListDomainsServed = mkPROC(
							1,
							ListDomainServedParams::make,
							RECORD::empty,
							AuthenticationError,
							CallError);
	
	
	/*
	 * CreateObject: PROCEDURE [name: ObjectName, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 2;
	 */
	public static final class CreateDeleteObjectParams extends RECORD {
		public final ObjectName name = mkMember(ObjectName::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private CreateDeleteObjectParams() {}
		public static CreateDeleteObjectParams make() { return new CreateDeleteObjectParams(); }
	}
	public final PROC<CreateDeleteObjectParams,RECORD> CreateObject = mkPROC(
							2,
							CreateDeleteObjectParams::make,
							RECORD::empty,
							ArgumentError,
							AuthenticationError,
							CallError,
							UpdateError,
							WrongServer);
	
	
	/*
	 * DeleteObject: PROCEDURE [name: ObjectName, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 3;
	 */
	public final PROC<CreateDeleteObjectParams,RECORD> DeleteObject = mkPROC(
							3,
							CreateDeleteObjectParams::make,
							RECORD::empty,
							ArgumentError,
							AuthenticationError,
							CallError,
							UpdateError,
							WrongServer);
	
	
	/*
	 * LookupObject: PROCEDURE [name: ObjectNamePattern, agent: Authenticator]
	 *   RETURNS [distinguishedObject: ObjectName]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 4;
	 */
	public static class LookupObjectParams extends RECORD {
		public final ObjectNamePattern name = mkRECORD(ObjectNamePattern::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private LookupObjectParams() {}
		public static LookupObjectParams make() { return new LookupObjectParams(); }
	}
	public final PROC<LookupObjectParams,DistinguishedObjectResults> LookupObject = mkPROC(
							4,
							LookupObjectParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							WrongServer);
	
	
	/*
	 * ListOrganizations: PROCEDURE [pattern: OrganizationNamePattern,
	 *     list: BulkData.Sink, agent: Authenticator]
	 *   REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 5;
	 */
	public static class ListOrganizationsParams extends RECORD {
		public final STRING name = mkSTRING(20); // OrganizationNamePattern is Organization is STRING
		public final BulkData1.Sink list = mkRECORD(BulkData1.Sink::make); // StreamOfOrganization = StreamOf<STRING>
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListOrganizationsParams() {}
		public static ListOrganizationsParams make() { return new ListOrganizationsParams(); }
	}
	public final PROC<ListOrganizationsParams,RECORD> ListOrganizations = mkPROC(
							5,
							ListOrganizationsParams::make,
							RECORD::empty,
							AuthenticationError,
							CallError,
							WrongServer);
	
	
	/*
	 * ListDomain: PROCEDURE [pattern: DomainNamePattern, list: BulkData.Sink,
	 *     agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 6;
	 */
	public static class ListDomainParams extends RECORD {
		public final TwoPartName pattern = mkRECORD(TwoPartName::make); // DomainNamePattern is TwoPartName
		public final BulkData1.Sink list = mkRECORD(BulkData1.Sink::make); // ?? StreamOfOrganization = StreamOf<STRING>
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListDomainParams() {}
		public static ListDomainParams make() { return new ListDomainParams(); }
	}
	public final PROC<ListDomainParams,RECORD> ListDomain = mkPROC(
							6,
							ListDomainParams::make,
							RECORD::empty,
							AuthenticationError,
							CallError,
							WrongServer);
	
	
	/*
	 * ListObjects: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *    list: BulkData.Sink, agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 7;
	 */
	public static class ListObjectsOrAliasesParams extends RECORD {
		public final ObjectNamePattern pattern = mkRECORD(ObjectNamePattern::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final BulkData1.Sink list = mkRECORD(BulkData1.Sink::make); // ?? StreamOfObject = StreamOf<STRING>
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListObjectsOrAliasesParams() {}
		public static ListObjectsOrAliasesParams make() { return new ListObjectsOrAliasesParams(); }
	}
	public final PROC<ListObjectsOrAliasesParams,RECORD> ListObjects = mkPROC(
							7,
							ListObjectsOrAliasesParams::make,
							RECORD::empty,
							AuthenticationError,
							CallError,
							WrongServer);
	
	
	/*
	 * ListAliases: PROCEDURE [pattern: ObjectNamePattern, list: BulkData.Sink,
	 *    agent: Authenticator]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 8;
	 */
	public static class ListAliasesParams extends RECORD {
		public final ObjectNamePattern pattern = mkRECORD(ObjectNamePattern::make);
		public final BulkData1.Sink list = mkRECORD(BulkData1.Sink::make); // ?? StreamOfObject = StreamOf<STRING>
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListAliasesParams() {}
		public static ListAliasesParams make() { return new ListAliasesParams(); }
	}
	public final PROC<ListAliasesParams,RECORD> ListAliases = mkPROC(
							8,
							ListAliasesParams::make,
							RECORD::empty,
							AuthenticationError,
							CallError,
							WrongServer);
	
	
	/*
	 * ListAliasesOf: PROCEDURE [pattern: ObjectNamePattern, list: BulkData.Sink,
	 *    agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 9;
	 */
	public final PROC<ListAliasesParams,DistinguishedObjectResults> ListAliasesOf = mkPROC(
							9,
							ListAliasesParams::make,
							DistinguishedObjectResults::make,
							AuthenticationError,
							CallError,
							WrongServer);
	
	/*
	 * CreateAlias: PROCEDURE [alias, sameAs: ObjectName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 10;
	 */
	public static class CreateAliasParams extends RECORD {
		public final ObjectName alias = mkMember(ObjectName::make);
		public final ObjectName sameAs = mkMember(ObjectName::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private CreateAliasParams() {}
		public static CreateAliasParams make() { return new CreateAliasParams(); }
	}
	public final PROC<CreateAliasParams,DistinguishedObjectResults> CreateAlias = mkPROC(
							10,
							CreateAliasParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							UpdateError,
							WrongServer);
	
	/*
	 * DeleteAlias: PROCEDURE [alias: ObjectName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, UpdateError, WrongServer] = 11;
	 */
	public static class DeleteAliasParams extends RECORD {
		public final ObjectName alias = mkMember(ObjectName::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private DeleteAliasParams() {}
		public static DeleteAliasParams make() { return new DeleteAliasParams(); }
	}
	public final PROC<DeleteAliasParams,DistinguishedObjectResults> DeleteAlias = mkPROC(
							11,
							DeleteAliasParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							UpdateError,
							WrongServer);
	
	/*
	 * AddGroupProperty: PROCEDURE [name: ObjectName, newProperty: Property,
	 *     membership: BulkData.Source, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 12;
	 */
	public static class AddGroupPropertyParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL newProperty = mkLONG_CARDINAL();
		public final BulkData1.Source membership = mkRECORD(BulkData1.Source::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private AddGroupPropertyParams() {}
		public static AddGroupPropertyParams make() { return new AddGroupPropertyParams(); }
	}
	public final PROC<AddGroupPropertyParams,DistinguishedObjectResults> AddGroupProperty = mkPROC(
							12,
							AddGroupPropertyParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * AddItemProperty: PROCEDURE [name: ObjectName, newProperty: Property,
	 *     value: Item, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 13;
	 */
	public static class AddItemPropertyParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL newProperty = mkLONG_CARDINAL();
		public final Item value = mkMember(Item::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private AddItemPropertyParams() {}
		public static AddItemPropertyParams make() { return new AddItemPropertyParams(); }
	}
	public final PROC<AddItemPropertyParams,DistinguishedObjectResults> AddItemProperty = mkPROC(
							13,
							AddItemPropertyParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * DeleteProperty: PROCEDURE [name: ObjectName, property: Property,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 14;
	 */
	public static class DeletePropertyParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL newProperty = mkLONG_CARDINAL();
		public final Item value = mkMember(Item::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private DeletePropertyParams() {}
		public static DeletePropertyParams make() { return new DeletePropertyParams(); }
	}
	public final PROC<DeletePropertyParams,DistinguishedObjectResults> DeleteProperty = mkPROC(
							14,
							DeletePropertyParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * ListProperties: PROCEDURE [pattern: ObjectNamePattern, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName, properties: Properties]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, WrongServer] = 15;
	 */
	public static class ListPropertiesParams extends RECORD {
		public final ObjectNamePattern pattern = mkRECORD(ObjectNamePattern::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ListPropertiesParams() { }
		public static ListPropertiesParams make() { return new ListPropertiesParams(); }
	}
	public static class ListPropertiesResults extends RECORD {
		public final ObjectName distinguishedObject = mkRECORD(ObjectName::make);
		public final Properties properties = mkMember(Properties::make);
		
		private ListPropertiesResults() { }
		public static ListPropertiesResults make() { return new ListPropertiesResults(); }
	}
	public final PROC<ListPropertiesParams,ListPropertiesResults> ListProperties = mkPROC(
							15,
							ListPropertiesParams::make,
							ListPropertiesResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							WrongServer);
	
	/*
	 * RetrieveItem: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName, value: Item]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 16;
	 */
	public static class RetrieveItemParams extends RECORD {
		public final ObjectNamePattern pattern = mkRECORD(ObjectNamePattern::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private RetrieveItemParams() {}
		public static RetrieveItemParams make() { return new RetrieveItemParams(); }
	}
	public static class RetrieveItemResults extends RECORD {
		public final ObjectName distinguishedObject = mkRECORD(ObjectName::make);
		public final Item value = mkMember(Item::make);
		
		private RetrieveItemResults() {}
		public static RetrieveItemResults make() { return new RetrieveItemResults(); }
	}
	public final PROC<RetrieveItemParams,RetrieveItemResults> RetrieveItem = mkPROC(
							16,
							RetrieveItemParams::make,
							RetrieveItemResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							WrongServer);
	
	/*
	 * ChangeItem: PROCEDURE [name: ObjectName, property: Property, newValue: Item,
	 *     agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 17;
	 */
	public static class ChangeItemParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final Item newValue = mkMember(Item::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private ChangeItemParams() {}
		public static ChangeItemParams make() { return new ChangeItemParams(); }
	}
	public final PROC<ChangeItemParams,DistinguishedObjectResults> ChangeItem = mkPROC(
							17,
							ChangeItemParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * RetrieveMembers: PROCEDURE [pattern: ObjectNamePattern, property: Property,
	 *     membership: BulkData.Sink, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 18;
	 */
	public static class RetrieveMembersParams extends RECORD {
		public final ObjectNamePattern pattern = mkRECORD(ObjectNamePattern::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final BulkData1.Sink membership = mkRECORD(BulkData1.Sink::make); // ?? StreamOfObject = StreamOf<ObjectName>
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private RetrieveMembersParams() {}
		public static RetrieveMembersParams make() { return new RetrieveMembersParams(); }
	}
	public final PROC<RetrieveMembersParams,DistinguishedObjectResults> RetrieveMembers = mkPROC(
							18,
							RetrieveMembersParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							WrongServer);
	
	/*
	 * AddMember: PROCEDURE [name: ObjectName, property: Property,
	 *     newMember: ThreePartName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 19;
	 */
	public static class AddDeleteMemberParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final ThreePartName member = mkRECORD(ThreePartName::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private AddDeleteMemberParams() {}
		public static AddDeleteMemberParams make() { return new AddDeleteMemberParams(); }
	}
	public final PROC<AddDeleteMemberParams,DistinguishedObjectResults> AddMember = mkPROC(
							19,
							AddDeleteMemberParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * AddSelf: PROCEDURE [name: ObjectName, property: Property, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 20;
	 */
	public static class AddDeleteSelfParams extends RECORD {
		public final ObjectName name = mkRECORD(ObjectName::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private AddDeleteSelfParams() {}
		public static AddDeleteSelfParams make() { return new AddDeleteSelfParams(); }
	}
	public final PROC<AddDeleteSelfParams,DistinguishedObjectResults> AddSelf = mkPROC(
							20,
							AddDeleteSelfParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * DeleteMember: PROCEDURE [name: ObjectName, property: Property,
	 *     member: ThreePartName, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 21;
	 */
	public final PROC<AddDeleteMemberParams,DistinguishedObjectResults> DeleteMember = mkPROC(
							21,
							AddDeleteMemberParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * DeleteSelf: PROCEDURE [name: ObjectName, property: Property, agent: Authenticator]
	 *  RETURNS [distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     UpdateError, WrongServer] = 22;
	 */
	public final PROC<AddDeleteSelfParams,DistinguishedObjectResults> DeleteSelf = mkPROC(
							22,
							AddDeleteSelfParams::make,
							DistinguishedObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							UpdateError,
							WrongServer);
	
	/*
	 * IsMember: PROCEDURE [memberOf: ObjectNamePattern,
	 *     property, secondaryProperty: Property, name: ThreePartName,
	 *     agent: Authenticator]
	 *  RETURNS [isMember: BOOLEAN, distinguishedObject: ObjectName]
	 *  REPORTS [ArgumentError, AuthenticationError, CallError, PropertyError,
	 *     WrongServer] = 23;
	 */
	public static class IsMemberParams extends RECORD {
		public final ObjectNamePattern memberOf = mkRECORD(ObjectNamePattern::make);
		public final LONG_CARDINAL property = mkLONG_CARDINAL();
		public final LONG_CARDINAL secondaryProperty = mkLONG_CARDINAL();
		public final ThreePartName name = mkRECORD(ThreePartName::make);
		public final Authenticator agent = mkRECORD(Authenticator::make);
		
		private IsMemberParams() {}
		public static IsMemberParams make() { return new IsMemberParams(); }
	}
	public static class IsMemberResults extends RECORD {
		public final BOOLEAN isMember = mkBOOLEAN();
		public final ObjectName distinguishedObject = mkRECORD(ObjectName::make);
		
		private IsMemberResults() {}
		public static IsMemberResults make() { return new IsMemberResults(); }
	}
	public final PROC<IsMemberParams,IsMemberResults> IsMember = mkPROC(
							23,
							IsMemberParams::make,
							IsMemberResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							PropertyError,
							WrongServer);
	
}
