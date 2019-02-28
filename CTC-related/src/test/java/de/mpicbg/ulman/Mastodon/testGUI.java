package de.mpicbg.ulman.Mastodon;

import net.imagej.ImageJ;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.command.CommandInfo;
import org.scijava.command.CommandService;
import org.scijava.convert.ConvertService;
import org.scijava.log.LogService;
import org.scijava.module.*;
import org.scijava.object.ObjectService;
import org.scijava.ui.UIService;
import org.scijava.ui.swing.widget.SwingInputHarvester;
import org.scijava.ui.swing.widget.SwingInputPanel;
import org.scijava.widget.InputPanel;
import org.scijava.widget.WidgetService;

import javax.swing.*;

public class testGUI
{

	public static void main(String... args)
	{
		//grab full "context"
		final ImageJ ij = new net.imagej.ImageJ();
		final Context context = ij.getContext();

		/*
		//grab minimal "context"
		final Context context = new Context(
				LogService.class,
				ModuleService.class,
				CommandService.class,
				ConvertService.class,
				ObjectService.class,
				WidgetService.class,
				UIService.class
		);
		*/

		//grab the general plugin description, not bound to any particular instance of the plugin
		//final ModuleInfo pluginInfo = context.getService(CommandService.class).getCommand(TestingPlugin.class);
		final ModuleInfo pluginInfo = ij.command().getCommand(TestingPlugin.class);

		//particular instance of the plugin, once as general-purpose Module, once as the particular instance
		Module m = null;
		TestingPlugin plugin = null;

		try {
			//create an instance of the plugin
			m = pluginInfo.createModule();

			//here's the reference to the created instance
			plugin = (TestingPlugin)m.getDelegateObject();
			plugin.fileNoDigits = 55;
			plugin.voxelType = new UnsignedShortType();

			//inject the mandatory context
			m.setInput("context", context);
			m.resolveInput("context");
			m.resolveInput("voxelType");
			m.resolveInput("filePostfix");

			//inject some default params...
			//m.setInput("outputPath","blabla");
			//m.resolveInput("outputPath");

			//GUI input harvester that we gonna use, and...
			final SwingInputHarvester sih = new SwingInputHarvester();
			sih.setContext(context);
			//...harvest the param values
			sih.harvest(m);

			/*
			if harvesting GUI is to be embedded into some other GUI element,
			one can follow the example below:
			(and use InputPanel and sih.createInputPanel(); instead of sih.harvest())

			example here:
			https://github.com/juglab/Blob_Detector/blob/14f246d2dad24ae258cd1810d7f43447d00bba87/src/main/java/command/OverlayCommand.java#L589-L657
			*/

			System.out.println("Done harvesting.");

		} catch (ModuleCanceledException e) {
			System.out.println("dialog canceled");
			plugin = null;
		} catch (ModuleException e) {
			e.printStackTrace();
			plugin = null;
		}

		if (plugin != null)
		{
            //just report the current params values
            System.out.println("---------");
            for (String i : m.getInputs().keySet())
            {
                System.out.println("Param: "+i+" = "+m.getInput(i));

            }
            System.out.println("---------");
            for (ModuleItem i : pluginInfo.inputs())
            {
                System.out.println("Param: "+i.getName()+" = "+i.getValue(m)+" (default: "+i.getDefaultValue()+")");
            }

			System.out.println("---------");
            //new Thread(m).start();
            new Thread(plugin).start();
		}

		System.out.println("Done.");
		//ExporterPlugin<UnsignedShortType> ep = new ExporterPlugin<>(new UnsignedShortType());
		//ep.setContext(context);
	}
}
