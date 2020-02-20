/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2018 Vladimír Ulman
 */
package de.mpicbg.ulman.ctc.silverGT;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/** A no-op plugin existing here only to harvest parameter values for
    the SIMPLE algorithm. The parameters here should be mirrored in
    the SIMPLE.setParams(). */
@Plugin(type = Command.class)
public class SIMPLE_params implements Command
{
	@Parameter
	int maxIters = 4;

	@Parameter
	int noOfNoUpdateIters = 2;

	@Parameter
	double initialQualityThreshold = 0.7;

	@Parameter
	double stepDownInQualityThreshold = 0.1;

	@Parameter
	double minimalQualityThreshold = 0.3;

	@Override
	public void run()
	{ /* intenionally empty */ }
}
