package dev.hawala.xns.level4.chs;

import dev.hawala.xns.level3.courier.CrEnum;
import dev.hawala.xns.level3.courier.ENUM;
import dev.hawala.xns.level3.courier.ErrorRECORD;
import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level4.common.AuthChsCommon;
import dev.hawala.xns.level4.common.BulkData1;

/**
 * Definition of the (so far supported) functionality
 * of the Courier Clearinghouse program (PROGRAM 2 VERSION 3).
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2018)
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
	 * ********* procedures
	 */
	
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
	public static class LookupObjectResults extends RECORD {
		public final ObjectName distinguishedObject = mkRECORD(ObjectName::make);
		
		private LookupObjectResults() {}
		public static LookupObjectResults make() { return new LookupObjectResults(); }
	}
	public final PROC<LookupObjectParams,LookupObjectResults> LookupObject = mkPROC(
							4,
							LookupObjectParams::make,
							LookupObjectResults::make,
							ArgumentError,
							AuthenticationError,
							CallError,
							WrongServer);
	
}
