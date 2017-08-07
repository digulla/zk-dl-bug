package org.zkoss.zk.ui.metainfo;

import java.net.URL;

public class MissingDependsException extends RuntimeException {

	private final URL url;
	private final String componentName;
	private final String extendedComponentName;

	public MissingDependsException(URL url, String componentName, String extendedComponentName) {
		super("Using <extends> in a <component> without <depends> will eventually cause problems depending on the classpath order. component=" +
				componentName + ", trying to extend=" + extendedComponentName + ", url=" + url);
		
		this.url = url;
		this.componentName = componentName;
		this.extendedComponentName = extendedComponentName;
	}
	
	public URL getUrl() {
		return url;
	}
	
	public String getComponentName() {
		return componentName;
	}
	
	public String getExtendedComponentName() {
		return extendedComponentName;
	}
}
