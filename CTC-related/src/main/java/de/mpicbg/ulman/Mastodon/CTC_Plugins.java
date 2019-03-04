package de.mpicbg.ulman.Mastodon;

import static org.mastodon.app.ui.ViewMenuBuilder.item;
import static org.mastodon.app.ui.ViewMenuBuilder.menu;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mastodon.app.ui.ViewMenuBuilder;
import org.mastodon.plugin.MastodonPlugin;
import org.mastodon.plugin.MastodonPluginAppModel;
import org.mastodon.revised.mamut.MamutAppModel;
import org.scijava.AbstractContextual;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleException;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.AbstractNamedAction;
import org.scijava.ui.behaviour.util.RunnableAction;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.ui.swing.widget.SwingInputHarvester;

@Plugin( type = CTC_Plugins.class )
public class CTC_Plugins extends AbstractContextual implements MastodonPlugin
{
	//"IDs" of all plug-ins wrapped in this class
	private static final String CTC_IMPORT = "CTC-import-all";
	private static final String CTC_EXPORT = "CTC-export-all";
	//------------------------------------------------------------------------

	@Override
	public List< ViewMenuBuilder.MenuItem > getMenuItems()
	{
		//this places the plug-in's menu items into the menu,
		//the titles of the items are defined right below
		return Arrays.asList(
				menu( "Plugins",
						menu( "Cell Tracking Challenge",
								item( CTC_IMPORT ), item ( CTC_EXPORT) ) ) );
	}

	/** titles of this plug-in's menu items */
	private static Map< String, String > menuTexts = new HashMap<>();
	static
	{
		menuTexts.put( CTC_IMPORT, "Import from CTC format" );
		menuTexts.put( CTC_EXPORT, "Export to CTC format" );
	}

	@Override
	public Map< String, String > getMenuTexts()
	{
		return menuTexts;
	}
	//------------------------------------------------------------------------

	private final AbstractNamedAction actionImport;
	private final AbstractNamedAction actionExport;

	/** default c'tor: creates Actions available from this plug-in */
	public CTC_Plugins()
	{
		actionImport = new RunnableAction( CTC_IMPORT, this::importer );
		actionExport = new RunnableAction( CTC_EXPORT, this::exporter );
		updateEnabledActions();
	}

	/** register the actions to the application (with no shortcut keys) */
	@Override
	public void installGlobalActions( final Actions actions )
	{
		final String[] noShortCut = new String[] {};
		actions.namedAction( actionImport, noShortCut );
		actions.namedAction( actionExport, noShortCut );
	}

	/** reference to the currently available project in Mastodon */
	private MastodonPluginAppModel pluginAppModel;

	/** learn about the current project's params */
	@Override
	public void setAppModel( final MastodonPluginAppModel model )
	{
		//the application reports back to us if some project is available
		this.pluginAppModel = model;
		updateEnabledActions();
	}

	/** enables/disables menu items based on the availability of some project */
	private void updateEnabledActions()
	{
		final MamutAppModel appModel = ( pluginAppModel == null ) ? null : pluginAppModel.getAppModel();
		actionImport.setEnabled( appModel != null );
		actionExport.setEnabled( appModel != null );
	}
	//------------------------------------------------------------------------

	/** opens the import dialog to find the tracks.txt file,
	    and runs the import on the currently viewed images
	    provided params were harvested successfully */
	private void importer()
	{
		//particular instance of the plugin
		ImporterPlugin ip = new ImporterPlugin();
		ip.setContext(this.getContext());

		//wrap Module around the (existing) command
		final CommandModule cm = new CommandModule( this.getContext().getService(CommandService.class).getCommand(ip.getClass()), ip );

		//update default values to the current situation
		ip.imgSource = pluginAppModel.getAppModel().getSharedBdvData().getSources().get(0).getSpimSource();
		ip.model     = pluginAppModel.getAppModel().getModel();

		ip.timeFrom  = pluginAppModel.getAppModel().getMinTimepoint();
		ip.timeTill  = pluginAppModel.getAppModel().getMaxTimepoint();

		//mark which fields of the plugin shall not be displayed
		cm.resolveInput("context");
		cm.resolveInput("imgSource");
		cm.resolveInput("model");

		try {
			//GUI harvest (or just confirm) values for (some) parameters
			final SwingInputHarvester sih = new SwingInputHarvester();
			sih.setContext(this.getContext());
			sih.harvest(cm);
		} catch (ModuleException e) {
			//NB: includes ModuleCanceledException which signals 'Cancel' button
			//flag that the plugin should not be started at all
			ip = null;
		}

		if (ip != null)
		{
			if (ip.inputPath == null)
				//provide fake input to give more meaningful error later...
				ip.inputPath = new File("NO INPUT FILE GIVEN");

			//starts the importer in a separate thread
			new Thread(ip,"Mastodon CTC importer").start();
		}
	}

	/** opens the export dialog, and runs the export
	    provided params were harvested successfully */
	private void exporter()
	{
		//particular instance of the plugin
		ExporterPlugin<UnsignedShortType> ep = new ExporterPlugin<>(new UnsignedShortType());
		ep.setContext(this.getContext());

		//wrap Module around the (existing) command
		final CommandModule cm = new CommandModule( this.getContext().getService(CommandService.class).getCommand(ep.getClass()), ep );

		//update default values to the current situation
		ep.imgSource  = pluginAppModel.getAppModel().getSharedBdvData().getSources().get(0).getSpimSource();
		ep.model      = pluginAppModel.getAppModel().getModel();

		ep.doOneZslicePerMarker = true;
		ep.timeFrom   = pluginAppModel.getAppModel().getMinTimepoint();
		ep.timeTill   = pluginAppModel.getAppModel().getMaxTimepoint();

		//mark which fields of the plugin shall not be displayed
		cm.resolveInput("context");
		cm.resolveInput("filePrefix");
		cm.resolveInput("filePostfix");
		cm.resolveInput("fileNoDigits");
		cm.resolveInput("imgSource");
		cm.resolveInput("model");

		try {
			//GUI harvest (or just confirm) values for (some) parameters
			final SwingInputHarvester sih = new SwingInputHarvester();
			sih.setContext(this.getContext());
			sih.harvest(cm);
		} catch (ModuleException e) {
			//NB: includes ModuleCanceledException which signals 'Cancel' button
			//flag that the plugin should not be started at all
			ep = null;
		}

		if (ep != null)
		{
			//check there is a TRA sub-folder; and if not, create it
			final File traFolder = new File(ep.outputPath.getPath()+File.separator+"TRA");
			if (traFolder.exists())
			{
				//"move" into the existing TRA folder
				ep.outputPath = traFolder;
			}
			else
			{
				if (traFolder.mkdirs())
					ep.outputPath = traFolder;
				else
					throw new IllegalArgumentException("Cannot create missing subfolder TRA in the folder: "+ep.outputPath.getAbsolutePath());
			}

			//starts the exporter in a separate thread
			new Thread(ep,"Mastodon CTC exporter").start();
		}
	}
}
