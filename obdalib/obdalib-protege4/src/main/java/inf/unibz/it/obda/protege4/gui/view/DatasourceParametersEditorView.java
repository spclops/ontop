package inf.unibz.it.obda.protege4.gui.view;

import inf.unibz.it.obda.api.controller.APIController;
import inf.unibz.it.obda.gui.swing.datasource.panels.DatasourceParameterEditorPanel;
import inf.unibz.it.obda.protege4.core.OBDAPluginController;

import java.awt.BorderLayout;

import org.apache.log4j.Logger;
import org.protege.editor.owl.ui.view.AbstractOWLViewComponent;


public class DatasourceParametersEditorView extends AbstractOWLViewComponent {
	/**
	 * 
	 */
	private static final long	serialVersionUID	= 1L;
	private static final Logger log = Logger.getLogger(DatasourceParametersEditorView.class);
	@Override
	protected void disposeOWLView() {

		
	}

	@Override
	protected void initialiseOWLView() throws Exception {
		
//		List<Bundle> plugins = ProtegeApplication.getBundleManager().getPlugins();
//    	Bundle obdaBundle = null;
//    	for(Bundle plugin: plugins) {
//    		String name = plugin.getSymbolicName();
//    		if (name.equals("inf.unibz.it.obda.protege4")) {
//    			obdaBundle =  plugin;
//    		}
//    	}
//    	if (obdaBundle == null)
//    		throw new Exception("Error initializing SQLQuery interface view, couldnt find OBDA Bundle");
//    	
//    	ServiceReference apicServiceReference = obdaBundle.getBundleContext().getServiceReference(APIController.class.getName().getName());
//    	OBDAPluginController apic = (OBDAPluginController)obdaBundle.getBundleContext().getService(apicServiceReference);
		OBDAPluginController apic = getOWLEditorKit().get(APIController.class.getName());
		
        setLayout(new BorderLayout());
//        try {
        add(new DatasourceParameterEditorPanel(apic.getOBDAManager()), BorderLayout.CENTER);
//        } catch (Exception e) {
//        	e.printStackTrace(System.out);
//        	throw e;
//        }
        log.info("Example View Component initialized");
		
	}

}
