package de.mpicbg.ulman.Mastodon;

import net.imagej.ImageJ;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.module.*;
import org.scijava.ui.swing.widget.SwingInputHarvester;

public class testGUI
{

	public static void main(String... args)
	{
		//grab full "context"
		final ImageJ ij = new net.imagej.ImageJ();
		final Context context = ij.getContext();


		//particular instance of the plugin
		TestingPlugin plugin = new TestingPlugin();
		plugin.setContext(context);

		//wrap Module around the (existing) command
		final CommandModule cm = new CommandModule( context.getService(CommandService.class).getCommand(plugin.getClass()), plugin );

		//update default values to the current situation
		plugin.fileNoDigits = 55;
		plugin.voxelType = new UnsignedShortType();

		//mark which fields of the plugin need not be harvested for
		cm.resolveInput("context");
		cm.resolveInput("voxelType");
		cm.resolveInput("filePostfix");

		try {
			//GUI harvest (or just confirm) values for (some) parameters
			final SwingInputHarvester sih = new SwingInputHarvester();
			sih.setContext(context);
			sih.harvest(cm);
		} catch (ModuleException e) {
			//NB: includes ModuleCanceledException which signals 'Cancel' button
			//flag that the plugin should not be started at all
			plugin = null;
		}

		if (plugin != null) new Thread(plugin).start();

		System.out.println("Done.");
	}
}
