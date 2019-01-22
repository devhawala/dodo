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

package dev.hawala.xns.level4.printing;

import org.junit.Test;

import dev.hawala.xns.level3.courier.RECORD;
import dev.hawala.xns.level3.courier.STRING;
import dev.hawala.xns.level4.common.BulkData1.DescriptorKind;
import dev.hawala.xns.level4.printing.Printing3.GetPrintRequestStatusParams;
import dev.hawala.xns.level4.printing.Printing3.GetPrintRequestStatusResults;
import dev.hawala.xns.level4.printing.Printing3.GetPrinterPropertiesResults;
import dev.hawala.xns.level4.printing.Printing3.GetPrinterStatusResults;
import dev.hawala.xns.level4.printing.Printing3.PrintAttributeChoice;
import dev.hawala.xns.level4.printing.Printing3.PrintParams;
import dev.hawala.xns.level4.printing.Printing3.PrintResults;
import dev.hawala.xns.level4.printing.Printing3.ValueRecord;

public class Printing3Tests {

	@Test
	public void testGetPrinterProperties() {
		RECORD params = RECORD.empty();
		GetPrinterPropertiesResults results = GetPrinterPropertiesResults.make();
		
		Printing3Impl.getPrinterProperties(params, results);
		
		StringBuilder sb = new StringBuilder();
		String resultsDesc = results.append(sb, "", "getPrinterProperties() => results").toString();
		System.out.println("\n" + resultsDesc + "\n");
	}
	
	@Test
	public void testGetPrinterStatus() {
		RECORD params = RECORD.empty();
		GetPrinterStatusResults results = GetPrinterStatusResults.make();
		
		Printing3Impl.getPrinterStatus(params, results);
		
		StringBuilder sb = new StringBuilder();
		String resultsDesc = results.append(sb, "", "getPrinterStatus() => results").toString();
		System.out.println("\n" + resultsDesc + "\n");
	}
	
	@Test
	public void testGetPrintRequestStatus() {
		GetPrintRequestStatusParams params = GetPrintRequestStatusParams.make();;
		GetPrintRequestStatusResults results = GetPrintRequestStatusResults.make();
		
		params.printRequestID.get(0).set(0xFFFF);
		params.printRequestID.get(1).set(0xFFFF);
		params.printRequestID.get(2).set(0xFFFF);
		params.printRequestID.get(3).set(0xFFFF);
		params.printRequestID.get(4).set(0xFFFF);
		
		Printing3Impl.getPrintRequestStatus(params, results);
		
		StringBuilder sb = new StringBuilder();
		String resultsDesc = results.append(sb, "", "getPrintRequestStatus() => results").toString();
		System.out.println("\n" + resultsDesc + "\n");
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testPrint() {
		PrintParams params = PrintParams.make();
		PrintResults results = PrintResults.make();
		
		ValueRecord<STRING> printObjectName = (ValueRecord<STRING>)params.printAttributes.add().setChoice(PrintAttributeChoice.printObjectName);
		printObjectName.value.set("UNITTEST");
		
		ValueRecord<STRING> senderName = (ValueRecord<STRING>)params.printAttributes.add().setChoice(PrintAttributeChoice.senderName);
		senderName.value.set("UNITTEST");
		
		params.master.descriptor.setChoice(DescriptorKind.nullKind);
		
		Printing3Impl.print(params, results);
		
		StringBuilder sb = new StringBuilder();
		String resultsDesc = results.append(sb, "", "print() => results").toString();
		System.out.println("\n" + resultsDesc + "\n");
	}
	
}
