/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2018 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.util;

import java.util.Set;
import java.util.TreeSet;
import java.text.ParseException;


public class NumberSequenceHandler
{
	/** Attempts to parse input 'inStr' and returns 'true' only if that can be done. */
	public static
	boolean isValidInput(final String inStr)
	{
		boolean inputIsOK = true;

		//dry-run parse to see if it is parse-able
		try {
			parseSequenceOfNumbers(inStr,null);
		}
		catch (ParseException e)
		{
			inputIsOK = false;
		}

		return inputIsOK;
	}


	/** Attempts to parse input 'inStr' and returns 'null' only if that can be done,
	    otherwise a string with complaint message is returned. */
	public static
	String whyIsInputInvalid(final String inStr)
	{
		String complaintMsg = null;

		//dry-run parse to see if it is parse-able
		try {
			parseSequenceOfNumbers(inStr,null);
		}
		catch (ParseException e)
		{
			complaintMsg = e.getMessage();
		}

		return complaintMsg;
	}


	/** Parses the input 'inStr' and returns an expanded set that corresponds
	    to the (succint) string input. */
	public static
	TreeSet<Integer> toSet(final String inStr)
	{
		try {
			TreeSet<Integer> outList = new TreeSet<>();
			parseSequenceOfNumbers(inStr,outList);
			return outList;
		}
		catch (ParseException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}


	/** Parses the input 'inStr' and adds to the 'outList' an expanded set
	    that corresponds to the (succint) string input. Note the output
	    is not explicitly cleared in this method. */
	public static
	void toSet(final String inStr, final Set<Integer> outList)
	{
		try {
			parseSequenceOfNumbers(inStr,outList);
		}
		catch (ParseException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}
	// -------------------------------------------------------------------------

	/** Reads inStr and parses it into outList (if outList is not null).
	    This is the string-to-set conversion workhorse. */
	public static
	void parseSequenceOfNumbers(final String inStr, final Set<Integer> outList)
	throws ParseException
	{
		//marker where we pretend that the input string begins
		//aka "how much has been parsed so far"
		int strFakeBegin = 0;

		try {
			while (strFakeBegin < inStr.length())
			{
				int ic = inStr.indexOf(',',strFakeBegin);
				int ih = inStr.indexOf('-',strFakeBegin);
				//NB: returns -1 if not found

				if (ic == -1)
					//if no comma is found, then we are processing the last term
					ic = inStr.length();

				if (ih == -1)
					//if no hyphen is found, then go to the "comma" branch
					ih = ic+1;

				if (ic < ih)
				{
					//"comma branch"
					//we're parsing out N,
					int N = Integer.parseInt( inStr.substring(strFakeBegin, ic).trim() );
					if (outList != null)
						outList.add(N);
				}
				else
				{
					//"hyphen branch"
					//we're parsing out N-M,
					int N = Integer.parseInt( inStr.substring(strFakeBegin, ih).trim() );
					int M = Integer.parseInt( inStr.substring(ih+1, ic).trim() );
					if (outList != null)
						for (int n=N; n <= M; ++n)
							outList.add(n);
				}

				strFakeBegin = ic+1;
			}
		}
		catch (NumberFormatException e)
		{
			throw new ParseException("Parsing problem after reading "
			                         +inStr.substring(0,strFakeBegin)+": "+e.getMessage(),0);
		}
	}
}
