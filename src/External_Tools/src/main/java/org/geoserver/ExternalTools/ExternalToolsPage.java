package org.geoserver.ExternalTools;

import java.util.*;

import org.apache.wicket.markup.html.basic.Label;
import org.geoserver.web.GeoServerSecuredPage;
import org.geoserver.web.ComponentAuthorizer;

public class ExternalToolsPage extends GeoServerSecuredPage {
	
	public ExternalToolsPage() 
	{	
       add( new Label( "testlabel", "點擊以下連結執行工具") );
	}
	
	protected ComponentAuthorizer getPageAuthorizer() {
        return ComponentAuthorizer.WORKSPACE_ADMIN;
    }
   
}