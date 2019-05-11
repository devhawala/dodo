/*
Copyright (c) 2019, Dr. Hans-Walter Latz
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

package dev.hawala.xns.level4.filing;

import dev.hawala.xns.level4.filing.FilingCommon.AccessErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.AccessProblem;
import dev.hawala.xns.level4.filing.FilingCommon.ArgumentProblem;
import dev.hawala.xns.level4.filing.FilingCommon.AttributeValueErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.HandleErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.HandleProblem;
import dev.hawala.xns.level4.filing.FilingCommon.InsertionErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.InsertionProblem;
import dev.hawala.xns.level4.filing.FilingCommon.ServiceErrorRecord;
import dev.hawala.xns.level4.filing.FilingCommon.ServiceProblem;
import dev.hawala.xns.level4.filing.fs.iErrorRaiser;

/**
 * Implementation of the error mapper for handling errors in volume operations
 * as exception for Filing Courier protocol.
 * 
 * @author Dr. Hans-Walter Latz / Berlin (2019)
 *
 */
public class ErrorRaiser implements iErrorRaiser {
	
	private void log(String msg) {
		System.out.println("FS,Error: " + msg);
	}

	@Override
	public void fileNotFound(long fileID, String msg) {
		this.log(msg);
		new AccessErrorRecord(AccessProblem.fileNotFound).raise();
	}

	@Override
	public void duplicateFilenameForChildrenUniquelyNamed(String msg) {
		this.log(msg);
		new InsertionErrorRecord(InsertionProblem.fileNotUnique).raise();
	}

	@Override
	public void notADirectory(long fileID, String msg) {
		this.log(msg);
		new HandleErrorRecord(HandleProblem.directoryRequired).raise();
	}

	@Override
	public void wouldCreateLoopInHierarchy(String msg) {
		this.log(msg);
		new InsertionErrorRecord(InsertionProblem.loopInHierarchy).raise();
	}

	@Override
	public void operationNotAllowed(String msg) {
		this.log(msg);
		new AccessErrorRecord(AccessProblem.accessRightsInsufficient).raise();
	}

	@Override
	public void serviceUnavailable(String msg) {
		this.log(msg);
		new ServiceErrorRecord(ServiceProblem.serviceUnavailable).raise();
	}

	@Override
	public void attributeValueError(int attributeType, String msg) {
		this.log(msg);
		new AttributeValueErrorRecord(ArgumentProblem.unreasonable, attributeType).raise();
	}

	@Override
	public void fileContentDamaged(String msg) {
		this.log(msg);
		new AccessErrorRecord(AccessProblem.fileDamaged).raise();
	}

}
