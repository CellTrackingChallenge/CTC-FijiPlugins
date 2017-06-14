/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.TextWidget;


@Plugin(type = Command.class, menuPath = "Tutorials>Text Area Demo")
public class TextAreaDemo implements Command
{
	@Parameter(label = "String + Text Area", style = TextWidget.AREA_STYLE)
	private String string;

	@Override
	public void run()
	{
		System.out.println(string);
	}

	public static void main(final String... args) throws Exception
	{
		final ImageJ ij = new ImageJ();
		ij.ui().showUI();
		ij.command().run(TextAreaDemo.class, true);
	}
}
